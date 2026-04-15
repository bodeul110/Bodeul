package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 환자와 보호자의 동행 신청 생성 및 상태 조회를 담당하는 저장소 계약이다.
 */
public interface BookingRepository {
    void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback);

    void createAppointmentRequest(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            RepositoryCallback<AppointmentRequest> callback
    );

    void updateAppointmentRequest(
            User currentUser,
            String requestId,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            RepositoryCallback<AppointmentRequest> callback
    );

    void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    );

    boolean isFirebaseBacked();
}
