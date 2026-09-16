package com.example.bodeul.ui.booking;

/** 검색 결과 선택 영역이 부족할 때 지역 바로가기를 접는다. */
final class BookingHospitalSelectorLayoutPolicy {
    private static final float MIN_CONTENT_HEIGHT_WITH_REGIONS_DP = 560f;

    private BookingHospitalSelectorLayoutPolicy() {
    }

    static boolean showRegionShortcuts(
            int contentHeightPx,
            float density,
            float fontScale,
            boolean keyboardVisible) {
        if (keyboardVisible) {
            return false;
        }
        if (contentHeightPx <= 0) {
            return true;
        }
        float safeDensity = density > 0f ? density : 1f;
        float safeFontScale = Math.max(1f, fontScale);
        int minimumHeightPx = Math.round(
                MIN_CONTENT_HEIGHT_WITH_REGIONS_DP * safeDensity * safeFontScale);
        return contentHeightPx >= minimumHeightPx;
    }
}
