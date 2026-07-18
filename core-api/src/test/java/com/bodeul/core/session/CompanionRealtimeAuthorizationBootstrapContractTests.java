package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionRealtimeAuthorizationBootstrapContractTests {

    @Test
    void privateBroadcastPolicyChecksFirebaseProjectAndSessionMembership() throws IOException {
        String bootstrap = read("db/bootstrap/003_companion_realtime_authorization.sql");

        assertThat(bootstrap)
                .contains("v_claims ->> 'role' <> 'authenticated'")
                .contains("https://securetoken.google.com/")
                .contains("realtime.topic()")
                .contains("appointment.patient_user_id")
                .contains("appointment.guardian_user_id")
                .contains("session.manager_user_id")
                .contains("realtime.messages.extension = 'broadcast'")
                .doesNotContain("for insert\nto authenticated");
    }

    @Test
    void helperDoesNotExposeBusinessTablesToAuthenticatedRole() throws IOException {
        String bootstrap = read("db/bootstrap/003_companion_realtime_authorization.sql");

        assertThat(bootstrap)
                .contains("security definer")
                .contains("set search_path = pg_catalog, pg_temp")
                .contains("revoke all on table bodeul_realtime_auth.allowed_firebase_projects")
                .contains("grant execute on function bodeul_realtime_auth.can_receive_companion_broadcast()")
                .doesNotContain("grant select on bodeul.")
                .doesNotContain("grant usage on schema bodeul to authenticated");
    }

    @Test
    void environmentFilesTrustOnlyTheirOwnFirebaseProject() throws IOException {
        String development = read(
                "db/bootstrap/environment/development_companion_realtime_authorization.sql");
        String production = read(
                "db/bootstrap/environment/production_companion_realtime_authorization.sql");

        assertThat(development)
                .contains("values ('bodeul-dev')")
                .contains("where project_id <> 'bodeul-dev'")
                .doesNotContain("bodeul-prod-110");
        assertThat(production)
                .contains("values ('bodeul-prod-110')")
                .contains("where project_id <> 'bodeul-prod-110'")
                .doesNotContain("values ('bodeul-dev')");
    }

    @Test
    void rollbackRemovesPolicyBeforePrivateSchema() throws IOException {
        String rollback = read(
                "db/bootstrap/rollback/003_companion_realtime_authorization_rollback.sql");

        assertThat(rollback.indexOf("drop policy"))
                .isLessThan(rollback.indexOf("drop schema"));
        assertThat(rollback)
                .contains("drop function if exists bodeul_realtime_auth.can_receive_companion_broadcast()")
                .contains("drop table if exists bodeul_realtime_auth.allowed_firebase_projects");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
