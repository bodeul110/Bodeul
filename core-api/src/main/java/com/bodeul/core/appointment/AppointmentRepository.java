package com.bodeul.core.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

interface AppointmentRepository {

    List<AppointmentRecord> findAllForParticipant(UUID userId, AppUserRole role);

    Optional<AppointmentRecord> findById(UUID appointmentId);

    Optional<AppointmentRecord> findByClientRequestId(UUID requesterUserId, UUID clientRequestId);

    Optional<AppointmentRecord> insert(AppointmentMutation mutation);

    Optional<AppointmentRecord> update(
            UUID appointmentId,
            long expectedVersion,
            AppointmentMutation mutation);

    Optional<AppointmentRecord> cancel(
            UUID appointmentId,
            long expectedVersion);

    boolean cancelActiveSession(UUID appointmentId);

    Optional<AppointmentFollowUpRecord> findFollowUpByAppointmentId(UUID appointmentId);

    Optional<AppointmentFollowUpRecord> insertFollowUp(AppointmentFollowUpMutation mutation);

    Optional<AppointmentFollowUpRecord> updateFollowUp(AppointmentFollowUpMutation mutation);

    record AppointmentMutation(
            UUID clientRequestId,
            UUID patientUserId,
            UUID guardianUserId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantSnapshot patient,
            ParticipantSnapshot guardian,
            ParticipantSnapshot requester,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            Instant appointmentAt,
            long appointmentAtEpochMillis,
            String appointmentDateKey,
            String meetingPlace,
            String specialNotes,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice,
            String paymentMethodCode,
            String couponCode,
            String paymentStatusCode) {
    }

    record AppointmentRecord(
            UUID id,
            String firestoreId,
            UUID patientUserId,
            UUID guardianUserId,
            UUID managerUserId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantSnapshot patient,
            ParticipantSnapshot guardian,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            Instant appointmentAt,
            String meetingPlace,
            String specialNotes,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String status,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice,
            String paymentMethodCode,
            String couponCode,
            String paymentStatusCode,
            String paymentApprovalCode,
            Instant paymentApprovedAt,
            String paymentProviderLabel,
            long version) {
    }

    record ParticipantSnapshot(String name, String phone, String email) {
    }

    record AppointmentFollowUpMutation(
            UUID appointmentId,
            UUID actorUserId,
            long expectedVersion,
            String reviewRatingCode,
            String settlementStatus,
            String settlementNote,
            String supportEscalationStatus) {
    }

    record AppointmentFollowUpRecord(
            UUID appointmentId,
            String reviewRatingCode,
            Instant reviewSavedAt,
            String settlementStatus,
            String settlementNote,
            Instant settlementSavedAt,
            String supportEscalationStatus,
            Instant supportEscalatedAt,
            long version) {
    }
}
