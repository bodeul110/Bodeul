package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;

/**
 * 승인된 재생 경로가 없을 때 길안내 영상 영역을 안전한 대체 안내로 렌더링한다.
 */
final class ManagerGuideVideoGuidanceBinder {
    private final View container;
    private final TextView title;
    private final TextView body;

    ManagerGuideVideoGuidanceBinder(View root) {
        container = root.findViewById(R.id.layoutGuideVideoGuidance);
        title = root.findViewById(R.id.textGuideVideoGuidanceTitle);
        body = root.findViewById(R.id.textGuideVideoGuidanceBody);
    }

    void bind(ManagerGuideFocusModel model) {
        container.setVisibility(model.isVideoGuidanceVisible() ? View.VISIBLE : View.GONE);
        if (!model.isVideoGuidanceVisible()) {
            title.setText("");
            body.setText("");
            return;
        }
        title.setText(model.getVideoGuidanceTitle());
        body.setText(model.getVideoGuidanceBody());
    }
}
