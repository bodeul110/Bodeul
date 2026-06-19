package com.example.bodeul.data.map;

/**
 * 카카오 로컬 검색으로 확인한 실제 좌표 한 건을 담는다.
 */
public final class KakaoPlaceCoordinate {
    private final String name;
    private final double latitude;
    private final double longitude;

    public KakaoPlaceCoordinate(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
