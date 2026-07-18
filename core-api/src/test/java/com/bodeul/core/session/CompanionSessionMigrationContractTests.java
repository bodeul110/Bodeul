package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionSessionMigrationContractTests {

    @Test
    void migrationCreatesPrivateOperationalTablesWithStateConstraints() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table bodeul.companion_sessions")
                .contains("unique (appointment_request_id)")
                .contains("references bodeul.appointment_requests (id)")
                .contains("references bodeul.app_users (id)")
                .contains("'READY', 'MEETING', 'WAITING', 'IN_TREATMENT'")
                .contains("create table bodeul.session_reports")
                .contains("unique (companion_session_id)")
                .contains("next_visit_at timestamptz")
                .contains("next_visit_note text not null default ''")
                .contains("create table bodeul.appointment_follow_ups")
                .contains("create table bodeul.companion_session_assignment_audits")
                .contains("alter table bodeul.companion_sessions enable row level security")
                .contains("alter table bodeul.session_reports enable row level security")
                .contains("alter table bodeul.appointment_follow_ups enable row level security")
                .contains("alter table bodeul.companion_session_assignment_audits enable row level security");
    }

    @Test
    void adminAssignmentUsesOneValidatedFunctionInsteadOfBroadTableWrites() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.assign_companion_session")
                .contains("security definer")
                .contains("where id = p_actor_admin_user_id and role = 'ADMIN'")
                .contains("where id = p_manager_user_id and role = 'MANAGER'")
                .contains("where id = p_appointment_request_id")
                .contains("for update")
                .contains("v_appointment_status <> 'REQUESTED'")
                .contains("v_appointment_version <> p_expected_appointment_version")
                .contains("status = 'MATCHED'")
                .contains("grant execute on function bodeul.assign_companion_session")
                .doesNotContain("grant insert on table bodeul.companion_sessions to bodeul_admin_runtime")
                .doesNotContain("grant update on table bodeul.appointment_requests to bodeul_admin_runtime")
                .doesNotContain("password");
    }

    @Test
    void publicAndSupabaseClientRolesReceiveNoTableWrites() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("revoke all on table bodeul.companion_sessions from public, anon, authenticated, service_role")
                .contains("grant select on table bodeul.companion_sessions to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("grant select on table bodeul.session_reports to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("grant select on table bodeul.appointment_follow_ups to bodeul_core_runtime, bodeul_admin_runtime")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V5__create_companion_session_operational_schema.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }
}
