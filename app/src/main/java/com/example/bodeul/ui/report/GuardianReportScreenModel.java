package com.example.bodeul.ui.report;

import java.util.List;

/**
 * 보호자 진행 화면 전체를 렌더링하기 위한 화면 모델이다.
 */
public final class GuardianReportScreenModel {
    private final String modeText;
    private final String greetingText;
    private final String summaryText;
    private final GuardianReportHighlightModel highlightModel;
    private final List<GuardianReportEntryCardModel> entryCards;

    public GuardianReportScreenModel(
            String modeText,
            String greetingText,
            String summaryText,
            GuardianReportHighlightModel highlightModel,
            List<GuardianReportEntryCardModel> entryCards
    ) {
        this.modeText = modeText;
        this.greetingText = greetingText;
        this.summaryText = summaryText;
        this.highlightModel = highlightModel;
        this.entryCards = entryCards;
    }

    public String getModeText() {
        return modeText;
    }

    public String getGreetingText() {
        return greetingText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public GuardianReportHighlightModel getHighlightModel() {
        return highlightModel;
    }

    public List<GuardianReportEntryCardModel> getEntryCards() {
        return entryCards;
    }

    public boolean hasEntries() {
        return !entryCards.isEmpty();
    }
}
