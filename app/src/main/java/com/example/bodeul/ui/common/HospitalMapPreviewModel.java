package com.example.bodeul.ui.common;

import com.example.bodeul.domain.model.BookingMeetingPointOption;

import java.util.List;

/**
 * 병원 내 안내 지점 미리보기용 선택 상태를 담는다.
 */
public final class HospitalMapPreviewModel {
    private final List<BookingMeetingPointOption> pointOptions;
    private final String selectedPointId;
    private final String highlightedPointId;

    public HospitalMapPreviewModel(
            List<BookingMeetingPointOption> pointOptions,
            String selectedPointId,
            String highlightedPointId
    ) {
        this.pointOptions = pointOptions;
        this.selectedPointId = selectedPointId;
        this.highlightedPointId = highlightedPointId;
    }

    public List<BookingMeetingPointOption> getPointOptions() {
        return pointOptions;
    }

    public String getSelectedPointId() {
        return selectedPointId;
    }

    public String getHighlightedPointId() {
        return highlightedPointId;
    }
}
