package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingMeetingPointOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 예약/진행 화면에서 공통으로 쓰는 병원 내 안내 지점 목록과 선택 규칙을 제공한다.
 */
public final class BookingMeetingPointCatalog {
    public static final String POINT_ID_MAIN = "main";
    public static final String POINT_ID_OUTPATIENT = "outpatient";
    public static final String POINT_ID_PARKING = "parking";
    public static final String POINT_ID_PHARMACY = "pharmacy";

    private BookingMeetingPointCatalog() {
    }

    public static List<BookingMeetingPointOption> createPointOptions(
            Context context,
            String hospitalName,
            String departmentName
    ) {
        Context appContext = context.getApplicationContext();
        List<BookingMeetingPointOption> options = new ArrayList<>();
        options.add(new BookingMeetingPointOption(
                POINT_ID_MAIN,
                appContext.getString(R.string.booking_location_point_main_title, hospitalName),
                appContext.getString(R.string.booking_location_point_main_body),
                appContext.getString(R.string.booking_location_point_main_place, hospitalName),
                0.28f,
                0.58f
        ));
        options.add(new BookingMeetingPointOption(
                POINT_ID_OUTPATIENT,
                appContext.getString(R.string.booking_location_point_outpatient_title, departmentName),
                appContext.getString(R.string.booking_location_point_outpatient_body),
                appContext.getString(R.string.booking_location_point_outpatient_place, departmentName),
                0.74f,
                0.36f
        ));
        options.add(new BookingMeetingPointOption(
                POINT_ID_PARKING,
                appContext.getString(R.string.booking_location_point_parking_title),
                appContext.getString(R.string.booking_location_point_parking_body),
                appContext.getString(R.string.booking_location_point_parking_place, hospitalName),
                0.20f,
                0.82f
        ));
        options.add(new BookingMeetingPointOption(
                POINT_ID_PHARMACY,
                appContext.getString(R.string.booking_location_point_pharmacy_title),
                appContext.getString(R.string.booking_location_point_pharmacy_body),
                appContext.getString(R.string.booking_location_point_pharmacy_place, hospitalName),
                0.78f,
                0.76f
        ));
        return options;
    }

    public static String resolveSelectedPointId(
            List<BookingMeetingPointOption> pointOptions,
            String meetingPlace
    ) {
        if (pointOptions == null || pointOptions.isEmpty()) {
            return "";
        }

        String normalizedMeetingPlace = normalize(meetingPlace);
        if (TextUtils.isEmpty(normalizedMeetingPlace)) {
            return pointOptions.get(0).getId();
        }

        for (BookingMeetingPointOption option : pointOptions) {
            String normalizedOptionPlace = normalize(option.getMeetingPlace());
            if (!TextUtils.isEmpty(normalizedOptionPlace)
                    && (normalizedMeetingPlace.contains(normalizedOptionPlace)
                    || normalizedOptionPlace.contains(normalizedMeetingPlace))) {
                return option.getId();
            }
        }

        if (normalizedMeetingPlace.contains("약국")) {
            return POINT_ID_PHARMACY;
        }
        if (normalizedMeetingPlace.contains("주차")) {
            return POINT_ID_PARKING;
        }
        if (normalizedMeetingPlace.contains("외래")
                || normalizedMeetingPlace.contains("진료")
                || normalizedMeetingPlace.contains("접수")) {
            return POINT_ID_OUTPATIENT;
        }
        if (normalizedMeetingPlace.contains("정문")
                || normalizedMeetingPlace.contains("로비")
                || normalizedMeetingPlace.contains("안내")) {
            return POINT_ID_MAIN;
        }

        return pointOptions.get(0).getId();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.KOREA)
                .replace(" ", "");
    }
}
