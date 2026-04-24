package com.example.bodeul.ui.manager;

/**
 * 현재 집중해서 보여줄 단계 카드 모델이다.
 */
public final class ManagerGuideFocusModel {
    private final String badge;
    private final String title;
    private final String body;
    private final String previewLabel;
    private final String previewBody;
    private final int previewBackgroundResId;

    public ManagerGuideFocusModel(
            String badge,
            String title,
            String body,
            String previewLabel,
            String previewBody,
            int previewBackgroundResId
    ) {
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.previewLabel = previewLabel;
        this.previewBody = previewBody;
        this.previewBackgroundResId = previewBackgroundResId;
    }

    public String getBadge() {
        return badge;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getPreviewLabel() {
        return previewLabel;
    }

    public String getPreviewBody() {
        return previewBody;
    }

    public int getPreviewBackgroundResId() {
        return previewBackgroundResId;
    }
}
