package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 매니저 화면의 고정 상단·하단 영역을 상태 표시줄, cutout, 탐색 영역 밖에 둔다.
 */
final class ManagerScreenInsets {
    private ManagerScreenInsets() {
    }

    static void apply(ScrollView content, View topBar, View bottomBar) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop();
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int topLeft = topBar.getPaddingLeft();
        int topTop = topBar.getPaddingTop();
        int topRight = topBar.getPaddingRight();
        int topBottom = topBar.getPaddingBottom();
        int bottomLeft = bottomBar.getPaddingLeft();
        int bottomTop = bottomBar.getPaddingTop();
        int bottomRight = bottomBar.getPaddingRight();
        int bottomBottom = bottomBar.getPaddingBottom();

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
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    topLeft + systemInsets.left,
                    topTop + systemInsets.top,
                    topRight + systemInsets.right,
                    topBottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    bottomLeft + systemInsets.left,
                    bottomTop,
                    bottomRight + systemInsets.right,
                    bottomBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
        ViewCompat.requestApplyInsets(bottomBar);
    }
}
