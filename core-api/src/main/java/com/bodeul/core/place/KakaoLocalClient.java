package com.bodeul.core.place;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class KakaoLocalClient {

    private static final int RESULT_SIZE = 15;

    private final RestClient restClient;
    private final KakaoLocalProperties properties;
    private final ObjectMapper objectMapper;

    public KakaoLocalClient(
            @Qualifier("kakaoLocalRestClient") RestClient restClient,
            KakaoLocalProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<PlaceSearchResult> search(String query, PlaceSearchCategory category) {
        String apiKey = properties.getRestApiKey() == null ? "" : properties.getRestApiKey().trim();
        if (apiKey.isEmpty()) {
            throw new KakaoLocalClientException(Failure.NOT_CONFIGURED);
        }

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("category_group_code", category.kakaoCategoryCode())
                            .queryParam("size", RESULT_SIZE)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> readResponse(response.getStatusCode().value(), response.getBody()));
        } catch (KakaoLocalClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new KakaoLocalClientException(Failure.UNAVAILABLE);
        } catch (Exception exception) {
            throw new KakaoLocalClientException(Failure.INVALID_RESPONSE);
        }
    }

    private List<PlaceSearchResult> readResponse(int statusCode, java.io.InputStream body) throws IOException {
        if (statusCode == 429) {
            throw new KakaoLocalClientException(Failure.QUOTA_EXCEEDED);
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new KakaoLocalClientException(Failure.INVALID_CREDENTIALS);
        }
        if (statusCode == 400) {
            throw new KakaoLocalClientException(Failure.REQUEST_REJECTED);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new KakaoLocalClientException(Failure.UNAVAILABLE);
        }

        KakaoSearchResponse response = objectMapper.readValue(body, KakaoSearchResponse.class);
        if (response.documents() == null) {
            return Collections.emptyList();
        }
        return response.documents().stream()
                .map(this::toPlaceSearchResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private PlaceSearchResult toPlaceSearchResult(KakaoDocument document) {
        if (document == null || document.placeName() == null || document.placeName().isBlank()) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(document.latitude());
            double longitude = Double.parseDouble(document.longitude());
            if (latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) {
                return null;
            }
            return new PlaceSearchResult(document.placeName().trim(), latitude, longitude);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    enum Failure {
        NOT_CONFIGURED,
        INVALID_CREDENTIALS,
        QUOTA_EXCEEDED,
        REQUEST_REJECTED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    static final class KakaoLocalClientException extends RuntimeException {
        private final Failure failure;

        KakaoLocalClientException(Failure failure) {
            this.failure = failure;
        }

        Failure failure() {
            return failure;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoSearchResponse(List<KakaoDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoDocument(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("y") String latitude,
            @JsonProperty("x") String longitude) {
    }
}
