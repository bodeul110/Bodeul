package com.example.bodeul.domain.model;

/**
 * 병원 동행 가이드에서 한 단계의 제목과 설명을 표현한다.
 */
public class GuideStep {
    // 서버와 Android가 단계의 업무 의미를 동일하게 식별하는 안정적인 코드다.
    private final String code;

    // 화면 정렬과 진행 판별에 사용하는 단계 순서다.
    private final int order;

    // 매니저 화면에 노출할 단계 이름과 설명이다.
    private final String title;
    private final String description;

    // 영상 원본 주소를 앱에 노출하지 않고 서버 자산 계약만 식별한다.
    private final String videoAssetId;
    private final String videoAssetVersion;
    private final String videoFallbackText;

    public GuideStep(int order, String title, String description) {
        this("", order, title, description, "", "", "");
    }

    public GuideStep(String code, int order, String title, String description) {
        this(code, order, title, description, "", "", "");
    }

    public GuideStep(
            String code,
            int order,
            String title,
            String description,
            String videoAssetId,
            String videoAssetVersion,
            String videoFallbackText
    ) {
        this.code = code == null ? "" : code.trim();
        this.order = order;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.videoAssetId = videoAssetId == null ? "" : videoAssetId.trim();
        this.videoAssetVersion = videoAssetVersion == null ? "" : videoAssetVersion.trim();
        this.videoFallbackText = videoFallbackText == null ? "" : videoFallbackText.trim();
    }

    public String getCode() {
        return code;
    }

    public int getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getVideoAssetId() {
        return videoAssetId;
    }

    public String getVideoAssetVersion() {
        return videoAssetVersion;
    }

    public String getVideoFallbackText() {
        return videoFallbackText;
    }

    public boolean hasVideoAssetMetadata() {
        return !videoAssetId.isEmpty() && !videoAssetVersion.isEmpty();
    }

    public boolean hasCompleteVideoGuidanceMetadata() {
        return hasVideoAssetMetadata() && !videoFallbackText.isEmpty();
    }
}
