package com.example.bodeul.ui.auth;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;

/**
 * 역할 선택 화면에서 현재 선택한 경로의 다음 단계를 요약한다.
 */
public final class RoleSelectionSummaryFormatter {
    private final Context context;

    public RoleSelectionSummaryFormatter(Context context) {
        this.context = context;
    }

    public AuthSummaryCardModel format(UserRole roleHint) {
        if (roleHint == UserRole.MANAGER) {
            return new AuthSummaryCardModel(
                    context.getString(R.string.role_select_summary_badge_manager),
                    context.getString(R.string.role_select_summary_title_manager),
                    context.getString(R.string.role_select_summary_body_manager)
            );
        }

        return new AuthSummaryCardModel(
                context.getString(R.string.role_select_summary_badge_patient),
                context.getString(R.string.role_select_summary_title_patient),
                context.getString(R.string.role_select_summary_body_patient)
        );
    }
}
