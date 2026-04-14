package com.example.bodeul.data.mock;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;

/**
 * Firebase 없이도 환자와 보호자의 신청 흐름을 확인할 수 있게 하는 목업 저장소다.
 */
public class MockBookingRepository implements BookingRepository {
    private final MockBodeulRepository repository;

    public MockBookingRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해주세요.");
            return;
        }
        callback.onSuccess(repository.getAppointmentRequestsForUser(currentUser.getId(), currentUser.getRole()));
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해주세요.");
            return;
        }

        AppointmentRequest request = repository.createAppointmentRequest(
                currentUser,
                hospitalName,
                departmentName,
                appointmentAt,
                meetingPlace,
                specialNotes
        );
        if (request == null) {
            callback.onError("동행 신청을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(request);
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private boolean supportsRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }
}
