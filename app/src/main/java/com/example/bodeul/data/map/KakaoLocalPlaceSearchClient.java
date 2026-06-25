package com.example.bodeul.data.map;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 카카오 로컬 REST API로 병원과 약국의 실좌표를 조회한다.
 */
public final class KakaoLocalPlaceSearchClient {
    public interface Callback {
        void onSuccess(HospitalMapCoordinateResult result);

        void onError(String message);
    }

    public interface KeywordCallback {
        void onSuccess(List<KakaoPlaceCoordinate> results);

        void onError(String message);
    }

    private static final String KEYWORD_SEARCH_ENDPOINT =
            "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String CATEGORY_HOSPITAL = "HP8";
    private static final String CATEGORY_PHARMACY = "PM9";
    private static final long CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, CacheEntry> COORDINATE_CACHE = new HashMap<>();

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String restApiKey;

    public KakaoLocalPlaceSearchClient(Context context) {
        this.context = context.getApplicationContext();
        this.restApiKey = context.getString(R.string.kakao_rest_api_key).trim();
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(restApiKey);
    }

    public void searchHospitalAndPharmacy(HospitalMapCoordinateQuery query, Callback callback) {
        if (query == null || query.isEmpty()) {
            postSuccess(callback, new HospitalMapCoordinateResult(null, null));
            return;
        }
        if (!isConfigured()) {
            postSuccess(callback, new HospitalMapCoordinateResult(null, null));
            return;
        }

        String cacheKey = buildCacheKey(query);
        HospitalMapCoordinateResult cachedResult = findCachedResult(cacheKey);
        if (cachedResult != null) {
            postSuccess(callback, cachedResult);
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                KakaoPlaceCoordinate hospitalCoordinate = searchWithFallback(
                        query.buildPrimaryHospitalQuery(),
                        query.buildFallbackHospitalQuery(),
                        CATEGORY_HOSPITAL
                );
                KakaoPlaceCoordinate pharmacyCoordinate = searchWithFallback(
                        query.buildPrimaryPharmacyQuery(),
                        query.buildFallbackPharmacyQuery(),
                        CATEGORY_PHARMACY
                );
                HospitalMapCoordinateResult result = new HospitalMapCoordinateResult(
                        hospitalCoordinate,
                        pharmacyCoordinate
                );
                cacheResult(cacheKey, result);
                postSuccess(callback, result);
            } catch (Exception exception) {
                postError(callback, context.getString(R.string.kakao_map_coordinate_load_error));
            }
        });
    }

    public void searchKeyword(String query, KeywordCallback callback) {
        if (TextUtils.isEmpty(query)) {
            postKeywordSuccess(callback, Collections.emptyList());
            return;
        }
        if (!isConfigured()) {
            postKeywordSuccess(callback, Collections.emptyList());
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                List<KakaoPlaceCoordinate> results = searchAll(query, CATEGORY_HOSPITAL);
                postKeywordSuccess(callback, results);
            } catch (Exception exception) {
                postKeywordError(callback, context.getString(R.string.kakao_local_keyword_search_error));
            }
        });
    }

    private String buildCacheKey(HospitalMapCoordinateQuery query) {
        return query.buildPrimaryHospitalQuery() + "|" + query.buildPrimaryPharmacyQuery();
    }

    @Nullable
    private HospitalMapCoordinateResult findCachedResult(String cacheKey) {
        synchronized (COORDINATE_CACHE) {
            CacheEntry entry = COORDINATE_CACHE.get(cacheKey);
            if (entry == null) {
                return null;
            }
            if (System.currentTimeMillis() - entry.cachedAtMillis > CACHE_TTL_MILLIS) {
                COORDINATE_CACHE.remove(cacheKey);
                return null;
            }
            return entry.result;
        }
    }

    private void cacheResult(String cacheKey, HospitalMapCoordinateResult result) {
        synchronized (COORDINATE_CACHE) {
            COORDINATE_CACHE.put(cacheKey, new CacheEntry(result, System.currentTimeMillis()));
        }
    }

    @Nullable
    private KakaoPlaceCoordinate searchWithFallback(
            String primaryQuery,
            String fallbackQuery,
            String categoryGroupCode
    ) throws Exception {
        KakaoPlaceCoordinate result = searchFirst(primaryQuery, categoryGroupCode);
        if (result != null || TextUtils.equals(primaryQuery, fallbackQuery)) {
            return result;
        }
        return searchFirst(fallbackQuery, categoryGroupCode);
    }

    @Nullable
    private KakaoPlaceCoordinate searchFirst(String query, String categoryGroupCode) throws Exception {
        List<KakaoPlaceCoordinate> results = searchAll(query, categoryGroupCode);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    private List<KakaoPlaceCoordinate> searchAll(String query, String categoryGroupCode) throws Exception {
        if (TextUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(buildKeywordSearchUrl(query, categoryGroupCode))
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization", "KakaoAK " + restApiKey);
            connection.setRequestProperty("Accept", "application/json");

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                return Collections.emptyList();
            }

            String payload = readAll(stream);
            JSONObject root = new JSONObject(payload);
            JSONArray documents = root.optJSONArray("documents");
            if (documents == null || documents.length() == 0) {
                return Collections.emptyList();
            }

            List<KakaoPlaceCoordinate> results = new ArrayList<>();
            for (int i = 0; i < documents.length(); i++) {
                JSONObject item = documents.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("place_name");
                double latitude = parseCoordinate(item.optString("y"));
                double longitude = parseCoordinate(item.optString("x"));
                if (latitude != 0d || longitude != 0d) {
                    results.add(new KakaoPlaceCoordinate(name, latitude, longitude));
                }
            }
            return results;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildKeywordSearchUrl(String query, String categoryGroupCode) throws Exception {
        StringBuilder builder = new StringBuilder(KEYWORD_SEARCH_ENDPOINT)
                .append("?size=15&query=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
        if (!TextUtils.isEmpty(categoryGroupCode)) {
            builder.append("&category_group_code=")
                    .append(URLEncoder.encode(categoryGroupCode, StandardCharsets.UTF_8.name()));
        }
        return builder.toString();
    }

    private double parseCoordinate(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0d;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private String readAll(InputStream inputStream) throws Exception {
        try (InputStream stream = new BufferedInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void postSuccess(Callback callback, HospitalMapCoordinateResult result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void postKeywordSuccess(KeywordCallback callback, List<KakaoPlaceCoordinate> results) {
        mainHandler.post(() -> callback.onSuccess(results));
    }

    private void postKeywordError(KeywordCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private static final class CacheEntry {
        private final HospitalMapCoordinateResult result;
        private final long cachedAtMillis;

        private CacheEntry(HospitalMapCoordinateResult result, long cachedAtMillis) {
            this.result = result;
            this.cachedAtMillis = cachedAtMillis;
        }
    }
}
