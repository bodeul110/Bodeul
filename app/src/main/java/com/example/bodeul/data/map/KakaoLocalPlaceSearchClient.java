package com.example.bodeul.data.map;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Core API를 우선 사용하고 전환 검증 전에는 기존 Kakao Local 직접 호출을 rollback 경로로 유지한다.
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
    private static final String CORE_CATEGORY_HOSPITAL = "HOSPITAL";
    private static final String CORE_CATEGORY_PHARMACY = "PHARMACY";
    private static final String CATEGORY_HOSPITAL = "HP8";
    private static final String CATEGORY_PHARMACY = "PM9";
    private static final long CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, CacheEntry> COORDINATE_CACHE = new HashMap<>();

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String coreApiBaseUrl;
    private final String restApiKey;

    public KakaoLocalPlaceSearchClient(Context context) {
        this.context = context.getApplicationContext();
        this.coreApiBaseUrl = normalizeBaseUrl(
                context.getString(R.string.bodeul_core_api_base_url));
        this.restApiKey = context.getString(R.string.kakao_rest_api_key).trim();
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(coreApiBaseUrl) || !TextUtils.isEmpty(restApiKey);
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

        executePreferredSearch(
                idToken -> searchHospitalAndPharmacyFromCore(query, idToken),
                () -> searchHospitalAndPharmacyDirect(query),
                new SearchResultHandler<HospitalMapCoordinateResult>() {
                    @Override
                    public void onSuccess(HospitalMapCoordinateResult result) {
                        cacheResult(cacheKey, result);
                        postSuccess(callback, result);
                    }

                    @Override
                    public void onError() {
                        postError(callback, context.getString(R.string.kakao_map_coordinate_load_error));
                    }
                }
        );
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

        executePreferredSearch(
                idToken -> searchAllFromCore(query, CORE_CATEGORY_HOSPITAL, idToken),
                () -> searchAllDirect(query, CATEGORY_HOSPITAL),
                new SearchResultHandler<List<KakaoPlaceCoordinate>>() {
                    @Override
                    public void onSuccess(List<KakaoPlaceCoordinate> result) {
                        postKeywordSuccess(callback, result);
                    }

                    @Override
                    public void onError() {
                        postKeywordError(
                                callback,
                                context.getString(R.string.kakao_local_keyword_search_error));
                    }
                }
        );
    }

    private HospitalMapCoordinateResult searchHospitalAndPharmacyFromCore(
            HospitalMapCoordinateQuery query,
            String idToken) throws Exception {
        KakaoPlaceCoordinate hospitalCoordinate = searchWithFallback(
                query.buildPrimaryHospitalQuery(),
                query.buildFallbackHospitalQuery(),
                CORE_CATEGORY_HOSPITAL,
                (searchQuery, category) -> searchAllFromCore(searchQuery, category, idToken)
        );
        KakaoPlaceCoordinate pharmacyCoordinate = searchWithFallback(
                query.buildPrimaryPharmacyQuery(),
                query.buildFallbackPharmacyQuery(),
                CORE_CATEGORY_PHARMACY,
                (searchQuery, category) -> searchAllFromCore(searchQuery, category, idToken)
        );
        return new HospitalMapCoordinateResult(hospitalCoordinate, pharmacyCoordinate);
    }

    private HospitalMapCoordinateResult searchHospitalAndPharmacyDirect(
            HospitalMapCoordinateQuery query) throws Exception {
        KakaoPlaceCoordinate hospitalCoordinate = searchWithFallback(
                query.buildPrimaryHospitalQuery(),
                query.buildFallbackHospitalQuery(),
                CATEGORY_HOSPITAL,
                this::searchAllDirect
        );
        KakaoPlaceCoordinate pharmacyCoordinate = searchWithFallback(
                query.buildPrimaryPharmacyQuery(),
                query.buildFallbackPharmacyQuery(),
                CATEGORY_PHARMACY,
                this::searchAllDirect
        );
        return new HospitalMapCoordinateResult(hospitalCoordinate, pharmacyCoordinate);
    }

    private <T> void executePreferredSearch(
            CoreSearchOperation<T> coreOperation,
            DirectSearchOperation<T> directOperation,
            SearchResultHandler<T> resultHandler) {
        if (!TextUtils.isEmpty(coreApiBaseUrl)) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                currentUser.getIdToken(false).addOnCompleteListener(task -> {
                    String idToken = task.isSuccessful() && task.getResult() != null
                            ? task.getResult().getToken()
                            : "";
                    if (!TextUtils.isEmpty(idToken)) {
                        EXECUTOR.execute(() -> executeCoreThenFallback(
                                idToken,
                                coreOperation,
                                directOperation,
                                resultHandler));
                    } else {
                        executeDirectOrError(directOperation, resultHandler);
                    }
                });
                return;
            }
        }
        executeDirectOrError(directOperation, resultHandler);
    }

    private <T> void executeCoreThenFallback(
            String idToken,
            CoreSearchOperation<T> coreOperation,
            DirectSearchOperation<T> directOperation,
            SearchResultHandler<T> resultHandler) {
        try {
            resultHandler.onSuccess(coreOperation.run(idToken));
        } catch (Exception coreException) {
            if (TextUtils.isEmpty(restApiKey)) {
                resultHandler.onError();
                return;
            }
            try {
                resultHandler.onSuccess(directOperation.run());
            } catch (Exception directException) {
                resultHandler.onError();
            }
        }
    }

    private <T> void executeDirectOrError(
            DirectSearchOperation<T> directOperation,
            SearchResultHandler<T> resultHandler) {
        if (TextUtils.isEmpty(restApiKey)) {
            resultHandler.onError();
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                resultHandler.onSuccess(directOperation.run());
            } catch (Exception exception) {
                resultHandler.onError();
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
            String category,
            PlaceSearcher placeSearcher) throws Exception {
        KakaoPlaceCoordinate result = searchFirst(primaryQuery, category, placeSearcher);
        if (result != null || TextUtils.equals(primaryQuery, fallbackQuery)) {
            return result;
        }
        return searchFirst(fallbackQuery, category, placeSearcher);
    }

    @Nullable
    private KakaoPlaceCoordinate searchFirst(
            String query,
            String category,
            PlaceSearcher placeSearcher) throws Exception {
        List<KakaoPlaceCoordinate> results = placeSearcher.search(query, category);
        return results.isEmpty() ? null : results.get(0);
    }

    private List<KakaoPlaceCoordinate> searchAllFromCore(
            String query,
            String category,
            String idToken) throws Exception {
        if (TextUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(
                    buildCoreSearchUrl(query, category)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IOException("Core API place search failed");
            }
            JSONObject root = new JSONObject(readAll(connection.getInputStream()));
            JSONArray places = root.optJSONArray("places");
            if (places == null || places.length() == 0) {
                return Collections.emptyList();
            }

            List<KakaoPlaceCoordinate> results = new ArrayList<>();
            for (int i = 0; i < places.length(); i++) {
                JSONObject item = places.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name");
                double latitude = item.optDouble("latitude", 0d);
                double longitude = item.optDouble("longitude", 0d);
                if (!TextUtils.isEmpty(name) && (latitude != 0d || longitude != 0d)) {
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

    private List<KakaoPlaceCoordinate> searchAllDirect(
            String query,
            String categoryGroupCode) throws Exception {
        if (TextUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(
                    buildKakaoKeywordSearchUrl(query, categoryGroupCode)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization", "KakaoAK " + restApiKey);
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IOException("Kakao Local place search failed");
            }
            JSONObject root = new JSONObject(readAll(connection.getInputStream()));
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
                if (!TextUtils.isEmpty(name) && (latitude != 0d || longitude != 0d)) {
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

    private String buildCoreSearchUrl(String query, String category) throws Exception {
        return coreApiBaseUrl
                + "/api/places/search?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                + "&category="
                + URLEncoder.encode(category, StandardCharsets.UTF_8.name());
    }

    private String buildKakaoKeywordSearchUrl(String query, String categoryGroupCode) throws Exception {
        StringBuilder builder = new StringBuilder(KEYWORD_SEARCH_ENDPOINT)
                .append("?size=15&query=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
        if (!TextUtils.isEmpty(categoryGroupCode)) {
            builder.append("&category_group_code=")
                    .append(URLEncoder.encode(categoryGroupCode, StandardCharsets.UTF_8.name()));
        }
        return builder.toString();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
                if (outputStream.size() + read > MAX_RESPONSE_BYTES) {
                    throw new IOException("Place search response is too large");
                }
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

    private interface CoreSearchOperation<T> {
        T run(String idToken) throws Exception;
    }

    private interface DirectSearchOperation<T> {
        T run() throws Exception;
    }

    private interface SearchResultHandler<T> {
        void onSuccess(T result);

        void onError();
    }

    private interface PlaceSearcher {
        List<KakaoPlaceCoordinate> search(String query, String category) throws Exception;
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
