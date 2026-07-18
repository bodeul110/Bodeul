package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionLiveStateMigrationContractTests {

    @Test
    void liveLocationAndAlertStateMovesToPostgresWithNarrowRuntimeGrant() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V12__move_companion_live_state_to_postgres.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("add column live_location_sharing_active boolean not null default false")
                .contains("add column live_location_sharing_started_at timestamptz")
                .contains("add column location_alert_stage text not null default 'none'")
                .contains("check (location_alert_stage in ('none', 'hospital_near', 'pharmacy_near'))")
                .contains("on table bodeul.companion_sessions to bodeul_core_runtime")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role");
    }

    @Test
    void rollbackRemovesOnlyNewLiveStateColumns() throws IOException {
        String rollback = Files.readString(
                Path.of("db", "rollback", "V12__restore_companion_live_state_to_firestore.sql"),
                StandardCharsets.UTF_8);

        assertThat(rollback)
                .contains("revoke update (")
                .contains("drop column if exists live_location_sharing_active")
                .doesNotContain("drop table");
    }
}
