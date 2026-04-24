package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;

import java.util.ArrayList;
import java.util.List;

/**
 * 병원 선택 결과를 기반으로 만남 위치 후보와 안내 문구를 조합한다.
 */
public final class BookingLocationSelectorCoordinator {
    private final Context context;

    public BookingLocationSelectorCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    public BookingLocationSelectorScreenModel createScreenModel(
            BookingHospitalSelection hospitalSelection,
            BookingMeetingLocationSelection currentSelection
    ) {
        String hospitalName = hospitalSelection.getHospitalName();
        String departmentName = hospitalSelection.getDepartmentName();
        List<BookingMeetingPointOption> pointOptions = createPointOptions(hospitalName, departmentName);
        String selectedPointId = currentSelection.getPointId();
        if (TextUtils.isEmpty(selectedPointId) && !pointOptions.isEmpty()) {
            selectedPointId = pointOptions.get(0).getId();
        }
        return new BookingLocationSelectorScreenModel(
                context.getString(R.string.booking_location_selector_badge),
                context.getString(R.string.booking_location_selector_title_format, hospitalName),
                context.getString(
                        R.string.booking_location_selector_body_format,
                        hospitalName,
                        departmentName
                ),
                context.getString(R.string.booking_location_selector_helper),
                selectedPointId,
                pointOptions
        );
    }

    private List<BookingMeetingPointOption> createPointOptions(String hospitalName, String departmentName) {
        List<BookingMeetingPointOption> options = new ArrayList<>();
        options.add(new BookingMeetingPointOption(
                "main",
                context.getString(R.string.booking_location_point_main_title, hospitalName),
                context.getString(R.string.booking_location_point_main_body),
                context.getString(R.string.booking_location_point_main_place, hospitalName),
                0.28f,
                0.58f
        ));
        options.add(new BookingMeetingPointOption(
                "outpatient",
                context.getString(R.string.booking_location_point_outpatient_title, departmentName),
                context.getString(R.string.booking_location_point_outpatient_body),
                context.getString(R.string.booking_location_point_outpatient_place, departmentName),
                0.74f,
                0.36f
        ));
        options.add(new BookingMeetingPointOption(
                "parking",
                context.getString(R.string.booking_location_point_parking_title),
                context.getString(R.string.booking_location_point_parking_body),
                context.getString(R.string.booking_location_point_parking_place, hospitalName),
                0.2f,
                0.82f
        ));
        options.add(new BookingMeetingPointOption(
                "pharmacy",
                context.getString(R.string.booking_location_point_pharmacy_title),
                context.getString(R.string.booking_location_point_pharmacy_body),
                context.getString(R.string.booking_location_point_pharmacy_place, hospitalName),
                0.78f,
                0.76f
        ));
        return options;
    }
}
