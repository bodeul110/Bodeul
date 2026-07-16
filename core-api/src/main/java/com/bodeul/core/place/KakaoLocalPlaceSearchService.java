package com.bodeul.core.place;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KakaoLocalPlaceSearchService implements PlaceSearchService {

    private static final int MAX_QUERY_LENGTH = 100;

    private final KakaoLocalClient client;
    private final KakaoLocalProperties properties;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<UUID, RateWindow> rateWindows = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public KakaoLocalPlaceSearchService(KakaoLocalClient client, KakaoLocalProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    KakaoLocalPlaceSearchService(KakaoLocalClient client, KakaoLocalProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<PlaceSearchResult> search(UUID userId, String query, PlaceSearchCategory category) {
        if (userId == null) {
            throw PlaceSearchException.invalidRequest("인증된 사용자 정보가 필요합니다.");
        }
        String normalizedQuery = normalizeQuery(query);
        enforceRateLimit(userId);

        CacheKey cacheKey = new CacheKey(category, normalizedQuery.toLowerCase(Locale.ROOT));
        List<PlaceSearchResult> cached = findCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            List<PlaceSearchResult> results = List.copyOf(client.search(normalizedQuery, category));
            cache(cacheKey, results);
            return results;
        } catch (KakaoLocalClient.KakaoLocalClientException exception) {
            throw mapFailure(exception.failure());
        }
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw PlaceSearchException.invalidRequest("검색어가 필요합니다.");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw PlaceSearchException.invalidRequest("검색어는 100자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private synchronized List<PlaceSearchResult> findCached(CacheKey key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        Duration ttl = positiveDuration(properties.getCacheTtl(), Duration.ofHours(6));
        if (!clock.instant().isBefore(entry.cachedAt().plus(ttl))) {
            cache.remove(key);
            return null;
        }
        return entry.results();
    }

    private synchronized void cache(CacheKey key, List<PlaceSearchResult> results) {
        int maxEntries = Math.max(1, properties.getCacheMaxEntries());
        cache.put(key, new CacheEntry(results, clock.instant()));
        removeEldestEntries(cache, maxEntries);
    }

    private synchronized void enforceRateLimit(UUID userId) {
        Instant now = clock.instant();
        Duration windowDuration = positiveDuration(properties.getRateLimitWindow(), Duration.ofMinutes(1));
        int requestLimit = Math.max(1, properties.getRateLimitRequests());
        RateWindow window = rateWindows.get(userId);
        if (window == null || !now.isBefore(window.startedAt().plus(windowDuration))) {
            rateWindows.put(userId, new RateWindow(now, 1));
        } else if (window.requestCount() >= requestLimit) {
            throw PlaceSearchException.rateLimitExceeded();
        } else {
            rateWindows.put(userId, new RateWindow(window.startedAt(), window.requestCount() + 1));
        }
        removeEldestEntries(rateWindows, Math.max(1, properties.getRateLimitMaxUsers()));
    }

    private <K, V> void removeEldestEntries(Map<K, V> entries, int maxEntries) {
        Iterator<K> iterator = entries.keySet().iterator();
        while (entries.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private PlaceSearchException mapFailure(KakaoLocalClient.Failure failure) {
        return switch (failure) {
            case NOT_CONFIGURED -> PlaceSearchException.kakaoNotConfigured();
            case INVALID_CREDENTIALS -> PlaceSearchException.kakaoCredentialsInvalid();
            case QUOTA_EXCEEDED -> PlaceSearchException.kakaoQuotaExceeded();
            case REQUEST_REJECTED, INVALID_RESPONSE -> PlaceSearchException.kakaoResponseInvalid();
            case UNAVAILABLE -> PlaceSearchException.kakaoUnavailable();
        };
    }

    private record CacheKey(PlaceSearchCategory category, String query) {
    }

    private record CacheEntry(List<PlaceSearchResult> results, Instant cachedAt) {
    }

    private record RateWindow(Instant startedAt, int requestCount) {
    }
}
