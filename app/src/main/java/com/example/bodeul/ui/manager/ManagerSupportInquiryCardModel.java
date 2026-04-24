package com.example.bodeul.ui.manager;

/**
 * 문의 카드 목록에서 한 항목을 그릴 때 사용하는 표현 모델이다.
 */
public final class ManagerSupportInquiryCardModel {
    private final String categoryText;
    private final String statusText;
    private final int statusBackgroundColorRes;
    private final int statusTextColorRes;
    private final String titleText;
    private final String bodyText;
    private final String timestampText;
    private final boolean showResponse;
    private final String responseBodyText;
    private final String responseMetaText;

    public ManagerSupportInquiryCardModel(
            String categoryText,
            String statusText,
            int statusBackgroundColorRes,
            int statusTextColorRes,
            String titleText,
            String bodyText,
            String timestampText,
            boolean showResponse,
            String responseBodyText,
            String responseMetaText
    ) {
        this.categoryText = categoryText;
        this.statusText = statusText;
        this.statusBackgroundColorRes = statusBackgroundColorRes;
        this.statusTextColorRes = statusTextColorRes;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.timestampText = timestampText;
        this.showResponse = showResponse;
        this.responseBodyText = responseBodyText;
        this.responseMetaText = responseMetaText;
    }

    public String getCategoryText() {
        return categoryText;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getStatusBackgroundColorRes() {
        return statusBackgroundColorRes;
    }

    public int getStatusTextColorRes() {
        return statusTextColorRes;
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

    public String getResponseBodyText() {
        return responseBodyText;
    }

    public String getResponseMetaText() {
        return responseMetaText;
    }
}
