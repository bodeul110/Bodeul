package com.example.bodeul.data.mock;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.MockBookingStore;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Firebase 없이도 환자와 보호자의 요청 흐름을 확인할 수 있게 하는 목업 저장소다.
 */
public class MockBookingRepository implements BookingRepository {
    private final MockBodeulRepository repository;
    private final MockBookingStore bookingStore;

    public MockBookingRepository(MockBodeulRepository repository) {
        this.repository = repository;
        this.bookingStore = new MockBookingStore(repository);
    }

    @Override
    public void getHospitalOptions(RepositoryCallback<List<BookingHospitalOption>> callback) {
        callback.onSuccess(toHospitalOptions(repository.getHospitalGuides()));
    }

    @Override
    public void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }
        callback.onSuccess(bookingStore.getAppointmentRequestsForUser(currentUser.getId(), currentUser.getRole()));
    }

    @Override
    public void getAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }

        AppointmentRequestDetail detail = bookingStore.getAppointmentRequestDetail(requestId);
        if (detail == null || !isRequestOwner(currentUser, detail.getAppointmentRequest())) {
            callback.onError("요청 상세 정보를 확인하지 못했습니다.");
            return;
        }
        callback.onSuccess(detail);
    }

    @Override
    public Runnable observeAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        getAppointmentRequestDetail(currentUser, requestId, callback);
        return () -> {};
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }

        AppointmentRequest request = bookingStore.createAppointmentRequest(
                currentUser,
                bookingRequestDraft
        );
        if (request == null) {
            callback.onError("동행 요청을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(request);
    }

    @Override
    public void updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }

        AppointmentRequest request = bookingStore.updateAppointmentRequest(
                currentUser,
                requestId,
                bookingRequestDraft
        );
        if (request == null) {
            callback.onError("접수 대기 상태 요청만 수정할 수 있습니다.");
            return;
        }
        callback.onSuccess(request);
    }

    @Override
    public void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }

        AppointmentRequest request = bookingStore.cancelAppointmentRequest(currentUser, requestId);
        if (request == null) {
            callback.onError("접수 대기 또는 매니저 배정 완료 상태 요청만 취소할 수 있습니다.");
            return;
        }
        callback.onSuccess(request);
    }

    @Override
    public void getAppointmentFollowUp(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail(requestId);
        if (detail == null || !isRequestOwner(currentUser, detail.getAppointmentRequest())) {
            callback.onError("후속 정보 조회 권한이 없습니다.");
            return;
        }
        callback.onSuccess(repository.getAppointmentFollowUpRecord(requestId));
    }

    @Override
    public void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail(requestId);
        if (detail == null || !isRequestOwner(currentUser, detail.getAppointmentRequest())) {
            callback.onError("후기 저장 권한이 없습니다.");
            return;
        }
        if (detail.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
            callback.onError("완료된 예약에서만 후기를 저장할 수 있습니다.");
            return;
        }

        AppointmentFollowUpRecord record = repository.saveAppointmentFollowUpReview(requestId, reviewRating);
        if (record == null) {
            callback.onError("후기 내용을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(record);
    }

    @Override
    public void saveAppointmentFollowUpSettlement(
            User currentUser,
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail(requestId);
        if (detail == null || !isRequestOwner(currentUser, detail.getAppointmentRequest())) {
            callback.onError("정산 후속 권한이 없습니다.");
            return;
        }
        if (detail.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
            callback.onError("완료된 예약에서만 정산 후속 확인을 저장할 수 있습니다.");
            return;
        }

        AppointmentFollowUpRecord record = repository.saveAppointmentFollowUpSettlement(
                requestId,
                settlementStatus,
                settlementNote
        );
        if (record == null) {
            callback.onError("정산 후속 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(record);
    }

    @Override
    public void saveAppointmentFollowUpSupportEscalation(
            User currentUser,
            String requestId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail(requestId);
        if (detail == null || !isRequestOwner(currentUser, detail.getAppointmentRequest())) {
            callback.onError("SOS 후속 권한이 없습니다.");
            return;
        }
        if (detail.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
            callback.onError("완료된 예약에서만 SOS 후속 기록을 저장할 수 있습니다.");
            return;
        }

        AppointmentFollowUpRecord record = repository.saveAppointmentFollowUpSupportEscalation(
                requestId,
                escalationStatus
        );
        if (record == null) {
            callback.onError("SOS 후속 기록을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(record);
    }

    @Override
    public void sendCompanionChatMessage(
            User currentUser,
            String requestId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        AppointmentRequestDetail detail = bookingStore.appendBookingCompanionChatMessage(
                currentUser,
                requestId,
                message,
                attachments
        );
        if (detail == null) {
            callback.onError("안심 채팅 메시지를 전송하지 못했습니다.");
            return;
        }
        callback.onSuccess(detail);
    }

    @Override
    public void markCompanionChatRead(User currentUser, String requestId) {
        if (!supportsRole(currentUser.getRole())) {
            return;
        }
        bookingStore.markBookingCompanionChatRead(currentUser, requestId);
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private boolean supportsRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }

    private boolean isRequestOwner(User currentUser, AppointmentRequest request) {
        if (currentUser.getRole() == UserRole.PATIENT) {
            return currentUser.getId().equals(request.getPatientUserId());
        }
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            return currentUser.getId().equals(request.getGuardianUserId());
        }
        return false;
    }

    private List<BookingHospitalOption> toHospitalOptions(List<HospitalGuide> guides) {
        Map<String, TreeSet<String>> departmentsByHospital = new TreeMap<>();
        for (HospitalGuide guide : guides) {
            if (guide == null) {
                continue;
            }
            String hospitalName = guide.getHospitalName();
            String departmentName = guide.getDepartmentName();
            if (hospitalName == null || hospitalName.trim().isEmpty()
                    || departmentName == null || departmentName.trim().isEmpty()) {
                continue;
            }
            departmentsByHospital
                    .computeIfAbsent(hospitalName.trim(), key -> new TreeSet<>())
                    .add(departmentName.trim());
        }

        List<BookingHospitalOption> options = new ArrayList<>();
        for (Map.Entry<String, TreeSet<String>> entry : departmentsByHospital.entrySet()) {
            options.add(new BookingHospitalOption(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
        return options;
    }
}
