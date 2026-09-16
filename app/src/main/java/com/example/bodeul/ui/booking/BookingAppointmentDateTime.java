package com.example.bodeul.ui.booking;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 예약 일정 문자열과 서울 시간대 달력 값의 변환 규칙을 한곳에서 관리한다.
 */
final class BookingAppointmentDateTime {
    private static final String APPOINTMENT_AT_FORMAT = "yyyy-MM-dd HH:mm";
    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";
    private static final Pattern APPOINTMENT_AT_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$");

    private BookingAppointmentDateTime() {
    }

    @Nullable
    static Calendar parse(String appointmentAt) {
        if (appointmentAt == null) {
            return null;
        }
        String normalized = appointmentAt.trim();
        if (!APPOINTMENT_AT_PATTERN.matcher(normalized).matches()) {
            return null;
        }

        SimpleDateFormat formatter = formatter();
        try {
            Calendar calendar = newCalendar();
            calendar.setTime(formatter.parse(normalized));
            return calendar;
        } catch (ParseException exception) {
            return null;
        }
    }

    static String format(Calendar date, int hourOfDay, int minute) {
        Calendar appointment = (Calendar) date.clone();
        appointment.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        appointment.set(Calendar.HOUR_OF_DAY, hourOfDay);
        appointment.set(Calendar.MINUTE, minute);
        appointment.set(Calendar.SECOND, 0);
        appointment.set(Calendar.MILLISECOND, 0);
        return formatter().format(appointment.getTime());
    }

    static String format(long appointmentAtMillis) {
        return formatter().format(appointmentAtMillis);
    }

    static Calendar resolveInitialSelection(@Nullable String appointmentAt, long nowMillis) {
        Calendar parsed = parse(appointmentAt);
        if (parsed != null) {
            return parsed;
        }

        Calendar now = newCalendar();
        now.setTimeInMillis(nowMillis);
        Calendar initial = (Calendar) now.clone();
        initial.set(Calendar.HOUR_OF_DAY, 10);
        initial.set(Calendar.MINUTE, 0);
        initial.set(Calendar.SECOND, 0);
        initial.set(Calendar.MILLISECOND, 0);
        if (!initial.after(now)) {
            initial.add(Calendar.DAY_OF_MONTH, 1);
        }
        return initial;
    }

    static boolean isBeforeToday(Calendar candidate, long nowMillis) {
        Calendar candidateDate = (Calendar) candidate.clone();
        normalizeDate(candidateDate);

        Calendar today = newCalendar();
        today.setTimeInMillis(nowMillis);
        normalizeDate(today);
        return candidateDate.before(today);
    }

    static Calendar newCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
    }

    private static void normalizeDate(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static SimpleDateFormat formatter() {
        SimpleDateFormat formatter = new SimpleDateFormat(APPOINTMENT_AT_FORMAT, Locale.KOREA);
        formatter.setLenient(false);
        formatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        return formatter;
    }
}
