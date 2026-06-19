package com.example.bodeul.ui.auth;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;

/**
 * 인증 화면 상단 요약 카드에 문구를 반영한다.
 */
public final class AuthSummaryCardBinder {
    private final TextView badgeView;
    private final TextView titleView;
    private final TextView bodyView;

    public AuthSummaryCardBinder(View root) {
        badgeView = root.findViewById(R.id.textAuthSummaryBadge);
        titleView = root.findViewById(R.id.textAuthSummaryTitle);
        bodyView = root.findViewById(R.id.textAuthSummaryBody);
    }

    public void render(AuthSummaryCardModel model) {
        badgeView.setText(model.getBadgeText());
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
    }
}
