package com.example.bodeul.domain.model;

/**
 * 예약 만남 위치 지도에 표시할 선택 지점을 표현한다.
 */
public final class BookingMeetingPointOption {
    private final String id;
    private final String title;
    private final String description;
    private final String meetingPlace;
    private final float relativeX;
    private final float relativeY;

    public BookingMeetingPointOption(
            String id,
            String title,
            String description,
            String meetingPlace,
            float relativeX,
            float relativeY
    ) {
        this.id = normalize(id);
        this.title = normalize(title);
        this.description = normalize(description);
        this.meetingPlace = normalize(meetingPlace);
        this.relativeX = clamp(relativeX);
        this.relativeY = clamp(relativeY);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public float getRelativeX() {
        return relativeX;
    }

    public float getRelativeY() {
        return relativeY;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clamp(float value) {
        return Math.max(0.1f, Math.min(0.9f, value));
    }
}
