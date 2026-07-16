package com.bodeul.core.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalClientTests {

    private KakaoLocalClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        KakaoLocalProperties properties = new KakaoLocalProperties();
        properties.setRestApiKey("test-rest-api-key");

        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void mapsKakaoKeywordResponseWithoutExposingProviderPayload() {
        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%84%9C%EC%9A%B8%EB%8C%80%EB%B3%91%EC%9B%90&category_group_code=HP8&size=15"))
                .andExpect(queryParam("category_group_code", "HP8"))
                .andExpect(queryParam("size", "15"))
                .andExpect(header("Authorization", "KakaoAK test-rest-api-key"))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            {"place_name":"서울대학교병원","y":"37.5796","x":"126.9990"},
                            {"place_name":"좌표 오류","y":"unknown","x":"126.9"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search("서울대병원", PlaceSearchCategory.HOSPITAL))
                .containsExactly(new PlaceSearchResult("서울대학교병원", 37.5796d, 126.9990d));
        server.verify();
    }

    @Test
    void mapsProviderQuotaResponseWithoutReturningRawBody() {
        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%95%BD%EA%B5%AD&category_group_code=PM9&size=15"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("provider quota detail")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("약국", PlaceSearchCategory.PHARMACY))
                .isInstanceOfSatisfying(
                        KakaoLocalClient.KakaoLocalClientException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(KakaoLocalClient.Failure.QUOTA_EXCEEDED));
        server.verify();
    }
}
