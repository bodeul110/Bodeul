package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingMeetingPointOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 위치 선택 화면에 필요한 안내 문구와 후보 목록을 담는다.
 */
public final class BookingLocationSelectorScreenModel {
    private final String badge;
    private final String title;
    private final String body;
    private final String helper;
    private final String selectedPointId;
    private final List<BookingMeetingPointOption> pointOptions;

    public BookingLocationSelectorScreenModel(
            String badge,
            String title,
            String body,
            String helper,
            String selectedPointId,
            List<BookingMeetingPointOption> pointOptions
    ) {
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.helper = helper;
        this.selectedPointId = selectedPointId == null ? "" : selectedPointId.trim();
        this.pointOptions = Collections.unmodifiableList(new ArrayList<>(pointOptions));
    }

    public String getBadge() {
        return badge;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getHelper() {
        return helper;
    }

    public String getSelectedPointId() {
        return selectedPointId;
    }

    public List<BookingMeetingPointOption> getPointOptions() {
        return pointOptions;
    }
}
