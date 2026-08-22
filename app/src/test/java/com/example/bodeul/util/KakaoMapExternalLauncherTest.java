package com.example.bodeul.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KakaoMapExternalLauncherTest {
    private static final String APP_URL = "kakaomap://open?page=placeSearch";
    private static final String MOBILE_WEB_URL =
            "http://m.map.kakao.com/scheme/open?page=placeSearch";
    private static final String MARKET_URL = "market://details?id=net.daum.android.map";
    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=net.daum.android.map";

    @Test
    public void openPlaceSearch_stopsWhenKakaoMapOpens() {
        RecordingStarter starter = new RecordingStarter(APP_URL);

        assertEquals(
                KakaoMapExternalLauncher.PlaceSearchResult.KAKAO_APP,
                KakaoMapExternalLauncher.openPlaceSearch(starter));
        assertEquals(Arrays.asList(APP_URL), starter.attempts);
    }

    @Test
    public void openPlaceSearch_usesMobileWebWhenAppIsUnavailable() {
        RecordingStarter starter = new RecordingStarter(MOBILE_WEB_URL);

        assertEquals(
                KakaoMapExternalLauncher.PlaceSearchResult.MOBILE_WEB,
                KakaoMapExternalLauncher.openPlaceSearch(starter));
        assertEquals(Arrays.asList(APP_URL, MOBILE_WEB_URL), starter.attempts);
    }

    @Test
    public void openPlaceSearch_usesMarketWhenAppAndWebAreUnavailable() {
        RecordingStarter starter = new RecordingStarter(MARKET_URL);

        assertEquals(
                KakaoMapExternalLauncher.PlaceSearchResult.APP_STORE,
                KakaoMapExternalLauncher.openPlaceSearch(starter));
        assertEquals(Arrays.asList(APP_URL, MOBILE_WEB_URL, MARKET_URL), starter.attempts);
    }

    @Test
    public void openPlaceSearch_usesPlayStoreWebWhenMarketIsUnavailable() {
        RecordingStarter starter = new RecordingStarter(PLAY_STORE_URL);

        assertEquals(
                KakaoMapExternalLauncher.PlaceSearchResult.APP_STORE,
                KakaoMapExternalLauncher.openPlaceSearch(starter));
        assertEquals(
                Arrays.asList(APP_URL, MOBILE_WEB_URL, MARKET_URL, PLAY_STORE_URL),
                starter.attempts);
    }

    @Test
    public void openPlaceSearch_reportsFailureAfterAllCandidatesFail() {
        RecordingStarter starter = new RecordingStarter("");

        assertEquals(
                KakaoMapExternalLauncher.PlaceSearchResult.FAILED,
                KakaoMapExternalLauncher.openPlaceSearch(starter));
        assertEquals(
                Arrays.asList(APP_URL, MOBILE_WEB_URL, MARKET_URL, PLAY_STORE_URL),
                starter.attempts);
    }

    private static final class RecordingStarter implements KakaoMapExternalLauncher.UrlStarter {
        private final String successfulUrl;
        private final List<String> attempts = new ArrayList<>();

        private RecordingStarter(String successfulUrl) {
            this.successfulUrl = successfulUrl;
        }

        @Override
        public boolean start(String url) {
            attempts.add(url);
            return successfulUrl.equals(url);
        }
    }
}
