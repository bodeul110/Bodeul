package com.example.bodeul.ui.admin;

/**
 * 운영 카드 상단 배지 하나를 표현한다.
 */
public final class AdminOperationBadgeModel {
    private final String text;
    private final AdminOperationBadgeTone tone;

    public AdminOperationBadgeModel(String text, AdminOperationBadgeTone tone) {
        this.text = text;
        this.tone = tone == null ? AdminOperationBadgeTone.PRIMARY : tone;
    }

    public String getText() {
        return text;
    }

    public AdminOperationBadgeTone getTone() {
        return tone;
    }
}
