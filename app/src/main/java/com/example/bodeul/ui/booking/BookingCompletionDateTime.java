package com.example.bodeul.ui.booking;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * API의 예약 일시 문자열을 완료 화면용 날짜와 시간으로 나눈다.
 */
final class BookingCompletionDateTime {
    private static final String API_PATTERN = "yyyy-MM-dd HH:mm";

    private BookingCompletionDateTime() {
    }

    static DisplayValue format(String appointmentAt, Locale locale) {
        String normalized = appointmentAt == null ? "" : appointmentAt.trim();
        if (normalized.isEmpty()) {
            return new DisplayValue("", "");
        }

        SimpleDateFormat parser = new SimpleDateFormat(API_PATTERN, Locale.ROOT);
        parser.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = parser.parse(normalized, position);
        if (parsed == null || position.getIndex() != normalized.length()) {
            // 예전 데이터 형식은 감추지 않고 원문을 날짜 위치에 그대로 보여준다.
            return new DisplayValue(normalized, "");
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy. MM. dd (E)", locale);
        SimpleDateFormat timeFormatter = new SimpleDateFormat("a hh:mm", locale);
        return new DisplayValue(
                dateFormatter.format(parsed),
                timeFormatter.format(parsed)
        );
    }

    static final class DisplayValue {
        private final String date;
        private final String time;

        DisplayValue(String date, String time) {
            this.date = date;
            this.time = time;
        }

        String getDate() {
            return date;
        }

        String getTime() {
            return time;
        }
    }
}
