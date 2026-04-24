package com.example.bodeul.ui.admin;

/**
 * 관리자 병원 가이드 카드 표현 모델이다.
 */
public final class AdminGuideCardModel {
    private final String guideId;
    private final String titleText;
    private final String countText;
    private final String previewText;

    public AdminGuideCardModel(
            String guideId,
            String titleText,
            String countText,
            String previewText
    ) {
        this.guideId = guideId;
        this.titleText = titleText;
        this.countText = countText;
        this.previewText = previewText;
    }

    public String getGuideId() {
        return guideId;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getCountText() {
        return countText;
    }

    public String getPreviewText() {
        return previewText;
    }
}
