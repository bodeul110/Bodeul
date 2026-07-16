package com.bodeul.core.auth;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class FirebaseAppCheckConfiguration {

    private static final String JWKS_URI = "https://firebaseappcheck.googleapis.com/v1/jwks";
    private static final Pattern PROJECT_NUMBER_PATTERN = Pattern.compile("[1-9][0-9]*");
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseAppCheckConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(AppCheckTokenVerifier.class)
    AppCheckTokenVerifier appCheckTokenVerifier(
            @Value("${bodeul.app-check.project-number:}") String projectNumber) {
        return createVerifier(projectNumber, FirebaseAppCheckConfiguration::createDecoder);
    }

    static AppCheckTokenVerifier createVerifier(String projectNumber, DecoderFactory decoderFactory) {
        String normalizedProjectNumber = projectNumber == null ? "" : projectNumber.trim();
        if (!PROJECT_NUMBER_PATTERN.matcher(normalizedProjectNumber).matches()) {
            return unavailableVerifier();
        }

        final TokenDecoder decoder;
        try {
            decoder = decoderFactory.create(normalizedProjectNumber);
        } catch (Exception exception) {
            LOGGER.error("Firebase App Check 검증기 초기화에 실패했습니다. project number 설정을 확인하세요.");
            return unavailableVerifier();
        }

        return appCheckToken -> {
            try {
                return new AppCheckTokenVerifier.VerifiedToken(decoder.verifyAndGetAppId(appCheckToken));
            } catch (AppCheckTokenVerifier.InvalidTokenException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new AppCheckTokenVerifier.InvalidTokenException();
            }
        };
    }

    static OAuth2TokenValidator<Jwt> createValidator(String projectNumber) {
        String issuer = "https://firebaseappcheck.googleapis.com/" + projectNumber;
        String audience = "projects/" + projectNumber;
        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "App Check audience가 일치하지 않습니다.", null));
        };
        return new DelegatingOAuth2TokenValidator<>(issuerAndTimestampValidator, audienceValidator);
    }

    private static TokenDecoder createDecoder(String projectNumber) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        jwtDecoder.setJwtValidator(createValidator(projectNumber));
        return new FirebaseAppCheckJwtDecoder(jwtDecoder);
    }

    private static AppCheckTokenVerifier unavailableVerifier() {
        return appCheckToken -> {
            throw new AppCheckTokenVerifier.UnavailableException();
        };
    }

    static final class FirebaseAppCheckJwtDecoder implements TokenDecoder {
        private final JwtDecoder jwtDecoder;

        FirebaseAppCheckJwtDecoder(JwtDecoder jwtDecoder) {
            this.jwtDecoder = jwtDecoder;
        }

        @Override
        public String verifyAndGetAppId(String appCheckToken) {
            Jwt jwt = jwtDecoder.decode(appCheckToken);
            if (!"JWT".equals(jwt.getHeaders().get("typ"))) {
                throw new AppCheckTokenVerifier.InvalidTokenException();
            }
            return jwt.getSubject();
        }
    }

    @FunctionalInterface
    interface DecoderFactory {
        TokenDecoder create(String projectNumber) throws Exception;
    }

    @FunctionalInterface
    interface TokenDecoder {
        String verifyAndGetAppId(String appCheckToken) throws Exception;
    }
}
