package com.bodeul.core.consent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianSharingConsentMigrationContractTests {

    @Test
    void migrationSeparatesCurrentStateAndAppendOnlyEventsWithCoreOnlyAccess() throws IOException {
        String sql = new ClassPathResource(
                "db/migration/V17__add_guardian_sharing_consents.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("create table bodeul.guardian_sharing_consents")
                .contains("create table bodeul.guardian_sharing_consent_events")
                .contains("unique (appointment_request_id)")
                .contains("adult_self_declared_at timestamptz not null")
                .contains("care_ended_at timestamptz")
                .contains("expiry_finalized boolean not null default false")
                .contains("expires_at = care_ended_at + interval '7 days'")
                .contains("not scopes @> '[\"ATTACHMENT\"]'::jsonb or scopes @> '[\"CHAT\"]'::jsonb")
                .contains("grant select, insert, update on table bodeul.guardian_sharing_consents")
                .contains("grant select, insert on table bodeul.guardian_sharing_consent_events")
                .contains("alter table bodeul.guardian_sharing_consents enable row level security")
                .doesNotContain("grant delete")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role");
    }

    @Test
    void realtimeBootstrapRejectsGuardianAndKeepsPatientManagerAccess() throws IOException {
        String sql = Files.readString(
                Path.of("db/bootstrap/005_guardian_sharing_realtime_authorization.sql"),
                StandardCharsets.UTF_8);
        String scenario = Files.readString(
                Path.of("db/verification/004_companion_realtime_authorization_scenarios.sql"),
                StandardCharsets.UTF_8);
        String bootstrapRollback = Files.readString(
                Path.of("db/bootstrap/rollback/005_guardian_sharing_realtime_authorization_rollback.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("app_user.id in (session.manager_user_id, appointment.patient_user_id)")
                .contains("security definer")
                .contains("set search_path = pg_catalog, pg_temp")
                .doesNotContain("appointment.guardian_user_id");
        assertThat(scenario)
                .contains("guardian_without_consent_denied")
                .contains("consented_guardian_broadcast_denied")
                .contains("revoked_guardian_denied");
        assertThat(bootstrapRollback)
                .contains("app_user.id in (session.manager_user_id, appointment.patient_user_id)")
                .doesNotContain("appointment.guardian_user_id");
    }

    @Test
    void rollbackAndReadOnlyVerificationCoverConsentObjects() throws IOException {
        String rollback = Files.readString(
                Path.of("db/rollback/V17__remove_guardian_sharing_consents.sql"),
                StandardCharsets.UTF_8);
        String verification = Files.readString(
                Path.of("db/verification/012_guardian_sharing_consent_checks.sql"),
                StandardCharsets.UTF_8);

        assertThat(rollback)
                .contains("guardian_sharing_consent_events")
                .contains("guardian_sharing_consents")
                .contains("errcode = '55000'")
                .contains("guardian sharing consent data must be exported and removed before schema rollback")
                .containsSubsequence(
                        "drop table bodeul.guardian_sharing_consent_events",
                        "drop table bodeul.guardian_sharing_consents",
                        "drop table bodeul.guardian_sharing_consent_settings");
        assertThat(verification)
                .contains("begin transaction read only")
                .contains("adult_self_declared_at")
                .contains("care_ended_at")
                .contains("expiry_finalized")
                .contains("not location_sharing_enabled")
                .contains("rollback;");
    }

    @Test
    void disposablePostgresPathAppliesExercisesAndSafelyRollsBackV17() throws IOException {
        String script = Files.readString(
                Path.of("db/verification/verify_guardian_sharing_consent_migration.sh"),
                StandardCharsets.UTF_8);
        String cancellationScenario = Files.readString(
                Path.of("db/verification/013_guardian_sharing_consent_cancellation_scenario.sql"),
                StandardCharsets.UTF_8);
        String rollbackChecks = Files.readString(
                Path.of("db/verification/014_guardian_sharing_consent_rollback_checks.sql"),
                StandardCharsets.UTF_8);
        String workflow = Files.readString(
                Path.of("../.github/workflows/core-api.yml"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains("./gradlew migrateDatabase")
                .contains("012_guardian_sharing_consent_checks.sql")
                .contains("013_guardian_sharing_consent_cancellation_scenario.sql")
                .contains("005_guardian_sharing_realtime_authorization.sql")
                .contains("004_companion_realtime_authorization_scenarios.sql")
                .contains("005_guardian_sharing_realtime_authorization_rollback.sql")
                .contains("if psql")
                .contains("V17__remove_guardian_sharing_consents.sql")
                .contains("pg_dump")
                .contains("014_guardian_sharing_consent_rollback_checks.sql");
        assertThat(cancellationScenario)
                .contains("status in ('REQUESTED', 'MATCHED')")
                .contains("current_status = 'CANCELED'")
                .contains("care_ended_at = session.canceled_at")
                .contains("expires_at = session.canceled_at + interval '7 days'")
                .contains("rollback;");
        assertThat(rollbackChecks)
                .contains("to_regclass('bodeul.guardian_sharing_consents') is null")
                .contains("appointment.guardian_user_id")
                .contains("rollback;");
        assertThat(workflow)
                .contains("image: postgres:17")
                .contains("bash db/verification/verify_guardian_sharing_consent_migration.sh");
    }
}
