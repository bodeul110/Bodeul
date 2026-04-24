package com.example.bodeul.domain.model;

/**
 * 지도 기반 만남 위치 선택 결과를 예약 폼에 전달한다.
 */
public final class BookingMeetingLocationSelection {
    private final String pointId;
    private final String meetingPlace;

    public BookingMeetingLocationSelection(String pointId, String meetingPlace) {
        this.pointId = normalize(pointId);
        this.meetingPlace = normalize(meetingPlace);
    }

    public String getPointId() {
        return pointId;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public boolean isComplete() {
        return !meetingPlace.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
