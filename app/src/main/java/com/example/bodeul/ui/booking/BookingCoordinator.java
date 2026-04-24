package com.example.bodeul.ui.booking;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.User;

import java.util.Collections;
import java.util.List;

/**
 * 예약 화면에 필요한 저장소 호출과 대시보드 조합을 맡는다.
 */
public final class BookingCoordinator {
    private final BookingRepository bookingRepository;

    public BookingCoordinator(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public boolean isFirebaseBacked() {
        return bookingRepository.isFirebaseBacked();
    }

    public void loadDashboard(User currentUser, RepositoryCallback<BookingDashboard> callback) {
        bookingRepository.getMyAppointmentRequests(currentUser, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                List<AppointmentRequest> safeResult = result == null ? Collections.emptyList() : result;
                callback.onSuccess(new BookingDashboard(currentUser, safeResult));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
