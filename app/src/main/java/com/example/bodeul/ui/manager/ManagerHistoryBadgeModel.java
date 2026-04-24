package com.example.bodeul.ui.manager;

/**
 * 과거 동행 이력 카드의 상단 배지 하나를 표현한다.
 */
public final class ManagerHistoryBadgeModel {
    private final String text;
    private final ManagerHistoryBadgeTone tone;

    public ManagerHistoryBadgeModel(String text, ManagerHistoryBadgeTone tone) {
        this.text = text == null ? "" : text;
        this.tone = tone == null ? ManagerHistoryBadgeTone.PRIMARY : tone;
    }

    public String getText() {
        return text;
    }

    public ManagerHistoryBadgeTone getTone() {
        return tone;
    }
}
