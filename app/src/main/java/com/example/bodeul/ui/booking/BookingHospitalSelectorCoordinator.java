package com.example.bodeul.ui.booking;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingHospitalOption;

import java.util.Collections;
import java.util.List;

/**
 * 병원 선택 화면에 필요한 저장소 조회와 화면용 목록 조합을 맡는다.
 */
public final class BookingHospitalSelectorCoordinator {
    private final BookingRepository bookingRepository;

    public BookingHospitalSelectorCoordinator(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public void loadCatalog(RepositoryCallback<BookingHospitalCatalog> callback) {
        bookingRepository.getHospitalOptions(new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override
            public void onSuccess(List<BookingHospitalOption> result) {
                List<BookingHospitalOption> safeResult = result == null ? Collections.emptyList() : result;
                callback.onSuccess(new BookingHospitalCatalog(safeResult));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
