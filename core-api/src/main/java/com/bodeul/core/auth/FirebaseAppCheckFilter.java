package com.bodeul.core.auth;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FirebaseAppCheckFilter extends OncePerRequestFilter {

    static final String APP_CHECK_HEADER = "X-Firebase-AppCheck";
    private static final int MAX_TOKEN_LENGTH = 8192;
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseAppCheckFilter.class);

    private final AppCheckTokenVerifier tokenVerifier;
    private final ApiErrorWriter errorWriter;
    private final Mode mode;

    public FirebaseAppCheckFilter(
            AppCheckTokenVerifier tokenVerifier,
            ApiErrorWriter errorWriter,
            @Value("${bodeul.app-check.mode:off}") String configuredMode) {
        this.tokenVerifier = tokenVerifier;
        this.errorWriter = errorWriter;
        this.mode = Mode.from(configuredMode);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/health".equals(path) || path.startsWith("/health/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (mode == Mode.OFF || !hasAuthenticatedUser()) {
            filterChain.doFilter(request, response);
            return;
        }

        VerificationResult result = verify(request);
        if (mode == Mode.OBSERVE) {
            LOGGER.info(
                    "app_check_verdict={} app_id={} path={}",
                    result.verdict().logValue,
                    result.appId() == null ? "-" : result.appId(),
                    request.getServletPath());
            filterChain.doFilter(request, response);
            return;
        }

        switch (result.verdict()) {
            case VALID -> filterChain.doFilter(request, response);
            case MISSING -> errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "missing_app_check",
                    "X-Firebase-AppCheck 헤더가 필요합니다.");
            case INVALID -> errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_app_check",
                    "Firebase App Check token 검증에 실패했습니다.");
            case UNAVAILABLE -> errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "app_check_not_configured",
                    "Firebase App Check token 검증기가 아직 설정되지 않았습니다.");
        }
    }

    private VerificationResult verify(HttpServletRequest request) {
        List<String> headers = Collections.list(request.getHeaders(APP_CHECK_HEADER));
        if (headers.isEmpty()) {
            return new VerificationResult(Verdict.MISSING, null);
        }
        if (headers.size() != 1) {
            return new VerificationResult(Verdict.INVALID, null);
        }

        String token = headers.getFirst();
        if (token == null
                || token.isBlank()
                || token.length() > MAX_TOKEN_LENGTH
                || token.chars().anyMatch(Character::isWhitespace)) {
            return new VerificationResult(Verdict.INVALID, null);
        }

        try {
            AppCheckTokenVerifier.VerifiedToken verifiedToken = tokenVerifier.verify(token);
            return new VerificationResult(Verdict.VALID, verifiedToken.appId());
        } catch (AppCheckTokenVerifier.InvalidTokenException exception) {
            return new VerificationResult(Verdict.INVALID, null);
        } catch (AppCheckTokenVerifier.UnavailableException exception) {
            return new VerificationResult(Verdict.UNAVAILABLE, null);
        }
    }

    private boolean hasAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    enum Mode {
        OFF,
        OBSERVE,
        ENFORCE;

        static Mode from(String configuredMode) {
            String normalized = configuredMode == null
                    ? ""
                    : configuredMode.trim().toUpperCase(Locale.ROOT);
            try {
                return Mode.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "bodeul.app-check.mode는 off, observe, enforce 중 하나여야 합니다.");
            }
        }
    }

    private enum Verdict {
        VALID("valid"),
        MISSING("missing"),
        INVALID("invalid"),
        UNAVAILABLE("unavailable");

        private final String logValue;

        Verdict(String logValue) {
            this.logValue = logValue;
        }
    }

    private record VerificationResult(Verdict verdict, String appId) {
    }
}
