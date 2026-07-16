package com.bodeul.core.place;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoLocalProperties.class)
class KakaoLocalConfiguration {

    @Bean
    @Qualifier("kakaoLocalRestClient")
    RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis(properties.getConnectTimeout()));
        requestFactory.setReadTimeout(timeoutMillis(properties.getReadTimeout()));

        String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? "https://dapi.kakao.com"
                : properties.getBaseUrl().trim();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private int timeoutMillis(Duration duration) {
        long millis = duration == null ? 5_000L : Math.max(1L, duration.toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
