package com.bodeul.core;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationProfileConfigurationTests {

    @Test
    void migrationConnectionsUseTheMigrationOwnerRole() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        StandardEnvironment environment = new StandardEnvironment();

        loader.load("migration", new ClassPathResource("application-migration.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql"))
                .isEqualTo("SET ROLE bodeul_migration");
    }
}
