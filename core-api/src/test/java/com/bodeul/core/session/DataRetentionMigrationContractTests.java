package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class DataRetentionMigrationContractTests {

    @Test
    void bootstrapSeparatesRetentionCredentialsFromApplicationRoles() throws IOException {
        String sql = fileSql("db/bootstrap/004_retention_runtime.sql");

        assertThat(sql)
                .contains("create role bodeul_retention_runtime nologin noinherit")
                .contains("create role bodeul_retention_service nologin inherit connection limit 2")
                .contains("grant bodeul_retention_runtime to bodeul_retention_service")
                .contains("grant usage on schema bodeul to bodeul_retention_runtime")
                .doesNotContain("password");
    }

    @Test
    void migrationExposesOnlyNarrowRetentionFunctions() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.preview_expired_companion_data")
                .contains("create function bodeul.claim_expired_companion_attachments")
                .contains("create function bodeul.mark_companion_attachment_deleted")
                .contains("create function bodeul.purge_expired_companion_records")
                .contains("security definer")
                .contains("set search_path = pg_catalog, pg_temp")
                .contains("to bodeul_retention_runtime")
                .doesNotContain("grant select on table bodeul.companion_chat_messages to bodeul_retention_runtime")
                .doesNotContain("grant update on table bodeul.companion_chat_attachments to bodeul_retention_runtime")
                .doesNotContain("grant delete on table bodeul.companion_session_locations to bodeul_retention_runtime");
    }

    @Test
    void legalHoldAndStorageOrderingArePartOfTheDatabaseContract() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("attachment.legal_hold_until is null or attachment.legal_hold_until <= p_as_of")
                .contains("location.legal_hold_until is null or location.legal_hold_until <= p_as_of")
                .contains("message.legal_hold_until is null or message.legal_hold_until <= p_as_of")
                .contains("attachment.storage_path = p_expected_storage_path")
                .contains("set status = 'DELETE_PENDING'")
                .contains("status in ('ACTIVE', 'DELETE_PENDING')")
                .contains("attachment.expires_at <= p_deleted_at")
                .contains("p_as_of > now() + interval '5 minutes'")
                .contains("p_deleted_at > now() + interval '5 minutes'")
                .contains("set status = 'DELETED'")
                .contains("storage_path = 'deleted/' || attachment.id::text")
                .contains("set body = '', deleted_at = p_as_of")
                .contains("delete from bodeul.companion_session_locations");
    }

    @Test
    void executionHistoryContainsCountsWithoutRawPersonalData() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table bodeul.retention_job_runs")
                .contains("manager_document_delete_failures")
                .contains("failure_stage text")
                .contains("create function bodeul.retention_monthly_summary")
                .doesNotContain("firebase_uid")
                .doesNotContain("body text");
    }

    @Test
    void rollbackRemovesFunctionsBeforeTheHistoryTable() throws IOException {
        String sql = fileSql("db/rollback/V13__remove_data_retention_job.sql");

        assertThat(sql)
                .contains("drop function bodeul.retention_monthly_summary")
                .contains("drop function bodeul.preview_expired_companion_data")
                .contains("drop table bodeul.retention_job_runs");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V13__add_data_retention_job.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }

    private String fileSql(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
