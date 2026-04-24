package com.example.bodeul.ui.report;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentStatus;

import java.util.List;

/**
 * 보호자 요청별 진행 카드에 필요한 전체 정보를 묶는다.
 */
public final class GuardianReportEntryCardModel {
    @Nullable
    private final String requestId;
    private final AppointmentStatus status;
    private final String titleText;
    private final String heroBodyText;
    private final String liveSectionTitleText;
    private final List<GuardianReportLineItem> liveLines;
    private final String reportSectionTitleText;
    private final List<GuardianReportLineItem> reportLines;
    @Nullable
    private final String pendingReportText;
    private final String actionLabelText;

    public GuardianReportEntryCardModel(
            @Nullable String requestId,
            AppointmentStatus status,
            String titleText,
            String heroBodyText,
            String liveSectionTitleText,
            List<GuardianReportLineItem> liveLines,
            String reportSectionTitleText,
            List<GuardianReportLineItem> reportLines,
            @Nullable String pendingReportText,
            String actionLabelText
    ) {
        this.requestId = requestId;
        this.status = status;
        this.titleText = titleText;
        this.heroBodyText = heroBodyText;
        this.liveSectionTitleText = liveSectionTitleText;
        this.liveLines = liveLines;
        this.reportSectionTitleText = reportSectionTitleText;
        this.reportLines = reportLines;
        this.pendingReportText = pendingReportText;
        this.actionLabelText = actionLabelText;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getLiveSectionTitleText() {
        return liveSectionTitleText;
    }

    public List<GuardianReportLineItem> getLiveLines() {
        return liveLines;
    }

    public String getReportSectionTitleText() {
        return reportSectionTitleText;
    }

    public List<GuardianReportLineItem> getReportLines() {
        return reportLines;
    }

    @Nullable
    public String getPendingReportText() {
        return pendingReportText;
    }

    public String getActionLabelText() {
        return actionLabelText;
    }
}
