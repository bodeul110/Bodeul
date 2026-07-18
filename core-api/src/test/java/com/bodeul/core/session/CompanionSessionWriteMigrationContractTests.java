package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionSessionWriteMigrationContractTests {

    @Test
    void coreRoleReceivesOnlyRequiredSessionAndReportColumns() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("grant update (")
                .contains("on table bodeul.companion_sessions to bodeul_core_runtime")
                .contains("on table bodeul.session_reports to bodeul_core_runtime")
                .contains("create policy companion_sessions_core_update")
                .contains("create policy session_reports_core_insert")
                .contains("create policy session_reports_core_update")
                .doesNotContain("grant delete")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role")
                .doesNotContain("to bodeul_admin_runtime");
    }

    @Test
    void foreignKeysReportedByAdvisorReceiveCoveringIndexes() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("ix_appointment_follow_ups_review_actor")
                .contains("ix_appointment_follow_ups_settlement_actor")
                .contains("ix_appointment_follow_ups_support_actor")
                .contains("ix_assignment_audits_session")
                .contains("ix_assignment_audits_previous_manager")
                .contains("ix_assignment_audits_assigned_manager")
                .contains("ix_assignment_audits_actor_admin");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V6__enable_core_companion_session_writes.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }
}
