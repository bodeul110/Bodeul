package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionRealtimeMigrationContractTests {

    @Test
    void migrationNormalizesChatLocationAndReadState() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table bodeul.companion_chat_messages")
                .contains("unique (companion_session_id, client_message_id)")
                .contains("check (char_length(body) <= 2000)")
                .contains("create table bodeul.companion_chat_attachments")
                .contains("check (content_type in ('image/jpeg', 'image/png', 'application/pdf'))")
                .contains("check (size_bytes >= 0 and size_bytes <= 10485760)")
                .contains("create table bodeul.companion_chat_read_receipts")
                .contains("foreign key (companion_session_id, last_read_message_id)")
                .contains("create table bodeul.companion_session_locations")
                .contains("check (latitude between -90.0 and 90.0)")
                .contains("check (longitude between -180.0 and 180.0)");
    }

    @Test
    void locationWritesAreDeduplicatedAndKeepOnlyRecentTenRows() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.record_companion_location")
                .contains("security definer")
                .contains("session.manager_user_id = p_manager_user_id")
                .contains("session.current_status not in ('COMPLETED', 'CANCELED')")
                .contains("on conflict (companion_session_id, client_location_id) do nothing")
                .contains("order by recent.captured_at desc, recent.id desc")
                .contains("limit 10")
                .contains("grant execute on function bodeul.record_companion_location")
                .doesNotContain("grant insert on table bodeul.companion_session_locations");
    }

    @Test
    void terminalSessionSchedulesApprovedRetentionPeriods() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.schedule_companion_realtime_expiry")
                .contains("after update of current_status on bodeul.companion_sessions")
                .contains("v_finished_at + interval '180 days'")
                .contains("v_finished_at + interval '30 days'")
                .contains("v_finished_at + interval '24 hours'")
                .contains("legal_hold_until");
    }

    @Test
    void publicRolesCannotReadOrWriteRealtimeTables() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("revoke all on table bodeul.companion_chat_messages from public, anon, authenticated, service_role")
                .contains("revoke all on table bodeul.companion_session_locations from public, anon, authenticated, service_role")
                .contains("grant select on table bodeul.companion_chat_messages to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("on table bodeul.companion_chat_messages to bodeul_core_runtime")
                .contains("on table bodeul.companion_chat_read_receipts to bodeul_core_runtime")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role")
                .doesNotContain("grant delete");
    }

    @Test
    void rollbackRemovesTriggersFunctionsAndTablesInDependencyOrder() throws IOException {
        String sql = rollbackSql();

        assertThat(sql)
                .contains("drop trigger if exists schedule_companion_realtime_expiry_after_session_end")
                .contains("drop function if exists bodeul.schedule_companion_realtime_expiry")
                .contains("drop function if exists bodeul.record_companion_location")
                .contains("drop table if exists bodeul.companion_session_locations")
                .contains("drop table if exists bodeul.companion_chat_read_receipts")
                .contains("drop table if exists bodeul.companion_chat_attachments")
                .contains("drop table if exists bodeul.companion_chat_messages");
    }

    @Test
    void followUpMigrationCoversTheCompositeReadReceiptForeignKey() throws IOException {
        String migration = classpathSql(
                "db/migration/V9__cover_companion_chat_read_receipt_fk.sql"
        );
        String rollback = fileSql(
                "V9__restore_chat_read_receipt_message_index.sql"
        );

        assertThat(migration)
                .contains("drop index if exists bodeul.ix_chat_read_receipts_message")
                .contains("create index ix_chat_read_receipts_session_message")
                .contains("companion_session_id")
                .contains("last_read_message_id");
        assertThat(rollback)
                .contains("drop index if exists bodeul.ix_chat_read_receipts_session_message")
                .contains("create index ix_chat_read_receipts_message")
                .contains("on bodeul.companion_chat_read_receipts (last_read_message_id)");
    }

    private String migrationSql() throws IOException {
        return classpathSql("db/migration/V8__create_companion_realtime_schema.sql");
    }

    private String rollbackSql() throws IOException {
        return fileSql("V8__drop_companion_realtime_schema.sql");
    }

    private String classpathSql(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private String fileSql(String fileName) throws IOException {
        Path rollbackPath = Path.of("db", "rollback", fileName);
        if (Files.notExists(rollbackPath)) {
            rollbackPath = Path.of("core-api").resolve(rollbackPath);
        }
        return Files.readString(rollbackPath, StandardCharsets.UTF_8);
    }
}
