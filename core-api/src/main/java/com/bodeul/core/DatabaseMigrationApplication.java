package com.bodeul.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class DatabaseMigrationApplication {

    private DatabaseMigrationApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BodeulCoreApiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("migration");

        try (ConfigurableApplicationContext ignored = application.run(args)) {
            // Flyway 초기화가 끝나면 DataSource와 application context를 즉시 닫는다.
        }
    }
}
