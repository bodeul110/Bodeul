package com.bodeul.core.config;

import com.bodeul.core.auth.ApiErrorWriter;
import com.bodeul.core.auth.FirebaseAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FirebaseAuthenticationFilter firebaseAuthenticationFilter,
            ApiErrorWriter errorWriter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
                                response,
                                401,
                                "missing_authorization",
                                "Authorization 헤더가 필요합니다."))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(
                                response,
                                403,
                                "permission_denied",
                                "요청한 기능을 사용할 권한이 없습니다.")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/api/auth/me", "/api/places/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(firebaseAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                .build();
    }
}
