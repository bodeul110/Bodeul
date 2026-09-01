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
    private final boolean videoGuidanceVisible;
    private final String videoGuidanceTitle;
    private final String videoGuidanceBody;

    public ManagerGuideFocusModel(
            String badge,
            String title,
            String body,
            String previewLabel,
            String previewBody,
            int previewBackgroundResId,
            boolean videoGuidanceVisible,
            String videoGuidanceTitle,
            String videoGuidanceBody
    ) {
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.previewLabel = previewLabel;
        this.previewBody = previewBody;
        this.previewBackgroundResId = previewBackgroundResId;
        this.videoGuidanceVisible = videoGuidanceVisible;
        this.videoGuidanceTitle = videoGuidanceTitle;
        this.videoGuidanceBody = videoGuidanceBody;
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

    public boolean isVideoGuidanceVisible() {
        return videoGuidanceVisible;
    }

    public String getVideoGuidanceTitle() {
        return videoGuidanceTitle;
    }

    public String getVideoGuidanceBody() {
        return videoGuidanceBody;
    }
}
