package com.bodeul.core.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentFollowUpWriteMigrationContractTests {

    @Test
    void coreRoleReceivesOnlyRequiredFollowUpColumns() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("grant insert (")
                .contains("grant update (")
                .contains("on table bodeul.appointment_follow_ups to bodeul_core_runtime")
                .contains("create policy appointment_follow_ups_core_insert")
                .contains("create policy appointment_follow_ups_core_update")
                .doesNotContain("grant delete")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role")
                .doesNotContain("to bodeul_admin_runtime");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V7__enable_core_appointment_follow_up_writes.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }
}
