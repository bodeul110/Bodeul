package com.example.bodeul.ui.auth;

import android.view.View;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge 환경에서 인증 화면의 스크롤 내용과 고정 하단 동작을 시스템 영역 밖에 둔다.
 */
final class AuthScreenInsets {
    private AuthScreenInsets() {
    }

    static void apply(ScrollView content, View bottomAction) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop();
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int actionLeft = bottomAction.getPaddingLeft();
        int actionTop = bottomAction.getPaddingTop();
        int actionRight = bottomAction.getPaddingRight();
        int actionBottom = bottomAction.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    contentLeft + systemInsets.left,
                    contentTop + systemInsets.top,
                    contentRight + systemInsets.right,
                    contentBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomAction, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    actionLeft + systemInsets.left,
                    actionTop,
                    actionRight + systemInsets.right,
                    actionBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(bottomAction);
    }
}
