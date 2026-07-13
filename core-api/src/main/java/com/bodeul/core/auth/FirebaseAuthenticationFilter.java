package com.bodeul.core.auth;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier tokenVerifier;
    private final ObjectProvider<AppUserRepository> appUserRepositoryProvider;
    private final ApiErrorWriter errorWriter;

    public FirebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier,
            ObjectProvider<AppUserRepository> appUserRepositoryProvider,
            ApiErrorWriter errorWriter) {
        this.tokenVerifier = tokenVerifier;
        this.appUserRepositoryProvider = appUserRepositoryProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/healthz".equals(path) || path.startsWith("/healthz/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> authorizationHeaders = Collections.list(request.getHeaders("Authorization"));
        if (authorizationHeaders.isEmpty() || authorizationHeaders.getFirst().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authorizationHeaders.size() != 1) {
            writeInvalidAuthorization(response);
            return;
        }

        String authorization = authorizationHeaders.getFirst();
        String idToken = extractBearerToken(authorization);
        if (idToken == null) {
            writeInvalidAuthorization(response);
            return;
        }

        FirebaseTokenVerifier.VerifiedToken verifiedToken;
        try {
            verifiedToken = tokenVerifier.verify(idToken);
        } catch (FirebaseTokenVerifier.InvalidTokenException exception) {
            errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_firebase_token",
                    "Firebase ID token 검증에 실패했습니다.");
            return;
        } catch (FirebaseTokenVerifier.UnavailableException exception) {
            errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "auth_not_configured",
                    "Firebase ID token 검증기가 아직 설정되지 않았습니다.");
            return;
        }

        AppUserRepository appUserRepository = appUserRepositoryProvider.getIfAvailable();
        if (appUserRepository == null) {
            errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "authorization_not_configured",
                    "사용자 역할 확인기가 아직 설정되지 않았습니다.");
            return;
        }

        final AppUserRepository.AppUser appUser;
        try {
            appUser = appUserRepository.findByFirebaseUid(verifiedToken.uid()).orElse(null);
        } catch (DataAccessException exception) {
            errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "role_lookup_failed",
                    "사용자 역할 확인에 실패했습니다.");
            return;
        }

        if (appUser == null) {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "role_not_found",
                    "서비스에 등록된 사용자 역할이 없습니다.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                appUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + appUser.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private void writeInvalidAuthorization(HttpServletResponse response) throws IOException {
        errorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "invalid_authorization",
                "Authorization 헤더는 하나의 Bearer 토큰 형식이어야 합니다.");
    }

    private String extractBearerToken(String authorization) {
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return token;
    }
}
