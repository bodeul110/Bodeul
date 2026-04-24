package com.example.bodeul.ui.admin;

/**
 * 관리자 문의 응답 카드 한 장의 표시 상태를 담는다.
 */
public final class AdminSupportInquiryCardModel {
    private final String inquiryId;
    private final String categoryText;
    private final String statusText;
    private final int statusBackgroundColorResId;
    private final int statusTextColorResId;
    private final String managerText;
    private final String titleText;
    private final String bodyText;
    private final String timestampText;
    private final boolean showResponse;
    private final String responseText;
    private final String responseMetaText;
    private final String actionButtonText;

    public AdminSupportInquiryCardModel(
            String inquiryId,
            String categoryText,
            String statusText,
            int statusBackgroundColorResId,
            int statusTextColorResId,
            String managerText,
            String titleText,
            String bodyText,
            String timestampText,
            boolean showResponse,
            String responseText,
            String responseMetaText,
            String actionButtonText
    ) {
        this.inquiryId = inquiryId;
        this.categoryText = categoryText;
        this.statusText = statusText;
        this.statusBackgroundColorResId = statusBackgroundColorResId;
        this.statusTextColorResId = statusTextColorResId;
        this.managerText = managerText;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.timestampText = timestampText;
        this.showResponse = showResponse;
        this.responseText = responseText;
        this.responseMetaText = responseMetaText;
        this.actionButtonText = actionButtonText;
    }

    public String getInquiryId() {
        return inquiryId;
    }

    public String getCategoryText() {
        return categoryText;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getStatusBackgroundColorResId() {
        return statusBackgroundColorResId;
    }

    public int getStatusTextColorResId() {
        return statusTextColorResId;
    }

    public String getManagerText() {
        return managerText;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getTimestampText() {
        return timestampText;
    }

    public boolean isShowResponse() {
        return showResponse;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getResponseMetaText() {
        return responseMetaText;
    }

    public String getActionButtonText() {
        return actionButtonText;
    }
}
