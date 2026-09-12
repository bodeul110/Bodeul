package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class BookingCompletionDateTimeTest {
    @Test
    public void format_morningAppointment_matchesFigmaLabels() {
        BookingCompletionDateTime.DisplayValue result = BookingCompletionDateTime.format(
                "2026-08-20 09:30",
                Locale.KOREA
        );

        assertEquals("2026. 08. 20 (목)", result.getDate());
        assertEquals("오전 09:30", result.getTime());
    }

    @Test
    public void format_afternoonAppointment_usesKoreanPeriod() {
        BookingCompletionDateTime.DisplayValue result = BookingCompletionDateTime.format(
                "2026-10-05 16:00",
                Locale.KOREA
        );

        assertEquals("2026. 10. 05 (월)", result.getDate());
        assertEquals("오후 04:00", result.getTime());
    }

    @Test
    public void format_legacyValue_preservesOriginalText() {
        BookingCompletionDateTime.DisplayValue result = BookingCompletionDateTime.format(
                "일정 확인 필요",
                Locale.KOREA
        );

        assertEquals("일정 확인 필요", result.getDate());
        assertEquals("", result.getTime());
    }

    @Test
    public void format_valueWithUnexpectedSuffix_preservesOriginalText() {
        BookingCompletionDateTime.DisplayValue result = BookingCompletionDateTime.format(
                "2026-08-20 09:30Z",
                Locale.KOREA
        );

        assertEquals("2026-08-20 09:30Z", result.getDate());
        assertEquals("", result.getTime());
    }
}
