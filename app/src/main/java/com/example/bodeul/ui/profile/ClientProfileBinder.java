package com.example.bodeul.ui.profile;

import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * 내 정보 화면 모델을 고정된 뷰에 표시한다.
 */
public final class ClientProfileBinder {
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView heroTitleView;
    private final TextView heroBodyView;
    private final TextView roleView;
    private final TextView nameView;
    private final TextView emailView;
    private final TextView phoneView;

    public ClientProfileBinder(
            TextView titleView,
            TextView subtitleView,
            TextView heroTitleView,
            TextView heroBodyView,
            TextView roleView,
            TextView nameView,
            TextView emailView,
            TextView phoneView
    ) {
        this.titleView = titleView;
        this.subtitleView = subtitleView;
        this.heroTitleView = heroTitleView;
        this.heroBodyView = heroBodyView;
        this.roleView = roleView;
        this.nameView = nameView;
        this.emailView = emailView;
        this.phoneView = phoneView;
    }

    public void bind(@NonNull ClientProfileScreenModel model) {
        titleView.setText(model.getTitle());
        subtitleView.setText(model.getSubtitle());
        heroTitleView.setText(model.getHeroTitle());
        heroBodyView.setText(model.getHeroBody());
        roleView.setText(model.getRole());
        nameView.setText(model.getName());
        emailView.setText(model.getEmail());
        phoneView.setText(model.getPhone());
    }
}
