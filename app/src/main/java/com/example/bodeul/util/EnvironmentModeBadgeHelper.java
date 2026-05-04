package com.example.bodeul.util;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;

/**
 * 내부 개발용 환경 배지의 노출 정책과 바인딩을 한 곳에서 관리한다.
 */
public final class EnvironmentModeBadgeHelper {
    private EnvironmentModeBadgeHelper() {
    }

    public static String resolveUserFacingLabel(Context context, boolean isFirebaseBacked) {
        // 일반 사용자와 매니저 화면에서는 내부 연동 상태를 기본으로 숨긴다.
        return "";
    }

    public static String resolveAdminLabel(Context context, boolean isFirebaseBacked) {
        return context.getString(isFirebaseBacked
                ? R.string.admin_mode_firebase
                : R.string.admin_mode_demo);
    }

    public static void bind(TextView textView, String labelText) {
        if (TextUtils.isEmpty(labelText)) {
            textView.setText("");
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(labelText);
    }
}
