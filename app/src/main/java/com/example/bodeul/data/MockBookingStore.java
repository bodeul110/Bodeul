package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.UserProfileSanitizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MockBookingStore {
    private final MockBodeulRepository repository;

    public MockBookingStore(MockBodeulRepository repository) {
        this.repository = repository;
    }

    public List<AppointmentRequest> getAppointmentRequestsForUser(String userId, UserRole role) {
        List<AppointmentRequest> result = new ArrayList<>();
        for (AppointmentRequest request : repository.getMutableAppointmentRequests()) {
            if (repository.matchesRequestOwner(request, userId, role)) {
                result.add(request);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public AppointmentRequestDetail getAppointmentRequestDetail(String requestId) {
        AppointmentRequest request = repository.findAppointmentRequest(requestId);
        if (request == null) {
            return null;
        }

        User patient = repository.findUserById(request.getPatientUserId());
        User guardian = repository.findUserById(request.getGuardianUserId());
        User manager = repository.findUserById(request.getManagerUserId());
        CompanionSession session = repository.findSessionByRequestId(requestId);
        SessionReport report = session == null ? null : repository.getSessionReport(session.getId());
        HospitalGuide guide = repository.getHospitalGuide(
                request.getHospitalName(),
                request.getDepartmentName()
        );
        AppointmentFollowUpRecord followUpRecord = request.getStatus() == AppointmentStatus.COMPLETED
                ? repository.getAppointmentFollowUpRecord(requestId)
                : null;
        return new AppointmentRequestDetail(
                request,
                patient,
                guardian,
                manager,
                session,
                report,
                guide,
                followUpRecord
        );
    }

    @Nullable
    public AppointmentRequest createAppointmentRequest(User currentUser, BookingRequestDraft bookingRequestDraft) {
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        String normalizedLinkedName =
                UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName());
        String normalizedLinkedPhone =
                UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone());
        String normalizedLinkedEmail =
                UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail());
        User linkedParticipant = repository.resolveLinkedParticipant(
                repository.resolveCounterpartRole(currentUser.getRole()),
                normalizedLinkedEmail,
                normalizedLinkedPhone
        );

        String patientUserId;
        String guardianUserId;
        String patientName;
        String patientPhone;
        String patientEmail;
        String guardianName;
        String guardianPhone;
        String guardianEmail;

        if (currentUser.getRole() == UserRole.PATIENT) {
            patientUserId = currentUser.getId();
            patientName = UserProfileSanitizer.normalizeName(currentUser.getName());
            patientPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            patientEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            guardianUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            guardianName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            guardianPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            guardianEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        } else {
            guardianUserId = currentUser.getId();
            guardianName = UserProfileSanitizer.normalizeName(currentUser.getName());
            guardianPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            guardianEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            patientUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            patientName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            patientPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            patientEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        }

        AppointmentRequest request = repository.createSnapshotBackedRequest(
                "request-" + (repository.getMutableAppointmentRequests().size() + 1),
                bookingRequestDraft,
                patientUserId,
                guardianUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail
        );
        repository.getMutableAppointmentRequests().add(0, request);
        return request;
    }

    @Nullable
    public AppointmentRequest updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft
    ) {
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        AppointmentRequest existingRequest = repository.findAppointmentRequest(requestId);
        if (existingRequest == null
                || existingRequest.getStatus() != AppointmentStatus.REQUESTED
                || !repository.matchesRequestOwner(existingRequest, currentUser.getId(), currentUser.getRole())) {
            return null;
        }

        String normalizedLinkedName =
                UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName());
        String normalizedLinkedPhone =
                UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone());
        String normalizedLinkedEmail =
                UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail());
        User linkedParticipant = repository.resolveLinkedParticipant(
                repository.resolveCounterpartRole(currentUser.getRole()),
                normalizedLinkedEmail,
                normalizedLinkedPhone
        );

        String patientUserId;
        String guardianUserId;
        String patientName;
        String patientPhone;
        String patientEmail;
        String guardianName;
        String guardianPhone;
        String guardianEmail;

        if (currentUser.getRole() == UserRole.PATIENT) {
            patientUserId = currentUser.getId();
            patientName = UserProfileSanitizer.normalizeName(currentUser.getName());
            patientPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            patientEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            guardianUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            guardianName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            guardianPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            guardianEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        } else {
            guardianUserId = currentUser.getId();
            guardianName = UserProfileSanitizer.normalizeName(currentUser.getName());
            guardianPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            guardianEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            patientUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            patientName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            patientPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            patientEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        }

        AppointmentRequest updatedRequest = repository.createSnapshotBackedRequest(
                existingRequest.getId(),
                bookingRequestDraft,
                patientUserId,
                guardianUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail
        );
        int requestIndex = repository.getMutableAppointmentRequests().indexOf(existingRequest);
        repository.getMutableAppointmentRequests().set(requestIndex, updatedRequest);
        return updatedRequest;
    }

    @Nullable
    public AppointmentRequest cancelAppointmentRequest(User currentUser, String requestId) {
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        AppointmentRequest existingRequest = repository.findAppointmentRequest(requestId);
        if (existingRequest == null
                || !repository.canCancelRequest(existingRequest)
                || !repository.matchesRequestOwner(existingRequest, currentUser.getId(), currentUser.getRole())) {
            return null;
        }

        existingRequest.setStatus(AppointmentStatus.CANCELED);
        CompanionSession session = repository.findSessionByRequestId(requestId);
        if (session != null && session.getStatus() != SessionStatus.COMPLETED) {
            session.setStatus(SessionStatus.CANCELED);
        }
        return existingRequest;
    }

    @Nullable
    public AppointmentRequest createLinkedAppointmentRequest(
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes
    ) {
        User patient = repository.findUserById(patientUserId);
        User guardian = repository.findUserById(guardianUserId);
        if (patient == null || guardian == null) {
            return null;
        }

        AppointmentRequest request = new AppointmentRequest(
                "request-" + (repository.getMutableAppointmentRequests().size() + 1),
                repository.normalizeText(patientUserId),
                repository.normalizeText(guardianUserId),
                repository.normalizeText(hospitalName),
                repository.normalizeText(departmentName),
                repository.normalizeText(appointmentAt),
                repository.normalizeText(meetingPlace),
                repository.normalizeText(specialNotes),
                AppointmentStatus.REQUESTED,
                null,
                UserProfileSanitizer.normalizeName(patient.getName()),
                UserProfileSanitizer.normalizePhone(patient.getPhone()),
                UserProfileSanitizer.normalizeEmail(patient.getEmail()),
                UserProfileSanitizer.normalizeName(guardian.getName()),
                UserProfileSanitizer.normalizePhone(guardian.getPhone()),
                UserProfileSanitizer.normalizeEmail(guardian.getEmail())
        );
        repository.getMutableAppointmentRequests().add(0, request);
        return request;
    }

    @Nullable
    public AppointmentRequestDetail appendBookingCompanionChatMessage(
            User currentUser,
            String requestId,
            String message,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        AppointmentRequestDetail detail = getAppointmentRequestDetail(requestId);
        if (detail == null
                || !repository.matchesRequestOwner(
                        detail.getAppointmentRequest(),
                        currentUser.getId(),
                        currentUser.getRole()
                )) {
            return null;
        }
        CompanionSession session = detail.getSession();
        if (session == null) {
            return null;
        }
        long sentAtMillis = System.currentTimeMillis();
        session.addChatMessage(new CompanionChatMessage(
                currentUser.getRole(),
                repository.normalizeText(message),
                sentAtMillis,
                attachments
        ));
        session.markChatRead(currentUser.getRole(), sentAtMillis);
        return getAppointmentRequestDetail(requestId);
    }

    @Nullable
    public AppointmentRequestDetail markBookingCompanionChatRead(User currentUser, String requestId) {
        AppointmentRequestDetail detail = getAppointmentRequestDetail(requestId);
        if (detail == null
                || !repository.matchesRequestOwner(
                        detail.getAppointmentRequest(),
                        currentUser.getId(),
                        currentUser.getRole()
                )) {
            return null;
        }
        CompanionSession session = detail.getSession();
        if (session == null) {
            return null;
        }
        session.markChatRead(currentUser.getRole(), System.currentTimeMillis());
        return getAppointmentRequestDetail(requestId);
    }
}
