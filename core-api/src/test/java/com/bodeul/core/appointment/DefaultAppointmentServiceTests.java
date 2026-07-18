package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppUserProfileRepository.AppUserProfile;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpRecord;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAppointmentServiceTests {

    private static final UUID PATIENT_ID = UUID.fromString("db7cc9f9-4f3e-4f73-b572-cf653564e887");
    private static final UUID GUARDIAN_ID = UUID.fromString("bfbc7b03-3f42-4016-85c0-0981097bf1f2");
    private static final UUID MANAGER_ID = UUID.fromString("04e9b7fd-9727-4f81-af7b-ab3534339fd0");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("ced5cb21-c07d-4d0e-a151-2994b6d40793");
    private static final UUID APPOINTMENT_ID = UUID.fromString("27bf3a07-6605-48ab-adbf-c7b18551a639");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    private FakeAppointmentRepository appointmentRepository;
    private FakeAppUserProfileRepository profileRepository;
    private DefaultAppointmentService service;

    @BeforeEach
    void setUp() {
        appointmentRepository = new FakeAppointmentRepository();
        profileRepository = new FakeAppUserProfileRepository();
        profileRepository.add(new AppUserProfile(
                PATIENT_ID,
                AppUserRole.PATIENT,
                "환자 사용자",
                "patient@example.com",
                "010-1234-5678"));
        profileRepository.add(new AppUserProfile(
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                "보호자 사용자",
                "guardian@example.com",
                "010-9876-5432"));
        profileRepository.add(new AppUserProfile(
                MANAGER_ID,
                AppUserRole.MANAGER,
                "매니저 사용자",
                "manager@example.com",
                "010-5555-7777"));
        service = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void patientCreatesAppointmentWithServerOwnedPriceAndLinkedGuardian() {
        var created = service.createAppointment(
                patient(),
                new AppointmentService.CreateAppointmentCommand(
                        UUID.fromString("7e80b784-1212-429e-b4ea-a5f9e8db7488"),
                        draft()));

        assertThat(created.id()).isEqualTo(APPOINTMENT_ID);
        assertThat(created.patientUserId()).isEqualTo(PATIENT_ID);
        assertThat(created.guardianUserId()).isEqualTo(GUARDIAN_ID);
        assertThat(created.patientName()).isEqualTo("환자 사용자");
        assertThat(created.guardianName()).isEqualTo("보호자 사용자");
        assertThat(created.basePrice()).isEqualTo(69_000);
        assertThat(created.optionSurchargePrice()).isEqualTo(37_000);
        assertThat(created.couponDiscountPrice()).isEqualTo(10_000);
        assertThat(created.finalPrice()).isEqualTo(96_000);
        assertThat(created.paymentStatusCode()).isEqualTo("PENDING");
        assertThat(created.status()).isEqualTo("REQUESTED");
    }

    @Test
    void repeatedClientRequestIdReturnsTheExistingAppointment() {
        UUID clientRequestId = UUID.fromString("c521b77c-2655-4604-9883-c92bc4d828f7");
        var command = new AppointmentService.CreateAppointmentCommand(clientRequestId, draft());

        var first = service.createAppointment(patient(), command);
        var second = service.createAppointment(patient(), command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void managerCanReadAssignedAppointmentList() {
        var manager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "manager-firebase-uid",
                AppUserRole.MANAGER);

        assertThat(service.getMyAppointments(manager)).isEmpty();
    }

    @Test
    void assignedManagerProfileIsIncludedInAppointmentView() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1, MANAGER_ID));

        var appointment = service.getAppointment(patient(), APPOINTMENT_ID);

        assertThat(appointment.managerUserId()).isEqualTo(MANAGER_ID);
        assertThat(appointment.managerName()).isEqualTo("매니저 사용자");
        assertThat(appointment.managerEmail()).isEqualTo("manager@example.com");
        assertThat(appointment.managerPhone()).isEqualTo("010-5555-7777");
    }

    @Test
    void managerCannotUsePatientAppointmentWriteApi() {
        var manager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "manager-firebase-uid",
                AppUserRole.MANAGER);

        assertThatThrownBy(() -> service.createAppointment(
                manager,
                new AppointmentService.CreateAppointmentCommand(UUID.randomUUID(), draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_role_not_supported");
    }

    @Test
    void participantFromAnotherAppointmentIsRejected() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var otherPatient = new AppUserRepository.AppUser(
                OTHER_PATIENT_ID,
                "other-patient",
                AppUserRole.PATIENT);

        assertThatThrownBy(() -> service.getAppointment(otherPatient, APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_permission_denied");
    }

    @Test
    void staleVersionIsRejectedBeforeUpdate() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 3));

        assertThatThrownBy(() -> service.updateAppointment(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(2, draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_version_conflict");
    }

    @Test
    void linkedParticipantCannotRemoveTheOriginalRequester() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);
        AppointmentService.AppointmentDraft differentPatient = new AppointmentService.AppointmentDraft(
                "다른 환자",
                "010-1111-2222",
                "different@example.com",
                "보행 지원 필요",
                "",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "",
                "WALKING_AID",
                "ONE_WAY",
                "ANY",
                "CARD",
                "NONE");

        assertThatThrownBy(() -> service.updateAppointment(
                guardian,
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0, differentPatient)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_requester_link_conflict");
    }

    @Test
    void matchedAppointmentCancellationAlsoCancelsTheActiveSession() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1));

        var canceled = service.cancelAppointment(patient(), APPOINTMENT_ID, 1);

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(appointmentRepository.sessionCanceled).isTrue();
    }

    @Test
    void requestedAppointmentCanBeCanceled() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 4));

        var canceled = service.cancelAppointment(patient(), APPOINTMENT_ID, 4);

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.version()).isEqualTo(5);
    }

    @Test
    void profileBackfillIsRequiredBeforeCreatingAppointments() {
        profileRepository.profiles.put(PATIENT_ID, new AppUserProfile(
                PATIENT_ID,
                AppUserRole.PATIENT,
                "",
                "",
                ""));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                new AppointmentService.CreateAppointmentCommand(UUID.randomUUID(), draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_profile_not_ready");
    }

    @Test
    void appointmentTimeMustBeInTheFuture() {
        AppointmentService.AppointmentDraft pastDraft = new AppointmentService.AppointmentDraft(
                "보호자 사용자",
                "010-9876-5432",
                "guardian@example.com",
                "",
                "",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-07-17 10:30",
                "본관 1층",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                "CARD",
                "NONE");

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                new AppointmentService.CreateAppointmentCommand(UUID.randomUUID(), pastDraft)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("invalid_appointment_request");
    }

    @Test
    void participantReadsEmptyFollowUpBeforeFirstSave() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));

        var followUp = service.getAppointmentFollowUp(patient(), APPOINTMENT_ID);

        assertThat(followUp.appointmentRequestId()).isEqualTo(APPOINTMENT_ID);
        assertThat(followUp.reviewRatingCode()).isEmpty();
        assertThat(followUp.version()).isZero();
    }

    @Test
    void participantSavesFollowUpActionsWithOptimisticVersion() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));

        var review = service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        0, "excellent", null, null, null));
        var settlement = service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        review.version(), null, "NEEDS_HELP", "결제 내역 확인 요청", null));

        assertThat(settlement.reviewRatingCode()).isEqualTo("excellent");
        assertThat(settlement.settlementFollowUpStatus()).isEqualTo("NEEDS_HELP");
        assertThat(settlement.settlementFollowUpNote()).isEqualTo("결제 내역 확인 요청");
        assertThat(settlement.version()).isEqualTo(2);
    }

    @Test
    void followUpWriteRequiresCompletedAppointment() {
        appointmentRepository.current = Optional.of(existingAppointment("IN_PROGRESS", 3));

        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        0, "good", null, null, null)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_state_conflict");
    }

    @Test
    void staleFollowUpVersionIsRejected() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        appointmentRepository.followUp = Optional.of(new AppointmentFollowUpRecord(
                APPOINTMENT_ID,
                "good",
                NOW,
                "",
                "",
                null,
                "",
                null,
                2));

        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        1, null, null, null, "GUIDE_VIEWED")))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_version_conflict");
    }

    private AppUserRepository.AppUser patient() {
        return new AppUserRepository.AppUser(PATIENT_ID, "patient-firebase-uid", AppUserRole.PATIENT);
    }

    private AppointmentService.AppointmentDraft draft() {
        return new AppointmentService.AppointmentDraft(
                "입력된 보호자",
                "01098765432",
                "GUARDIAN@example.com",
                "휠체어 이동 지원 필요",
                "아침 약 복용",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "진료 20분 전 도착",
                "WHEELCHAIR",
                "ROUND_TRIP",
                "ANY",
                "CARD",
                "FAMILY");
    }

    private AppointmentRecord existingAppointment(String status, long version) {
        return existingAppointment(status, version, null);
    }

    private AppointmentRecord existingAppointment(String status, long version, UUID managerUserId) {
        return new AppointmentRecord(
                APPOINTMENT_ID,
                "legacy-firestore-id",
                PATIENT_ID,
                GUARDIAN_ID,
                managerUserId,
                PATIENT_ID,
                AppUserRole.PATIENT,
                new AppointmentRepository.ParticipantSnapshot(
                        "환자 사용자", "010-1234-5678", "patient@example.com"),
                new AppointmentRepository.ParticipantSnapshot(
                        "보호자 사용자", "010-9876-5432", "guardian@example.com"),
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                Instant.parse("2026-12-20T01:30:00Z"),
                "본관 1층",
                "",
                "",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                status,
                69_000,
                0,
                0,
                69_000,
                "CARD",
                "NONE",
                "PENDING",
                "",
                null,
                "",
                version);
    }

    private final class FakeAppointmentRepository implements AppointmentRepository {
        private Optional<AppointmentRecord> current = Optional.empty();
        private Optional<AppointmentFollowUpRecord> followUp = Optional.empty();
        private final Map<String, AppointmentRecord> byClientRequest = new HashMap<>();
        private int insertCount;
        private boolean sessionCanceled;

        @Override
        public List<AppointmentRecord> findAllForParticipant(UUID userId, AppUserRole role) {
            return current.stream().toList();
        }

        @Override
        public Optional<AppointmentRecord> findById(UUID appointmentId) {
            return current.filter(appointment -> appointment.id().equals(appointmentId));
        }

        @Override
        public Optional<AppointmentRecord> findByClientRequestId(
                UUID requesterUserId,
                UUID clientRequestId) {
            return Optional.ofNullable(byClientRequest.get(requesterUserId + ":" + clientRequestId));
        }

        @Override
        public Optional<AppointmentRecord> insert(AppointmentMutation mutation) {
            insertCount++;
            AppointmentRecord inserted = fromMutation(mutation, "REQUESTED", 0);
            current = Optional.of(inserted);
            byClientRequest.put(
                    mutation.requesterUserId() + ":" + mutation.clientRequestId(),
                    inserted);
            return Optional.of(inserted);
        }

        @Override
        public Optional<AppointmentRecord> update(
                UUID appointmentId,
                long expectedVersion,
                AppointmentMutation mutation) {
            if (current.isEmpty() || current.get().version() != expectedVersion) {
                return Optional.empty();
            }
            AppointmentRecord updated = fromMutation(mutation, "REQUESTED", expectedVersion + 1);
            current = Optional.of(updated);
            return current;
        }

        @Override
        public Optional<AppointmentRecord> cancel(UUID appointmentId, long expectedVersion) {
            if (current.isEmpty() || current.get().version() != expectedVersion) {
                return Optional.empty();
            }
            AppointmentRecord appointment = current.get();
            current = Optional.of(new AppointmentRecord(
                    appointment.id(),
                    appointment.firestoreId(),
                    appointment.patientUserId(),
                    appointment.guardianUserId(),
                    appointment.managerUserId(),
                    appointment.requesterUserId(),
                    appointment.requesterRole(),
                    appointment.patient(),
                    appointment.guardian(),
                    appointment.hospitalName(),
                    appointment.departmentName(),
                    appointment.hospitalLatitude(),
                    appointment.hospitalLongitude(),
                    appointment.appointmentAt(),
                    appointment.meetingPlace(),
                    appointment.specialNotes(),
                    appointment.patientConditionSummary(),
                    appointment.medicationSummary(),
                    appointment.mobilitySupportCode(),
                    appointment.tripTypeCode(),
                    appointment.managerGenderPreferenceCode(),
                    "CANCELED",
                    appointment.basePrice(),
                    appointment.optionSurchargePrice(),
                    appointment.couponDiscountPrice(),
                    appointment.finalPrice(),
                    appointment.paymentMethodCode(),
                    appointment.couponCode(),
                    appointment.paymentStatusCode(),
                    appointment.paymentApprovalCode(),
                    appointment.paymentApprovedAt(),
                    appointment.paymentProviderLabel(),
                    expectedVersion + 1));
            return current;
        }

        @Override
        public boolean cancelActiveSession(UUID appointmentId) {
            sessionCanceled = true;
            return true;
        }

        @Override
        public Optional<AppointmentFollowUpRecord> findFollowUpByAppointmentId(UUID appointmentId) {
            return followUp.filter(value -> value.appointmentId().equals(appointmentId));
        }

        @Override
        public Optional<AppointmentFollowUpRecord> insertFollowUp(AppointmentFollowUpMutation mutation) {
            if (followUp.isPresent() || mutation.expectedVersion() != 0) {
                return Optional.empty();
            }
            followUp = Optional.of(new AppointmentFollowUpRecord(
                    mutation.appointmentId(),
                    valueOrEmpty(mutation.reviewRatingCode()),
                    mutation.reviewRatingCode() == null ? null : NOW,
                    valueOrEmpty(mutation.settlementStatus()),
                    valueOrEmpty(mutation.settlementNote()),
                    mutation.settlementStatus() == null ? null : NOW,
                    valueOrEmpty(mutation.supportEscalationStatus()),
                    mutation.supportEscalationStatus() == null ? null : NOW,
                    1));
            return followUp;
        }

        @Override
        public Optional<AppointmentFollowUpRecord> updateFollowUp(AppointmentFollowUpMutation mutation) {
            if (followUp.isEmpty() || followUp.get().version() != mutation.expectedVersion()) {
                return Optional.empty();
            }
            AppointmentFollowUpRecord currentFollowUp = followUp.get();
            followUp = Optional.of(new AppointmentFollowUpRecord(
                    mutation.appointmentId(),
                    mutation.reviewRatingCode() == null
                            ? currentFollowUp.reviewRatingCode()
                            : mutation.reviewRatingCode(),
                    mutation.reviewRatingCode() == null ? currentFollowUp.reviewSavedAt() : NOW,
                    mutation.settlementStatus() == null
                            ? currentFollowUp.settlementStatus()
                            : mutation.settlementStatus(),
                    mutation.settlementStatus() == null
                            ? currentFollowUp.settlementNote()
                            : valueOrEmpty(mutation.settlementNote()),
                    mutation.settlementStatus() == null ? currentFollowUp.settlementSavedAt() : NOW,
                    mutation.supportEscalationStatus() == null
                            ? currentFollowUp.supportEscalationStatus()
                            : mutation.supportEscalationStatus(),
                    mutation.supportEscalationStatus() == null
                            ? currentFollowUp.supportEscalatedAt()
                            : NOW,
                    currentFollowUp.version() + 1));
            return followUp;
        }

        private String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }

        private AppointmentRecord fromMutation(
                AppointmentMutation mutation,
                String status,
                long version) {
            return new AppointmentRecord(
                    APPOINTMENT_ID,
                    null,
                    mutation.patientUserId(),
                    mutation.guardianUserId(),
                    null,
                    mutation.requesterUserId(),
                    mutation.requesterRole(),
                    mutation.patient(),
                    mutation.guardian(),
                    mutation.hospitalName(),
                    mutation.departmentName(),
                    mutation.hospitalLatitude(),
                    mutation.hospitalLongitude(),
                    mutation.appointmentAt(),
                    mutation.meetingPlace(),
                    mutation.specialNotes(),
                    mutation.patientConditionSummary(),
                    mutation.medicationSummary(),
                    mutation.mobilitySupportCode(),
                    mutation.tripTypeCode(),
                    mutation.managerGenderPreferenceCode(),
                    status,
                    mutation.basePrice(),
                    mutation.optionSurchargePrice(),
                    mutation.couponDiscountPrice(),
                    mutation.finalPrice(),
                    mutation.paymentMethodCode(),
                    mutation.couponCode(),
                    mutation.paymentStatusCode(),
                    "",
                    null,
                    "",
                    version);
        }
    }

    private static final class FakeAppUserProfileRepository implements AppUserProfileRepository {
        private final Map<UUID, AppUserProfile> profiles = new HashMap<>();

        void add(AppUserProfile profile) {
            profiles.put(profile.id(), profile);
        }

        @Override
        public Optional<AppUserProfile> findById(UUID userId) {
            return Optional.ofNullable(profiles.get(userId));
        }

        @Override
        public List<AppUserProfile> findByEmail(AppUserRole role, String email) {
            return matching(role, email, true);
        }

        @Override
        public List<AppUserProfile> findByPhone(AppUserRole role, String phone) {
            return matching(role, phone, false);
        }

        private List<AppUserProfile> matching(AppUserRole role, String value, boolean email) {
            List<AppUserProfile> matches = new ArrayList<>();
            for (AppUserProfile profile : profiles.values()) {
                String profileValue = email ? profile.email() : profile.phone();
                if (profile.role() == role && profileValue.equals(value)) {
                    matches.add(profile);
                }
            }
            return matches;
        }
    }
}
