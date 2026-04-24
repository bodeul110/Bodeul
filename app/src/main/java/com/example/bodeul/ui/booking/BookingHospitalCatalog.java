package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingHospitalOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 병원 선택 화면에서 검색과 정렬에 사용할 병원 후보 목록을 보관한다.
 */
public final class BookingHospitalCatalog {
    private final List<BookingHospitalOption> options;

    public BookingHospitalCatalog(List<BookingHospitalOption> options) {
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
    }

    public static BookingHospitalCatalog empty() {
        return new BookingHospitalCatalog(Collections.emptyList());
    }

    public List<BookingHospitalOption> filter(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return options;
        }

        List<BookingHospitalOption> filtered = new ArrayList<>();
        for (BookingHospitalOption option : options) {
            if (matches(option, normalizedQuery)) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }

    private boolean matches(BookingHospitalOption option, String normalizedQuery) {
        if (normalize(option.getHospitalName()).contains(normalizedQuery)) {
            return true;
        }
        for (String departmentName : option.getDepartmentNames()) {
            if (normalize(departmentName).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.KOREA);
    }
}
