package com.example.bodeul.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;

/**
 * 자동 위치 알림 단계를 사용자 화면에서 같은 기준으로 출력한다.
 */
public final class CompanionLocationAlertDisplayHelper {
    private CompanionLocationAlertDisplayHelper() {
    }

    public static String buildAlertSummary(Context context, @Nullable CompanionSession session) {
        if (session == null) {
            return context.getString(R.string.companion_location_alert_stage_none);
        }
        String stageLabel = toStageLabel(context, session.getLocationAlertStage());
        if (session.getLocationAlertSentAtMillis() <= 0L
                || session.getLocationAlertStage() == CompanionLocationAlertStage.NONE) {
            return stageLabel;
        }
        return context.getString(
                R.string.companion_location_alert_value_with_time,
                stageLabel,
                CompanionLocationDisplayHelper.formatSharedLocationTime(
                        session.getLocationAlertSentAtMillis()
                )
        );
    }

    public static String toStageLabel(
            Context context,
            @Nullable CompanionLocationAlertStage stage
    ) {
        if (stage == CompanionLocationAlertStage.HOSPITAL_NEAR) {
            return context.getString(R.string.companion_location_alert_stage_hospital);
        }
        if (stage == CompanionLocationAlertStage.PHARMACY_NEAR) {
            return context.getString(R.string.companion_location_alert_stage_pharmacy);
        }
        return context.getString(R.string.companion_location_alert_stage_none);
    }
}
