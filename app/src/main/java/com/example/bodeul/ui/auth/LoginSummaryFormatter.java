package com.example.bodeul.ui.auth;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;

/**
 * 로그인 화면의 현재 경로와 다음 단계를 짧게 요약한다.
 */
public final class LoginSummaryFormatter {
    private final Context context;

    public LoginSummaryFormatter(Context context) {
        this.context = context;
    }

    public AuthSummaryCardModel format(
            UserRole roleHint,
            UserRole selectedRole,
            boolean registerMode
    ) {
        if (roleHint == UserRole.ADMIN) {
            return new AuthSummaryCardModel(
                    context.getString(R.string.login_summary_badge_admin),
                    context.getString(R.string.login_summary_title_admin),
                    context.getString(R.string.login_summary_body_admin)
            );
        }

        if (roleHint == UserRole.MANAGER) {
            return new AuthSummaryCardModel(
                    context.getString(R.string.login_summary_badge_manager),
                    context.getString(
                            registerMode
                                    ? R.string.login_summary_title_manager_register
                                    : R.string.login_summary_title_manager_login
                    ),
                    context.getString(
                            registerMode
                                    ? R.string.login_summary_body_manager_register
                                    : R.string.login_summary_body_manager_login
                    )
            );
        }

        if (selectedRole == UserRole.GUARDIAN) {
            return new AuthSummaryCardModel(
                    context.getString(R.string.login_summary_badge_guardian),
                    context.getString(
                            registerMode
                                    ? R.string.login_summary_title_guardian_register
                                    : R.string.login_summary_title_guardian_login
                    ),
                    context.getString(
                            registerMode
                                    ? R.string.login_summary_body_guardian_register
                                    : R.string.login_summary_body_guardian_login
                    )
            );
        }

        return new AuthSummaryCardModel(
                context.getString(R.string.login_summary_badge_patient),
                context.getString(
                        registerMode
                                ? R.string.login_summary_title_patient_register
                                : R.string.login_summary_title_patient_login
                ),
                context.getString(
                        registerMode
                                ? R.string.login_summary_body_patient_register
                                : R.string.login_summary_body_patient_login
                )
        );
    }
}
