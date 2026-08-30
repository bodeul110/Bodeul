package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.Grant;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentRepository.AppointmentContext;
import com.bodeul.core.consent.GuardianSharingConsentRepository.ConsentSettings;
import com.bodeul.core.consent.GuardianSharingConsentRepository.EventAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultGuardianSharingConsentService implements GuardianSharingConsentService {

    private static final Set<String> CLOSED_APPOINTMENT_STATUSES = Set.of("COMPLETED", "CANCELED");
    private static final long CONSENT_DAYS_AFTER_CARE_BOUNDARY = 7L;

    private final GuardianSharingConsentRepository consentRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    @Autowired
    DefaultGuardianSharingConsentService(
            GuardianSharingConsentRepository consentRepository,
            AppUserRepository appUserRepository) {
        this(consentRepository, appUserRepository, Clock.systemUTC());
    }

    DefaultGuardianSharingConsentService(
            GuardianSharingConsentRepository consentRepository,
            AppUserRepository appUserRepository,
            Clock clock) {
        this.consentRepository = consentRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentView get(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId) {
        AppointmentContext appointment = findAppointment(appointmentRequestId);
        requireRelatedReader(appUser, appointment);
        return consentRepository.findByAppointmentId(appointmentRequestId)
                .filter(grant -> grant.patientUserId().equals(appointment.patientUserId()))
                .filter(grant -> grant.guardianUserId().equals(appointment.guardianUserId()))
                .map(this::toView)
                .orElseThrow(GuardianSharingConsentException::consentNotFound);
    }

    @Override
    @Transactional
    public ConsentView grant(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId,
            Set<InformationScope> scopes,
            boolean adultPatientConfirmed) {
        AppointmentContext appointment = findAppointment(appointmentRequestId);
        requirePatient(appUser, appointment);
        if (!adultPatientConfirmed) {
            throw GuardianSharingConsentException.invalidRequest(
                    "성인 환자 본인 확인이 필요합니다.");
        }
        if (CLOSED_APPOINTMENT_STATUSES.contains(appointment.status())
                || appointment.careEndedAt() != null) {
            throw GuardianSharingConsentException.stateConflict();
        }
        if (appointment.guardianUserId() == null) {
            throw GuardianSharingConsentException.invalidRequest("연결된 보호자 계정이 필요합니다.");
        }

        Set<InformationScope> normalizedScopes = normalizeScopes(scopes);
        if (normalizedScopes.contains(InformationScope.ATTACHMENT)
                && !normalizedScopes.contains(InformationScope.CHAT)) {
            throw GuardianSharingConsentException.invalidRequest(
                    "첨부 파일을 공유하려면 채팅 범위도 함께 선택해 주세요.");
        }
        ConsentSettings settings = consentRepository.getSettings();
        if (!settings.locationSharingEnabled()
                && normalizedScopes.contains(InformationScope.LOCATION)) {
            throw GuardianSharingConsentException.invalidRequest(
                    "현재 위치 공유 기능이 비활성화되어 LOCATION 범위를 선택할 수 없습니다.");
        }
        AppUserRepository.AppUser guardian = appUserRepository.findById(appointment.guardianUserId())
                .filter(user -> user.role() == AppUserRole.GUARDIAN)
                .orElseThrow(() -> GuardianSharingConsentException.invalidRequest(
                        "연결된 보호자 계정 상태를 확인해 주세요."));

        Instant now = clock.instant();
        Instant careBoundary = appointment.appointmentAt().isAfter(now)
                ? appointment.appointmentAt()
                : now;
        Instant expiresAt = careBoundary.plus(
                CONSENT_DAYS_AFTER_CARE_BOUNDARY,
                ChronoUnit.DAYS);
        Grant requested = AdultPatientGuardianSharingPolicy.grantByPatient(
                appUser.id(),
                appUser.role(),
                appointment.id(),
                appointment.patientUserId(),
                guardian.id(),
                guardian.role(),
                normalizedScopes,
                now,
                expiresAt,
                settings.policyVersion());
        Grant saved = consentRepository.grant(requested);
        consentRepository.appendEvent(saved, EventAction.GRANTED, appUser.id(), now);
        return toView(saved);
    }

    @Override
    @Transactional
    public ConsentView revoke(
            AppUserRepository.AppUser appUser,
            UUID appointmentRequestId) {
        AppointmentContext appointment = findAppointment(appointmentRequestId);
        requirePatient(appUser, appointment);
        Grant existing = consentRepository.findByAppointmentId(appointmentRequestId)
                .orElseThrow(GuardianSharingConsentException::consentNotFound);
        Instant now = clock.instant();
        Grant domainRevoked = AdultPatientGuardianSharingPolicy.revokeByPatient(
                existing,
                appUser.id(),
                appUser.role(),
                now);
        if (domainRevoked == existing) {
            return toView(existing);
        }
        Grant revoked = consentRepository.revoke(
                        appointmentRequestId,
                        appUser.id(),
                        now,
                        existing.version())
                .orElseThrow(GuardianSharingConsentException::stateConflict);
        consentRepository.appendEvent(revoked, EventAction.REVOKED, appUser.id(), now);
        return toView(revoked);
    }

    private AppointmentContext findAppointment(UUID appointmentRequestId) {
        if (appointmentRequestId == null) {
            throw GuardianSharingConsentException.invalidRequest("예약 ID가 필요합니다.");
        }
        return consentRepository.findAppointment(appointmentRequestId)
                .orElseThrow(GuardianSharingConsentException::appointmentNotFound);
    }

    private void requireRelatedReader(
            AppUserRepository.AppUser appUser,
            AppointmentContext appointment) {
        if (appUser == null) {
            throw GuardianSharingConsentException.permissionDenied();
        }
        if (appUser.role() == AppUserRole.PATIENT
                && appUser.id().equals(appointment.patientUserId())) {
            return;
        }
        if (appUser.role() == AppUserRole.GUARDIAN
                && appUser.id().equals(appointment.guardianUserId())) {
            return;
        }
        throw GuardianSharingConsentException.permissionDenied();
    }

    private void requirePatient(
            AppUserRepository.AppUser appUser,
            AppointmentContext appointment) {
        if (appUser == null || appUser.role() != AppUserRole.PATIENT) {
            throw GuardianSharingConsentException.patientRequired();
        }
        if (!appUser.id().equals(appointment.patientUserId())) {
            throw GuardianSharingConsentException.permissionDenied();
        }
    }

    private Set<InformationScope> normalizeScopes(Set<InformationScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw GuardianSharingConsentException.invalidRequest(
                    "하나 이상의 정보공유 범위를 선택해 주세요.");
        }
        if (scopes.stream().anyMatch(Objects::isNull)) {
            throw GuardianSharingConsentException.invalidRequest(
                    "정보공유 범위를 확인해 주세요.");
        }
        return Set.copyOf(EnumSet.copyOf(scopes));
    }

    private ConsentView toView(Grant grant) {
        ConsentSettings settings = consentRepository.getSettings();
        Instant now = clock.instant();
        boolean expiryFinalized = consentRepository.isExpiryFinalized(
                grant.appointmentRequestId());
        boolean active = grant.revokedAt() == null
                && !now.isBefore(grant.grantedAt())
                && (!expiryFinalized || now.isBefore(grant.expiresAt()))
                && grant.policyVersion().equals(settings.policyVersion());
        return new ConsentView(
                grant.id(),
                grant.appointmentRequestId(),
                grant.patientUserId(),
                grant.guardianUserId(),
                grant.scopes(),
                grant.policyVersion(),
                grant.grantedAt().toString(),
                grant.grantedAt().toString(),
                grant.expiresAt().toString(),
                grant.revokedAt() == null ? "" : grant.revokedAt().toString(),
                active,
                expiryFinalized,
                settings.locationSharingEnabled(),
                grant.version());
    }
}
