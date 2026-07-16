package com.bodeul.core.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class HospitalGuidesMigrationContractTests {

    @Test
    void hospitalGuidesMigrationKeepsTheReadModelInsideThePrivateSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/V2__create_hospital_guides.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("create table bodeul.hospital_guides")
                .contains("check (jsonb_typeof(steps) = 'array')")
                .contains("revoke all on table bodeul.hospital_guides from public, anon, authenticated, service_role")
                .contains("grant select on table bodeul.hospital_guides to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("alter table bodeul.hospital_guides enable row level security")
                .contains("to bodeul_core_runtime")
                .contains("to bodeul_admin_runtime")
                .doesNotContain("insert into")
                .doesNotContain("password");
    }
}
