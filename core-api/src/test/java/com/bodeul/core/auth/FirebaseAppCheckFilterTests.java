package com.bodeul.core.auth;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class FirebaseAppCheckFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void offModeDoesNotInspectToken() throws Exception {
        Invocation invocation = invoke("off", token -> {
            throw new AssertionError("off 모드에서는 token verifier를 호출하지 않아야 합니다.");
        });

        assertThat(invocation.chainCalled()).isTrue();
        assertThat(invocation.response().getStatus()).isEqualTo(200);
    }

    @Test
    void observeModeRecordsMissingTokenWithoutBlocking(CapturedOutput output) throws Exception {
        Invocation invocation = invoke("observe", token -> {
            throw new AssertionError("누락 token은 verifier를 호출하지 않아야 합니다.");
        });

        assertThat(invocation.chainCalled()).isTrue();
        assertThat(output).contains("app_check_verdict=missing app_id=- path=/api/auth/me");
    }

    @Test
    void observeModeRecordsValidAppIdWithoutRawToken(CapturedOutput output) throws Exception {
        String rawToken = "raw-app-check-token";
        Invocation invocation = invoke(
                "observe",
                token -> new AppCheckTokenVerifier.VerifiedToken(
                        "1:533563500316:android:registered-app"),
                rawToken);

        assertThat(invocation.chainCalled()).isTrue();
        assertThat(output)
                .contains("app_check_verdict=valid")
                .contains("app_id=1:533563500316:android:registered-app")
                .doesNotContain(rawToken);
    }

    @Test
    void enforceModeRejectsMissingToken() throws Exception {
        Invocation invocation = invoke("enforce", token -> {
            throw new AssertionError("누락 token은 verifier를 호출하지 않아야 합니다.");
        });

        assertThat(invocation.chainCalled()).isFalse();
        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.response().getContentAsString()).contains("missing_app_check");
    }

    @Test
    void enforceModeRejectsDuplicateHeadersWithoutRawValues() throws Exception {
        Invocation invocation = invoke(
                "enforce",
                token -> new AppCheckTokenVerifier.VerifiedToken("unused"),
                "first-token",
                "second-token");

        assertThat(invocation.chainCalled()).isFalse();
        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.response().getContentAsString())
                .contains("invalid_app_check")
                .doesNotContain("first-token")
                .doesNotContain("second-token");
    }

    @Test
    void enforceModeRejectsInvalidTokenWithoutRawValue() throws Exception {
        String rawToken = "tampered-app-check-token";
        Invocation invocation = invoke("enforce", token -> {
            throw new AppCheckTokenVerifier.InvalidTokenException();
        }, rawToken);

        assertThat(invocation.chainCalled()).isFalse();
        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.response().getContentAsString())
                .contains("invalid_app_check")
                .doesNotContain(rawToken);
    }

    @Test
    void enforceModeReturns503WhenVerifierIsUnavailable() throws Exception {
        Invocation invocation = invoke("enforce", token -> {
            throw new AppCheckTokenVerifier.UnavailableException();
        }, "app-check-token");

        assertThat(invocation.chainCalled()).isFalse();
        assertThat(invocation.response().getStatus()).isEqualTo(503);
        assertThat(invocation.response().getContentAsString()).contains("app_check_not_configured");
    }

    @Test
    void enforceModeAllowsVerifiedToken() throws Exception {
        Invocation invocation = invoke(
                "enforce",
                token -> new AppCheckTokenVerifier.VerifiedToken(
                        "1:533563500316:android:registered-app"),
                "valid-app-check-token");

        assertThat(invocation.chainCalled()).isTrue();
        assertThat(invocation.response().getStatus()).isEqualTo(200);
    }

    @Test
    void unauthenticatedRequestKeepsExistingAuthorizationContract() throws Exception {
        SecurityContextHolder.clearContext();
        Invocation invocation = invokeWithoutAuthentication(
                "enforce",
                token -> {
                    throw new AssertionError("인증 전 요청은 App Check verifier를 호출하지 않아야 합니다.");
                });

        assertThat(invocation.chainCalled()).isTrue();
    }

    @Test
    void anonymousAuthenticationDoesNotTriggerAppCheck() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key",
                "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        Invocation invocation = invokeWithoutAuthentication("enforce", token -> {
            throw new AssertionError("anonymous 요청은 App Check verifier를 호출하지 않아야 합니다.");
        });

        assertThat(invocation.chainCalled()).isTrue();
    }

    @Test
    void invalidModeFailsFast() {
        assertThatThrownBy(() -> new FirebaseAppCheckFilter(
                token -> new AppCheckTokenVerifier.VerifiedToken("app-id"),
                new ApiErrorWriter(new ObjectMapper()),
                "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("off, observe, enforce");
    }

    private Invocation invoke(
            String mode,
            AppCheckTokenVerifier verifier,
            String... appCheckHeaders) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, List.of()));
        return invokeWithoutAuthentication(mode, verifier, appCheckHeaders);
    }

    private Invocation invokeWithoutAuthentication(
            String mode,
            AppCheckTokenVerifier verifier,
            String... appCheckHeaders) throws Exception {
        FirebaseAppCheckFilter filter = new FirebaseAppCheckFilter(
                verifier,
                new ApiErrorWriter(new ObjectMapper()),
                mode);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setServletPath("/api/auth/me");
        for (String header : appCheckHeaders) {
            request.addHeader(FirebaseAppCheckFilter.APP_CHECK_HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainCalled.set(true));

        return new Invocation(response, chainCalled.get());
    }

    private record Invocation(MockHttpServletResponse response, boolean chainCalled) {
    }
}
