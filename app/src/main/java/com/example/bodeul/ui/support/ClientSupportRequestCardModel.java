package com.example.bodeul.ui.support;

/**
 * 사용자 문의 카드 한 장의 표시 상태를 담는다.
 */
public final class ClientSupportRequestCardModel {
    private final String categoryText;
    private final String statusText;
    private final int statusBackgroundColorResId;
    private final int statusTextColorResId;
    private final String titleText;
    private final String bodyText;
    private final String timestampText;
    private final boolean hasResponse;
    private final String responseBodyText;
    private final String responseMetaText;

    public ClientSupportRequestCardModel(
            String categoryText,
            String statusText,
            int statusBackgroundColorResId,
            int statusTextColorResId,
            String titleText,
            String bodyText,
            String timestampText,
            boolean hasResponse,
            String responseBodyText,
            String responseMetaText
    ) {
        this.categoryText = categoryText;
        this.statusText = statusText;
        this.statusBackgroundColorResId = statusBackgroundColorResId;
        this.statusTextColorResId = statusTextColorResId;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.timestampText = timestampText;
        this.hasResponse = hasResponse;
        this.responseBodyText = responseBodyText;
        this.responseMetaText = responseMetaText;
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

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getTimestampText() {
        return timestampText;
    }

    public boolean hasResponse() {
        return hasResponse;
    }

    public String getResponseBodyText() {
        return responseBodyText;
    }

    public String getResponseMetaText() {
        return responseMetaText;
    }
}
