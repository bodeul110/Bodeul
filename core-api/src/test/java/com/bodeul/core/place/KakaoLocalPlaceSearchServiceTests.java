package com.bodeul.core.place;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoLocalPlaceSearchServiceTests {

    private static final UUID USER_ID = UUID.fromString("a2e55f0c-6407-438b-af7c-5c74abbd6167");

    @Mock
    private KakaoLocalClient client;

    private KakaoLocalProperties properties;
    private KakaoLocalPlaceSearchService service;

    @BeforeEach
    void setUp() {
        properties = new KakaoLocalProperties();
        service = new KakaoLocalPlaceSearchService(
                client,
                properties,
                Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void cachesNormalizedQueryResults() {
        List<PlaceSearchResult> expected = List.of(
                new PlaceSearchResult("서울대학교병원", 37.5796d, 126.9990d));
        when(client.search("서울대병원", PlaceSearchCategory.HOSPITAL)).thenReturn(expected);

        assertThat(service.search(USER_ID, " 서울대병원 ", PlaceSearchCategory.HOSPITAL)).isEqualTo(expected);
        assertThat(service.search(USER_ID, "서울대병원", PlaceSearchCategory.HOSPITAL)).isEqualTo(expected);

        verify(client).search("서울대병원", PlaceSearchCategory.HOSPITAL);
    }

    @Test
    void limitsRequestsPerAuthenticatedUser() {
        properties.setRateLimitRequests(1);
        when(client.search("첫 검색", PlaceSearchCategory.HOSPITAL)).thenReturn(List.of());

        service.search(USER_ID, "첫 검색", PlaceSearchCategory.HOSPITAL);

        assertThatThrownBy(() -> service.search(USER_ID, "두 번째 검색", PlaceSearchCategory.HOSPITAL))
                .isInstanceOfSatisfying(PlaceSearchException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.error()).isEqualTo("place_search_rate_limit_exceeded");
                });
    }

    @Test
    void mapsKakaoQuotaToStableApiError() {
        when(client.search("약국", PlaceSearchCategory.PHARMACY))
                .thenThrow(new KakaoLocalClient.KakaoLocalClientException(
                        KakaoLocalClient.Failure.QUOTA_EXCEEDED));

        assertThatThrownBy(() -> service.search(USER_ID, "약국", PlaceSearchCategory.PHARMACY))
                .isInstanceOfSatisfying(PlaceSearchException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.error()).isEqualTo("kakao_local_quota_exceeded");
                    assertThat(exception.getMessage()).doesNotContain("test-rest-api-key");
                });
    }
}
