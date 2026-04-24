package com.example.bodeul.ui.common;

import java.util.Collections;
import java.util.List;

/**
 * 예약 진행 로드맵의 현재 포커스와 단계 목록을 함께 전달한다.
 */
public final class AppointmentProgressOverviewModel {
    private final String badgeText;
    private final String titleText;
    private final String bodyText;
    private final String guideTitleText;
    private final String guideBodyText;
    private final List<AppointmentProgressStageModel> stages;

    public AppointmentProgressOverviewModel(
            String badgeText,
            String titleText,
            String bodyText,
            String guideTitleText,
            String guideBodyText,
            List<AppointmentProgressStageModel> stages
    ) {
        this.badgeText = badgeText;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.guideTitleText = guideTitleText;
        this.guideBodyText = guideBodyText;
        this.stages = Collections.unmodifiableList(stages);
    }

    public String getBadgeText() {
        return badgeText;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getGuideTitleText() {
        return guideTitleText;
    }

    public String getGuideBodyText() {
        return guideBodyText;
    }

    public List<AppointmentProgressStageModel> getStages() {
        return stages;
    }
}
