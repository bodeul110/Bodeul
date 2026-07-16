package com.bodeul.core.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(OutputCaptureExtension.class)
class FirebaseAppCheckConfigurationTests {

    private static final String PROJECT_NUMBER = "533563500316";
    private static final String ISSUER = "https://firebaseappcheck.googleapis.com/" + PROJECT_NUMBER;
    private static final String AUDIENCE = "projects/" + PROJECT_NUMBER;

    @Test
    void missingProjectNumberDisablesVerifier() {
        AppCheckTokenVerifier verifier = FirebaseAppCheckConfiguration.createVerifier(" ", projectNumber -> {
            fail("project number가 없으면 검증기를 만들지 않아야 합니다.");
            return appCheckToken -> "unused";
        });

        assertThatThrownBy(() -> verifier.verify("app-check-token"))
                .isInstanceOf(AppCheckTokenVerifier.UnavailableException.class);
    }

    @Test
    void nonNumericProjectNumberDisablesVerifier() {
        AppCheckTokenVerifier verifier = FirebaseAppCheckConfiguration.createVerifier(
                "bodeul-dev",
                projectNumber -> {
                    fail("숫자가 아닌 project number로 검증기를 만들지 않아야 합니다.");
                    return appCheckToken -> "unused";
                });

        assertThatThrownBy(() -> verifier.verify("app-check-token"))
                .isInstanceOf(AppCheckTokenVerifier.UnavailableException.class);
    }

    @Test
    void initializationFailureDoesNotExposeDetails(CapturedOutput output) {
        AppCheckTokenVerifier verifier = FirebaseAppCheckConfiguration.createVerifier(
                PROJECT_NUMBER,
                projectNumber -> {
                    throw new IllegalStateException("private_key=local-secret-key");
                });

        assertThatThrownBy(() -> verifier.verify("app-check-token"))
                .isInstanceOf(AppCheckTokenVerifier.UnavailableException.class)
                .hasMessageNotContaining("local-secret-key");

        assertThat(output)
                .contains("Firebase App Check 검증기 초기화에 실패했습니다.")
                .doesNotContain("local-secret-key")
                .doesNotContain("private_key");
    }

    @Test
    void returnsOnlyAppIdVerifiedByDecoder() {
        AppCheckTokenVerifier verifier = FirebaseAppCheckConfiguration.createVerifier(
                PROJECT_NUMBER,
                projectNumber -> {
                    assertThat(projectNumber).isEqualTo(PROJECT_NUMBER);
                    return appCheckToken -> {
                        assertThat(appCheckToken).isEqualTo("app-check-token");
                        return "1:533563500316:android:registered-app";
                    };
                });

        assertThat(verifier.verify("app-check-token").appId())
                .isEqualTo("1:533563500316:android:registered-app");
    }

    @Test
    void mapsDecoderFailureToGenericTokenError() {
        AppCheckTokenVerifier verifier = FirebaseAppCheckConfiguration.createVerifier(
                PROJECT_NUMBER,
                projectNumber -> appCheckToken -> {
                    throw new IllegalArgumentException("signature details");
                });

        assertThatThrownBy(() -> verifier.verify("raw-app-check-token"))
                .isInstanceOf(AppCheckTokenVerifier.InvalidTokenException.class)
                .hasMessageNotContaining("signature details")
                .hasMessageNotContaining("raw-app-check-token");
    }

    @Test
    void acceptsExpectedIssuerAudienceAndTimestamp() {
        OAuth2TokenValidator<Jwt> validator = FirebaseAppCheckConfiguration.createValidator(PROJECT_NUMBER);

        assertThat(validator.validate(validJwt()).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenFromAnotherProject() {
        OAuth2TokenValidator<Jwt> validator = FirebaseAppCheckConfiguration.createValidator(PROJECT_NUMBER);

        Jwt wrongIssuer = jwt(
                "https://firebaseappcheck.googleapis.com/999999999999",
                List.of(AUDIENCE),
                Instant.now().plusSeconds(300));
        Jwt wrongAudience = jwt(
                ISSUER,
                List.of("projects/999999999999"),
                Instant.now().plusSeconds(300));

        assertThat(validator.validate(wrongIssuer).hasErrors()).isTrue();
        assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        OAuth2TokenValidator<Jwt> validator = FirebaseAppCheckConfiguration.createValidator(PROJECT_NUMBER);
        Jwt expired = jwt(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(600));

        assertThat(validator.validate(expired).hasErrors()).isTrue();
    }

    @Test
    void decoderRequiresJwtTypeHeader() {
        FirebaseAppCheckConfiguration.FirebaseAppCheckJwtDecoder decoder =
                new FirebaseAppCheckConfiguration.FirebaseAppCheckJwtDecoder(token -> jwtWithHeaders(
                        Map.of("alg", "RS256")));

        assertThatThrownBy(() -> decoder.verifyAndGetAppId("app-check-token"))
                .isInstanceOf(AppCheckTokenVerifier.InvalidTokenException.class);
    }

    private Jwt validJwt() {
        return jwt(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300));
    }

    private Jwt jwt(String issuer, List<String> audience, Instant expiresAt) {
        Instant issuedAt = expiresAt.minusSeconds(300);
        return new Jwt(
                "app-check-token",
                issuedAt,
                expiresAt,
                Map.of("alg", "RS256", "typ", "JWT"),
                Map.of(
                        "iss", issuer,
                        "aud", audience,
                        "sub", "1:533563500316:android:registered-app"));
    }

    private Jwt jwtWithHeaders(Map<String, Object> headers) {
        Instant now = Instant.now();
        return new Jwt(
                "app-check-token",
                now.minusSeconds(30),
                now.plusSeconds(300),
                headers,
                Map.of(
                        "iss", ISSUER,
                        "aud", List.of(AUDIENCE),
                        "sub", "1:533563500316:android:registered-app"));
    }
}
