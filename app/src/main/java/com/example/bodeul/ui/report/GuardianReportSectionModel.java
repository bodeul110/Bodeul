package com.example.bodeul.ui.report;

import java.util.Collections;
import java.util.List;

/**
 * 보호자 리포트 카드 안에서 세부 리포트 묶음을 제목과 항목 목록으로 전달한다.
 */
public final class GuardianReportSectionModel {
    private final String titleText;
    private final List<GuardianReportLineItem> lines;

    public GuardianReportSectionModel(String titleText, List<GuardianReportLineItem> lines) {
        this.titleText = titleText;
        this.lines = Collections.unmodifiableList(lines);
    }

    public String getTitleText() {
        return titleText;
    }

    public List<GuardianReportLineItem> getLines() {
        return lines;
    }
}
