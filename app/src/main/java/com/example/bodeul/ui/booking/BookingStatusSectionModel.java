package com.example.bodeul.ui.booking;

import java.util.Collections;
import java.util.List;

/**
 * 예약 상세 화면에서 한 카드 안의 정보 묶음을 제목과 항목 목록으로 전달한다.
 */
public final class BookingStatusSectionModel {
    private final String titleText;
    private final List<BookingStatusLineItem> lines;

    public BookingStatusSectionModel(String titleText, List<BookingStatusLineItem> lines) {
        this.titleText = titleText;
        this.lines = Collections.unmodifiableList(lines);
    }

    public String getTitleText() {
        return titleText;
    }

    public List<BookingStatusLineItem> getLines() {
        return lines;
    }
}
