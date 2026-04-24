package com.example.bodeul.ui.manager;

/**
 * 단계 레일에 렌더링할 한 줄짜리 진행 상태 모델이다.
 */
public final class ManagerGuideStageModel {
    private final int order;
    private final String title;
    private final String description;
    private final String stateLabel;
    private final ManagerGuideStageState state;

    public ManagerGuideStageModel(
            int order,
            String title,
            String description,
            String stateLabel,
            ManagerGuideStageState state
    ) {
        this.order = order;
        this.title = title;
        this.description = description;
        this.stateLabel = stateLabel;
        this.state = state;
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

    public String getStateLabel() {
        return stateLabel;
    }

    public ManagerGuideStageState getState() {
        return state;
    }
}
