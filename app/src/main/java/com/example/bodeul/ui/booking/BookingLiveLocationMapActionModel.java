package com.example.bodeul.ui.booking;

import androidx.annotation.Nullable;

/**
 * 실시간 위치 확인 화면에서 외부 지도 fallback 액션 한 건을 표현한다.
 */
public final class BookingLiveLocationMapActionModel {
    private final String title;
    private final String body;
    private final String buttonLabel;
    private final String queryText;
    @Nullable
    private final String directUrl;

    public BookingLiveLocationMapActionModel(
            String title,
            String body,
            String buttonLabel,
            String queryText,
            @Nullable String directUrl
    ) {
        this.title = title;
        this.body = body;
        this.buttonLabel = buttonLabel;
        this.queryText = queryText;
        this.directUrl = directUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    public String getQueryText() {
        return queryText;
    }

    @Nullable
    public String getDirectUrl() {
        return directUrl;
    }
}
