package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class BookingAppointmentDateTimeTest {
    @Test
    public void parseAndFormat_preserveSeoulAppointmentValue() {
        Calendar parsed = BookingAppointmentDateTime.parse("2026-09-14 13:30");

        assertNotNull(parsed);
        assertEquals(2026, parsed.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, parsed.get(Calendar.MONTH));
        assertEquals(14, parsed.get(Calendar.DAY_OF_MONTH));
        assertEquals(13, parsed.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, parsed.get(Calendar.MINUTE));
        assertEquals("2026-09-14 13:30", BookingAppointmentDateTime.format(parsed, 13, 30));
    }

    @Test
    public void parse_rejectsInvalidDateAndFormat() {
        assertNull(BookingAppointmentDateTime.parse("2026-02-30 10:00"));
        assertNull(BookingAppointmentDateTime.parse("2026/09/14 10:00"));
        assertNull(BookingAppointmentDateTime.parse("2026-09-14 10:00 추가"));
        assertNull(BookingAppointmentDateTime.parse(""));
    }

    @Test
    public void resolveInitialSelection_movesDefaultToTomorrowWhenTenOclockPassed() {
        Calendar now = BookingAppointmentDateTime.newCalendar();
        now.set(2026, Calendar.SEPTEMBER, 12, 11, 0, 0);
        now.set(Calendar.MILLISECOND, 0);

        Calendar initial = BookingAppointmentDateTime.resolveInitialSelection("", now.getTimeInMillis());

        assertEquals(13, initial.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, initial.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, initial.get(Calendar.MINUTE));
    }

    @Test
    public void isBeforeToday_comparesOnlyCalendarDate() {
        Calendar now = BookingAppointmentDateTime.newCalendar();
        now.set(2026, Calendar.SEPTEMBER, 12, 18, 0, 0);
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        Calendar todayMorning = (Calendar) now.clone();
        todayMorning.set(Calendar.HOUR_OF_DAY, 8);

        assertTrue(BookingAppointmentDateTime.isBeforeToday(yesterday, now.getTimeInMillis()));
        assertFalse(BookingAppointmentDateTime.isBeforeToday(todayMorning, now.getTimeInMillis()));
    }
}
