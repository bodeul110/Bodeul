package com.example.bodeul.ui.booking;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.Collections;
import java.util.List;

/**
 * 예약 신청 화면 상단에 필요한 상태를 한 번에 전달한다.
 */
public final class BookingDashboard {
    private final User user;
    private final List<AppointmentRequest> requests;

    public BookingDashboard(User user, List<AppointmentRequest> requests) {
        this.user = user;
        this.requests = Collections.unmodifiableList(requests);
    }

    public User getUser() {
        return user;
    }

    public List<AppointmentRequest> getRequests() {
        return requests;
    }

    public boolean hasRequests() {
        return !requests.isEmpty();
    }

    public boolean isGuardianUser() {
        return user.getRole() == UserRole.GUARDIAN;
    }

    @Nullable
    public AppointmentRequest getLatestRequest() {
        for (AppointmentRequest request : requests) {
            if (request.getStatus() != AppointmentStatus.COMPLETED
                    && request.getStatus() != AppointmentStatus.CANCELED) {
                return request;
            }
        }
        return requests.isEmpty() ? null : requests.get(0);
    }
}
