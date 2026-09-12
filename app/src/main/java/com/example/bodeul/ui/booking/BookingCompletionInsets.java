package com.example.bodeul.ui.booking;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 예약 완료 화면을 상태 표시줄과 내비게이션 영역 안쪽에 배치한다.
 */
final class BookingCompletionInsets {
    private BookingCompletionInsets() {
    }

    static void apply(View root) {
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    initialLeft + systemInsets.left,
                    initialTop + systemInsets.top,
                    initialRight + systemInsets.right,
                    initialBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
