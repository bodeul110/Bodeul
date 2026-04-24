package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 환자와 보호자의 동행 신청 생성 및 상태 조회를 담당하는 저장소 계약이다.
 */
public interface BookingRepository {
    void getHospitalOptions(RepositoryCallback<List<BookingHospitalOption>> callback);

    void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback);

    void getAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    );

    void createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    );

    void updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    );

    void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    );

    void getAppointmentFollowUp(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    );

    void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    );

    void saveAppointmentFollowUpSettlement(
            User currentUser,
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    );

    void saveAppointmentFollowUpSupportEscalation(
            User currentUser,
            String requestId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    );

    boolean isFirebaseBacked();
}
