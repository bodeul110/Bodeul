package com.bodeul.core.place;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bodeul.kakao-local")
public class KakaoLocalProperties {

    private String restApiKey = "";
    private String baseUrl = "https://dapi.kakao.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration cacheTtl = Duration.ofHours(6);
    private int cacheMaxEntries = 1_000;
    private Duration rateLimitWindow = Duration.ofMinutes(1);
    private int rateLimitRequests = 60;
    private int rateLimitMaxUsers = 10_000;

    public String getRestApiKey() {
        return restApiKey;
    }

    public void setRestApiKey(String restApiKey) {
        this.restApiKey = restApiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    public void setRateLimitWindow(Duration rateLimitWindow) {
        this.rateLimitWindow = rateLimitWindow;
    }

    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public void setRateLimitRequests(int rateLimitRequests) {
        this.rateLimitRequests = rateLimitRequests;
    }

    public int getRateLimitMaxUsers() {
        return rateLimitMaxUsers;
    }

    public void setRateLimitMaxUsers(int rateLimitMaxUsers) {
        this.rateLimitMaxUsers = rateLimitMaxUsers;
    }
}
