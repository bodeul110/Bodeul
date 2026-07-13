package com.bodeul.core.auth;

import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class FirebaseAdminConfiguration {

    private static final String APP_NAME = "bodeul-core-api";
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseAdminConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(FirebaseTokenVerifier.class)
    FirebaseTokenVerifier firebaseTokenVerifier(
            @Value("${FIREBASE_PROJECT_ID:}") String projectId) {
        return createVerifier(projectId, this::createDecoder);
    }

    static FirebaseTokenVerifier createVerifier(String projectId, DecoderFactory decoderFactory) {
        if (projectId == null || projectId.isBlank()) {
            return unavailableVerifier();
        }

        final TokenDecoder decoder;
        try {
            decoder = decoderFactory.create(projectId.trim());
        } catch (Exception exception) {
            LOGGER.error("Firebase Admin SDK 초기화에 실패했습니다. project ID와 ADC 설정을 확인하세요.");
            return unavailableVerifier();
        }

        return idToken -> {
            try {
                return new FirebaseTokenVerifier.VerifiedToken(decoder.verifyAndGetUid(idToken));
            } catch (FirebaseTokenVerifier.InvalidTokenException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new FirebaseTokenVerifier.InvalidTokenException();
            }
        };
    }

    private static FirebaseTokenVerifier unavailableVerifier() {
        return idToken -> {
            throw new FirebaseTokenVerifier.UnavailableException();
        };
    }

    private TokenDecoder createDecoder(String projectId) throws Exception {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();

        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(candidate -> APP_NAME.equals(candidate.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));

        String configuredProjectId = app.getOptions().getProjectId();
        if (!Objects.equals(projectId, configuredProjectId)) {
            throw new IllegalStateException("Firebase project 설정이 일치하지 않습니다.");
        }

        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        return idToken -> auth.verifyIdToken(idToken).getUid();
    }

    @FunctionalInterface
    interface DecoderFactory {
        TokenDecoder create(String projectId) throws Exception;
    }

    @FunctionalInterface
    interface TokenDecoder {
        String verifyAndGetUid(String idToken) throws Exception;
    }
}
