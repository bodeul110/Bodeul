package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;

import org.junit.Test;

public class AppointmentFollowUpVisibilityPolicyTest {
    @Test
    public void legacySupportOnlyRecord_isNotVisibleAsMvpFollowUpAction() {
        AppointmentFollowUpRecord record = new AppointmentFollowUpRecord(
                "request-id",
                null,
                0L,
                null,
                "",
                0L,
                AppointmentFollowUpSupportEscalationStatus.DIALED_119,
                100L
        );

        assertTrue(record.hasSavedSupportEscalation());
        assertFalse(AppointmentFollowUpVisibilityPolicy.hasVisibleAction(record));
    }

    @Test
    public void reviewOrSettlement_remainsVisible() {
        AppointmentFollowUpRecord review = new AppointmentFollowUpRecord(
                "review-id",
                AppointmentFollowUpReviewRating.GOOD,
                100L,
                null,
                "",
                0L,
                null,
                0L
        );
        AppointmentFollowUpRecord settlement = new AppointmentFollowUpRecord(
                "settlement-id",
                null,
                0L,
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                "확인 완료",
                100L,
                null,
                0L
        );

        assertTrue(AppointmentFollowUpVisibilityPolicy.hasVisibleAction(review));
        assertTrue(AppointmentFollowUpVisibilityPolicy.hasVisibleAction(settlement));
    }
}
