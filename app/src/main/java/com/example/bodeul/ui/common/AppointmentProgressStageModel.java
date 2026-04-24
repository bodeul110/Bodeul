package com.example.bodeul.ui.common;

/**
 * 예약 진행 로드맵에 표시할 단계 카드 한 장의 데이터를 담는다.
 */
public final class AppointmentProgressStageModel {
    private final int order;
    private final String titleText;
    private final String bodyText;
    private final String stateText;
    private final AppointmentProgressStageState state;

    public AppointmentProgressStageModel(
            int order,
            String titleText,
            String bodyText,
            String stateText,
            AppointmentProgressStageState state
    ) {
        this.order = order;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.stateText = stateText;
        this.state = state;
    }

    public int getOrder() {
        return order;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getStateText() {
        return stateText;
    }

    public AppointmentProgressStageState getState() {
        return state;
    }
}
