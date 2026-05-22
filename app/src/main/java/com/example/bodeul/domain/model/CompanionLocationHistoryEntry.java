package com.example.bodeul.domain.model;

/**
 * 동행 중 공유한 좌표 변화를 시간순으로 기록하는 항목이다.
 */
public final class CompanionLocationHistoryEntry {
    private final double latitude;
    private final double longitude;
    private final String summary;
    private final long capturedAtMillis;

    public CompanionLocationHistoryEntry(
            double latitude,
            double longitude,
            String summary,
            long capturedAtMillis
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.summary = summary == null ? "" : summary;
        this.capturedAtMillis = capturedAtMillis;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getSummary() {
        return summary;
    }

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }
}
