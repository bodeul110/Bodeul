package com.example.bodeul.ui.booking;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentFollowUpRecord;

/**
 * MVP에서 사용자와 매니저에게 노출하는 완료 예약 후속 항목을 제한한다.
 */
public final class AppointmentFollowUpVisibilityPolicy {
    private AppointmentFollowUpVisibilityPolicy() {
    }

    public static boolean hasVisibleAction(@Nullable AppointmentFollowUpRecord record) {
        return record != null && (record.hasSavedReview() || record.hasSavedSettlement());
    }
}
