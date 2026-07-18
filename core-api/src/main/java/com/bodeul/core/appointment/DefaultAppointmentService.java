package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.appointment.AppUserProfileRepository.AppUserProfile;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpRecord;
import com.bodeul.core.appointment.AppointmentRepository.ParticipantSnapshot;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultAppointmentService implements AppointmentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter APPOINTMENT_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm", Locale.KOREA)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int BASE_PRICE = 69_000;
    private static final Set<String> REVIEW_RATINGS = Set.of(
            "excellent", "good", "ok", "disappointing", "need_help");
    private static final Set<String> SETTLEMENT_STATUSES = Set.of(
            "CONFIRMED", "NEEDS_HELP", "OVERTIME_REVIEW", "REFUND_REVIEW");
    private static final Set<String> SUPPORT_ESCALATION_STATUSES = Set.of(
            "GUIDE_VIEWED", "MANAGER_CALLED", "DIALED_119");

    private final AppointmentRepository appointmentRepository;
    private final AppUserProfileRepository profileRepository;
    private final Clock clock;

    @Autowired
    DefaultAppointmentService(
            AppointmentRepository appointmentRepository,
            AppUserProfileRepository profileRepository) {
        this(appointmentRepository, profileRepository, Clock.systemUTC());
    }

    DefaultAppointmentService(
            AppointmentRepository appointmentRepository,
            AppUserProfileRepository profileRepository,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> getMyAppointments(AppUserRepository.AppUser appUser) {
        requireReadableRole(appUser);
        return appointmentRepository.findAllForParticipant(appUser.id(), appUser.role())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentView getAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        requireReadableRole(appUser);
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireReader(appUser, appointment);
        return toView(appointment);
    }

    @Override
    @Transactional
    public AppointmentView createAppointment(
            AppUserRepository.AppUser appUser,
            CreateAppointmentCommand command) {
        requireSupportedRole(appUser);
        if (command == null || command.clientRequestId() == null) {
            throw AppointmentException.invalidRequest("중복 생성 방지용 clientRequestId가 필요합니다.");
        }

        NormalizedDraft draft = normalizeDraft(command.draft());
        var existing = appointmentRepository.findByClientRequestId(
                appUser.id(),
                command.clientRequestId());
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        ParticipantPair participants = resolveParticipants(appUser, draft);
        Price price = calculatePrice(draft);
        AppointmentMutation mutation = toMutation(
                command.clientRequestId(),
                appUser.id(),
                appUser.role(),
                participants,
                draft,
                price);

        return appointmentRepository.insert(mutation)
                .or(() -> appointmentRepository.findByClientRequestId(
                        appUser.id(), command.clientRequestId()))
                .map(this::toView)
                .orElseThrow(AppointmentException::versionConflict);
    }

    @Override
    @Transactional
    public AppointmentView updateAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentCommand command) {
        requireSupportedRole(appUser);
        if (command == null || command.version() < 0) {
            throw AppointmentException.invalidRequest("예약 버전이 필요합니다.");
        }

        AppointmentRecord existing = findAppointment(appointmentId);
        requireParticipant(appUser, existing);
        if (!"REQUESTED".equals(existing.status())) {
            throw AppointmentException.stateConflict();
        }
        if (existing.version() != command.version()) {
            throw AppointmentException.versionConflict();
        }

        NormalizedDraft draft = normalizeDraft(command.draft());
        ParticipantPair participants = resolveParticipants(appUser, draft);
        requireRequesterLink(existing, participants);
        Price price = calculatePrice(draft);
        ParticipantSnapshot requester = existing.requesterRole() == AppUserRole.PATIENT
                ? participants.patient()
                : participants.guardian();
        AppointmentMutation mutation = toMutation(
                null,
                existing.requesterUserId(),
                existing.requesterRole(),
                participants,
                draft,
                price,
                requester);

        return appointmentRepository.update(appointmentId, command.version(), mutation)
                .map(this::toView)
                .orElseThrow(AppointmentException::versionConflict);
    }

    @Override
    @Transactional
    public AppointmentView cancelAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            long version) {
        requireSupportedRole(appUser);
        if (version < 0) {
            throw AppointmentException.invalidRequest("예약 버전이 필요합니다.");
        }

        AppointmentRecord existing = findAppointment(appointmentId);
        requireParticipant(appUser, existing);
        if (!"REQUESTED".equals(existing.status()) && !"MATCHED".equals(existing.status())) {
            throw AppointmentException.stateConflict();
        }
        if (existing.version() != version) {
            throw AppointmentException.versionConflict();
        }

        AppointmentRecord canceled = appointmentRepository.cancel(appointmentId, version)
                .orElseThrow(AppointmentException::versionConflict);
        if ("MATCHED".equals(existing.status())
                && !appointmentRepository.cancelActiveSession(appointmentId)) {
            throw AppointmentException.stateConflict();
        }
        return toView(canceled);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentFollowUpView getAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        requireReadableRole(appUser);
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireReader(appUser, appointment);
        return appointmentRepository.findFollowUpByAppointmentId(appointmentId)
                .map(this::toFollowUpView)
                .orElseGet(() -> emptyFollowUpView(appointmentId));
    }

    @Override
    @Transactional
    public AppointmentFollowUpView updateAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentFollowUpCommand command) {
        requireSupportedRole(appUser);
        if (command == null || command.version() < 0) {
            throw AppointmentException.invalidRequest("후속 기록 버전이 필요합니다.");
        }
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireParticipant(appUser, appointment);
        if (!"COMPLETED".equals(appointment.status())) {
            throw AppointmentException.stateConflict();
        }

        String reviewRatingCode = normalizeOptionalCode(
                command.reviewRatingCode(), REVIEW_RATINGS, false, "후기 만족도");
        String settlementStatus = normalizeOptionalCode(
                command.settlementStatus(), SETTLEMENT_STATUSES, true, "정산 확인 상태");
        String supportEscalationStatus = normalizeOptionalCode(
                command.supportEscalationStatus(), SUPPORT_ESCALATION_STATUSES, true, "긴급 지원 상태");
        if (reviewRatingCode == null
                && settlementStatus == null
                && supportEscalationStatus == null) {
            throw AppointmentException.invalidRequest("저장할 후속 기록이 필요합니다.");
        }
        String settlementNote = settlementStatus == null
                ? null
                : limitText(normalizeText(command.settlementNote()), "정산 확인 메모", 2_000);

        var existing = appointmentRepository.findFollowUpByAppointmentId(appointmentId);
        long currentVersion = existing.map(AppointmentFollowUpRecord::version).orElse(0L);
        if (currentVersion != command.version()) {
            throw AppointmentException.versionConflict();
        }
        AppointmentFollowUpMutation mutation = new AppointmentFollowUpMutation(
                appointmentId,
                appUser.id(),
                command.version(),
                reviewRatingCode,
                settlementStatus,
                settlementNote,
                supportEscalationStatus);
        return (existing.isEmpty()
                        ? appointmentRepository.insertFollowUp(mutation)
                        : appointmentRepository.updateFollowUp(mutation))
                .map(this::toFollowUpView)
                .orElseThrow(AppointmentException::versionConflict);
    }

    private AppointmentRecord findAppointment(UUID appointmentId) {
        if (appointmentId == null) {
            throw AppointmentException.invalidRequest("예약 ID가 필요합니다.");
        }
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(AppointmentException::notFound);
    }

    private void requireSupportedRole(AppUserRepository.AppUser appUser) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT && appUser.role() != AppUserRole.GUARDIAN)) {
            throw AppointmentException.roleNotSupported();
        }
    }

    private void requireReadableRole(AppUserRepository.AppUser appUser) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT
                && appUser.role() != AppUserRole.GUARDIAN
                && appUser.role() != AppUserRole.MANAGER)) {
            throw AppointmentException.readRoleNotSupported();
        }
    }

    private void requireReader(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        if (appUser.role() == AppUserRole.MANAGER) {
            if (!appUser.id().equals(appointment.managerUserId())) {
                throw AppointmentException.permissionDenied();
            }
            return;
        }
        requireParticipant(appUser, appointment);
    }

    private void requireParticipant(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        UUID participantId = appUser.role() == AppUserRole.PATIENT
                ? appointment.patientUserId()
                : appointment.guardianUserId();
        if (!appUser.id().equals(participantId)) {
            throw AppointmentException.permissionDenied();
        }
    }

    private void requireRequesterLink(
            AppointmentRecord existing,
            ParticipantPair participants) {
        UUID requesterParticipantId = existing.requesterRole() == AppUserRole.PATIENT
                ? participants.patientUserId()
                : participants.guardianUserId();
        if (!existing.requesterUserId().equals(requesterParticipantId)) {
            throw AppointmentException.requesterLinkConflict();
        }
    }

    private ParticipantPair resolveParticipants(
            AppUserRepository.AppUser appUser,
            NormalizedDraft draft) {
        AppUserProfile currentProfile = profileRepository.findById(appUser.id())
                .filter(profile -> profile.role() == appUser.role())
                .orElseThrow(AppointmentException::profileNotReady);
        ParticipantSnapshot currentSnapshot = requireCompleteProfile(currentProfile);

        AppUserRole linkedRole = appUser.role() == AppUserRole.PATIENT
                ? AppUserRole.GUARDIAN
                : AppUserRole.PATIENT;
        var linkedProfile = resolveLinkedProfile(
                linkedRole,
                draft.linkedParticipantEmail(),
                draft.linkedParticipantPhone());
        ParticipantSnapshot linkedSnapshot = linkedProfile
                .map(this::toSnapshot)
                .orElseGet(() -> new ParticipantSnapshot(
                        draft.linkedParticipantName(),
                        draft.linkedParticipantPhone(),
                        draft.linkedParticipantEmail()));

        if (appUser.role() == AppUserRole.PATIENT) {
            return new ParticipantPair(
                    appUser.id(),
                    linkedProfile.map(AppUserProfile::id).orElse(null),
                    currentSnapshot,
                    linkedSnapshot);
        }
        return new ParticipantPair(
                linkedProfile.map(AppUserProfile::id).orElse(null),
                appUser.id(),
                linkedSnapshot,
                currentSnapshot);
    }

    private java.util.Optional<AppUserProfile> resolveLinkedProfile(
            AppUserRole role,
            String email,
            String phone) {
        Map<UUID, AppUserProfile> candidates = new LinkedHashMap<>();
        if (!email.isEmpty()) {
            for (AppUserProfile profile : profileRepository.findByEmail(role, email)) {
                candidates.put(profile.id(), profile);
            }
        }
        if (!phone.isEmpty()) {
            for (AppUserProfile profile : profileRepository.findByPhone(role, phone)) {
                candidates.put(profile.id(), profile);
            }
        }
        if (candidates.size() > 1) {
            throw AppointmentException.participantAmbiguous();
        }
        return candidates.values().stream().findFirst();
    }

    private ParticipantSnapshot requireCompleteProfile(AppUserProfile profile) {
        ParticipantSnapshot snapshot = toSnapshot(profile);
        if (snapshot.name().isEmpty()
                || (snapshot.email().isEmpty() && snapshot.phone().isEmpty())) {
            throw AppointmentException.profileNotReady();
        }
        return snapshot;
    }

    private ParticipantSnapshot toSnapshot(AppUserProfile profile) {
        return new ParticipantSnapshot(
                normalizeName(profile.name()),
                normalizePhone(profile.phone()),
                normalizeEmail(profile.email()));
    }

    private NormalizedDraft normalizeDraft(AppointmentDraft draft) {
        if (draft == null) {
            throw AppointmentException.invalidRequest("예약 입력값이 필요합니다.");
        }

        String linkedParticipantName = requireText(
                normalizeName(draft.linkedParticipantName()),
                "연결 사용자 이름",
                100);
        String linkedParticipantPhone = normalizePhone(draft.linkedParticipantPhone());
        if (!isValidPhone(linkedParticipantPhone)) {
            throw AppointmentException.invalidRequest("연결 사용자 전화번호를 확인해 주세요.");
        }
        String linkedParticipantEmail = limitText(
                normalizeEmail(draft.linkedParticipantEmail()),
                "연결 사용자 이메일",
                320);
        if (!linkedParticipantEmail.isEmpty() && !isValidEmail(linkedParticipantEmail)) {
            throw AppointmentException.invalidRequest("연결 사용자 이메일을 확인해 주세요.");
        }
        String patientConditionSummary = requireText(
                draft.patientConditionSummary(),
                "환자 상태",
                2_000);
        String hospitalName = requireText(draft.hospitalName(), "병원 이름", 200);
        String departmentName = requireText(draft.departmentName(), "진료과", 100);
        String meetingPlace = requireText(draft.meetingPlace(), "만남 장소", 300);
        String appointmentAtText = requireText(draft.appointmentAt(), "예약 일시", 16);
        Instant appointmentAt = parseAppointmentAt(appointmentAtText);
        if (!appointmentAt.isAfter(clock.instant())) {
            throw AppointmentException.invalidRequest("예약 일시는 현재보다 이후여야 합니다.");
        }
        if (!Double.isFinite(draft.hospitalLatitude())
                || !Double.isFinite(draft.hospitalLongitude())
                || draft.hospitalLatitude() < -90 || draft.hospitalLatitude() > 90
                || draft.hospitalLongitude() < -180 || draft.hospitalLongitude() > 180) {
            throw AppointmentException.invalidRequest("병원 좌표 범위를 확인해 주세요.");
        }

        String mobilitySupport = requireCode(
                draft.mobilitySupportCode(),
                "이동 보조 방식",
                MobilitySupport.values());
        String tripType = requireCode(draft.tripTypeCode(), "이동 범위", TripType.values());
        String managerGender = requireCode(
                draft.managerGenderPreferenceCode(),
                "매니저 성별 선호",
                ManagerGender.values());
        String paymentMethod = requireCode(
                draft.paymentMethodCode(),
                "결제 방식",
                PaymentMethod.values());
        String coupon = requireCode(draft.couponCode(), "쿠폰", Coupon.values());

        return new NormalizedDraft(
                linkedParticipantName,
                linkedParticipantPhone,
                linkedParticipantEmail,
                patientConditionSummary,
                limitText(normalizeText(draft.medicationSummary()), "복약 정보", 2_000),
                hospitalName,
                departmentName,
                draft.hospitalLatitude(),
                draft.hospitalLongitude(),
                appointmentAt,
                meetingPlace,
                limitText(normalizeText(draft.specialNotes()), "특이사항", 2_000),
                mobilitySupport,
                tripType,
                managerGender,
                paymentMethod,
                coupon);
    }

    private Price calculatePrice(NormalizedDraft draft) {
        int optionSurcharge = MobilitySupport.valueOf(draft.mobilitySupportCode()).surcharge
                + TripType.valueOf(draft.tripTypeCode()).surcharge;
        int subtotal = BASE_PRICE + optionSurcharge;
        int discount = Math.min(subtotal, Coupon.valueOf(draft.couponCode()).discount);
        return new Price(BASE_PRICE, optionSurcharge, discount, subtotal - discount);
    }

    private AppointmentMutation toMutation(
            UUID clientRequestId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantPair participants,
            NormalizedDraft draft,
            Price price) {
        ParticipantSnapshot requester = requesterRole == AppUserRole.PATIENT
                ? participants.patient()
                : participants.guardian();
        return toMutation(
                clientRequestId,
                requesterUserId,
                requesterRole,
                participants,
                draft,
                price,
                requester);
    }

    private AppointmentMutation toMutation(
            UUID clientRequestId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantPair participants,
            NormalizedDraft draft,
            Price price,
            ParticipantSnapshot requester) {
        return new AppointmentMutation(
                clientRequestId,
                participants.patientUserId(),
                participants.guardianUserId(),
                requesterUserId,
                requesterRole,
                participants.patient(),
                participants.guardian(),
                requester,
                draft.hospitalName(),
                draft.departmentName(),
                draft.hospitalLatitude(),
                draft.hospitalLongitude(),
                draft.appointmentAt(),
                draft.appointmentAt().toEpochMilli(),
                draft.appointmentAt().atZone(SEOUL).toLocalDate().toString(),
                draft.meetingPlace(),
                draft.specialNotes(),
                draft.patientConditionSummary(),
                draft.medicationSummary(),
                draft.mobilitySupportCode(),
                draft.tripTypeCode(),
                draft.managerGenderPreferenceCode(),
                price.basePrice(),
                price.optionSurchargePrice(),
                price.couponDiscountPrice(),
                price.finalPrice(),
                draft.paymentMethodCode(),
                draft.couponCode(),
                "ON_SITE".equals(draft.paymentMethodCode()) ? "DEFERRED" : "PENDING");
    }

    private AppointmentView toView(AppointmentRecord appointment) {
        AppUserProfile manager = appointment.managerUserId() == null
                ? null
                : profileRepository.findById(appointment.managerUserId()).orElse(null);
        return new AppointmentView(
                appointment.id(),
                nullToEmpty(appointment.firestoreId()),
                appointment.patientUserId(),
                appointment.guardianUserId(),
                appointment.managerUserId(),
                manager == null ? "" : manager.name(),
                manager == null ? "" : manager.phone(),
                manager == null ? "" : manager.email(),
                appointment.patient().name(),
                appointment.patient().phone(),
                appointment.patient().email(),
                appointment.guardian().name(),
                appointment.guardian().phone(),
                appointment.guardian().email(),
                appointment.hospitalName(),
                appointment.departmentName(),
                appointment.hospitalLatitude(),
                appointment.hospitalLongitude(),
                APPOINTMENT_FORMATTER.format(appointment.appointmentAt().atZone(SEOUL)),
                appointment.meetingPlace(),
                appointment.specialNotes(),
                appointment.patientConditionSummary(),
                appointment.medicationSummary(),
                appointment.mobilitySupportCode(),
                appointment.tripTypeCode(),
                appointment.managerGenderPreferenceCode(),
                appointment.status(),
                appointment.basePrice(),
                appointment.optionSurchargePrice(),
                appointment.couponDiscountPrice(),
                appointment.finalPrice(),
                appointment.paymentMethodCode(),
                appointment.couponCode(),
                appointment.paymentStatusCode(),
                appointment.paymentApprovalCode(),
                appointment.paymentApprovedAt() == null
                        ? ""
                        : appointment.paymentApprovedAt().toString(),
                appointment.paymentProviderLabel(),
                appointment.version());
    }

    private AppointmentFollowUpView toFollowUpView(AppointmentFollowUpRecord followUp) {
        return new AppointmentFollowUpView(
                followUp.appointmentId(),
                nullToEmpty(followUp.reviewRatingCode()),
                instantText(followUp.reviewSavedAt()),
                nullToEmpty(followUp.settlementStatus()),
                nullToEmpty(followUp.settlementNote()),
                instantText(followUp.settlementSavedAt()),
                nullToEmpty(followUp.supportEscalationStatus()),
                instantText(followUp.supportEscalatedAt()),
                followUp.version());
    }

    private AppointmentFollowUpView emptyFollowUpView(UUID appointmentId) {
        return new AppointmentFollowUpView(
                appointmentId, "", "", "", "", "", "", "", 0L);
    }

    private Instant parseAppointmentAt(String value) {
        try {
            return LocalDateTime.parse(value, APPOINTMENT_FORMATTER)
                    .atZone(SEOUL)
                    .toInstant();
        } catch (DateTimeParseException exception) {
            throw AppointmentException.invalidRequest("예약 일시는 yyyy-MM-dd HH:mm 형식이어야 합니다.");
        }
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            throw AppointmentException.invalidRequest(label + "이(가) 필요합니다.");
        }
        return limitText(normalized, label, maxLength);
    }

    private String limitText(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw AppointmentException.invalidRequest(label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return value;
    }

    private <T extends Enum<T>> String requireCode(String value, String label, T[] values) {
        String normalized = normalizeText(value);
        List<String> allowed = new ArrayList<>();
        for (T candidate : values) {
            allowed.add(candidate.name());
        }
        if (!allowed.contains(normalized)) {
            throw AppointmentException.invalidRequest(label + " 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeOptionalCode(
            String value,
            Set<String> allowed,
            boolean uppercase,
            String label) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeText(value);
        normalized = uppercase
                ? normalized.toUpperCase(Locale.ROOT)
                : normalized.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw AppointmentException.invalidRequest(label + " 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeName(String value) {
        return normalizeText(value).replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            int prefix = digits.startsWith("02") ? 2 : 3;
            int middle = prefix + (10 - prefix) / 2;
            return digits.substring(0, prefix) + "-" + digits.substring(prefix, middle) + "-" + digits.substring(middle);
        }
        if (digits.length() == 9 && digits.startsWith("02")) {
            return digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
        }
        return digits;
    }

    private boolean isValidPhone(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return digits.startsWith("0") && digits.length() >= 9 && digits.length() <= 11;
    }

    private boolean isValidEmail(String value) {
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < value.length() - 1 && value.indexOf(' ') < 0;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String instantText(Instant value) {
        return value == null ? "" : value.toString();
    }

    private enum MobilitySupport {
        INDEPENDENT(0),
        WALKING_AID(8_000),
        WHEELCHAIR(15_000);

        private final int surcharge;

        MobilitySupport(int surcharge) {
            this.surcharge = surcharge;
        }
    }

    private enum TripType {
        ONE_WAY(0),
        ROUND_TRIP(22_000);

        private final int surcharge;

        TripType(int surcharge) {
            this.surcharge = surcharge;
        }
    }

    private enum ManagerGender {
        ANY,
        FEMALE,
        MALE
    }

    private enum PaymentMethod {
        CARD,
        EASY_PAY,
        ON_SITE
    }

    private enum Coupon {
        NONE(0),
        FIRST_VISIT(5_000),
        FAMILY(10_000);

        private final int discount;

        Coupon(int discount) {
            this.discount = discount;
        }
    }

    private record NormalizedDraft(
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            String patientConditionSummary,
            String medicationSummary,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            Instant appointmentAt,
            String meetingPlace,
            String specialNotes,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode) {
    }

    private record ParticipantPair(
            UUID patientUserId,
            UUID guardianUserId,
            ParticipantSnapshot patient,
            ParticipantSnapshot guardian) {
    }

    private record Price(
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice) {
    }
}
