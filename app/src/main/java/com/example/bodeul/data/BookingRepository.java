package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 환자와 보호자의 동행 신청 생성 및 상태 조회를 담당하는 저장소 계약이다.
 */
public interface BookingRepository {
    // 현재 로그인한 환자 또는 보호자의 신청 목록을 최신 상태로 조회한다.
    void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback);

    // 병원 동행 신청을 생성하고 저장된 요청을 반환한다.
    void createAppointmentRequest(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            RepositoryCallback<AppointmentRequest> callback
    );

    // 화면에서 데모 모드 안내를 분기할 때 사용한다.
    boolean isFirebaseBacked();
}
