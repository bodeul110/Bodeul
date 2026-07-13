package com.bodeul.core.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AppUsersMigrationContractTests {

    @Test
    void appUsersMigrationUsesPrivateSchemaAndReadOnlyRuntimeGrant() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/V1__create_app_users.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("create table bodeul.app_users")
                .contains("firebase_uid text not null")
                .contains("grant select on table bodeul.app_users to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("alter table bodeul.app_users enable row level security")
                .contains("to bodeul_core_runtime")
                .contains("to bodeul_admin_runtime")
                .doesNotContain("password")
                .doesNotContain("service_role key");
    }
}
