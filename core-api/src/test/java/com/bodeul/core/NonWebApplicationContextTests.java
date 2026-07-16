package com.bodeul.core;

import java.util.Map;

import com.bodeul.core.auth.FirebaseAuthenticationFilter;
import com.bodeul.core.auth.FirebaseTokenVerifier;
import com.bodeul.core.auth.AppCheckTokenVerifier;
import com.bodeul.core.auth.FirebaseAppCheckFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class NonWebApplicationContextTests {

    @Test
    void nonWebContextStartsWithoutServletAuthenticationBeans() {
        SpringApplication application = new SpringApplication(BodeulCoreApiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("local");
        application.setDefaultProperties(Map.of(
                "spring.main.banner-mode", "off",
                "spring.main.log-startup-info", "false"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBeansOfType(FirebaseAuthenticationFilter.class)).isEmpty();
            assertThat(context.getBeansOfType(FirebaseTokenVerifier.class)).isEmpty();
            assertThat(context.getBeansOfType(FirebaseAppCheckFilter.class)).isEmpty();
            assertThat(context.getBeansOfType(AppCheckTokenVerifier.class)).isEmpty();
        }
    }
}
