package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionRealtimeBroadcastMigrationContractTests {

    @Test
    void migrationBroadcastsOnlyMinimalPrivateChangeSignals() throws IOException {
        String migration = read("db/migration/V10__broadcast_companion_realtime_changes.sql");

        assertThat(migration)
                .contains("to_regprocedure('realtime.send(jsonb,text,text,boolean)')")
                .contains("'sessionId', v_session_id::text")
                .contains("'resource', v_resource")
                .contains("'recordId', v_record_id::text")
                .contains("'companion-session:' || v_session_id::text, true")
                .contains("when others then")
                .doesNotContain("new.body")
                .doesNotContain("new.latitude")
                .doesNotContain("new.longitude")
                .doesNotContain("new.storage_path");
    }

    @Test
    void migrationCoversMessageReceiptAndLocationChanges() throws IOException {
        String migration = read("db/migration/V10__broadcast_companion_realtime_changes.sql");

        assertThat(migration)
                .contains("after insert on bodeul.companion_chat_messages")
                .contains("after insert or update on bodeul.companion_chat_read_receipts")
                .contains("after insert on bodeul.companion_session_locations")
                .contains("'chat.changed'")
                .contains("'read-receipt.changed'")
                .contains("'location.changed'");
    }

    @Test
    void rollbackRemovesEveryBroadcastTriggerAndFunction() throws IOException {
        String rollback = readRollback("V10__drop_companion_realtime_broadcast.sql");

        assertThat(rollback)
                .contains("drop trigger if exists broadcast_companion_location_after_insert")
                .contains("drop trigger if exists broadcast_companion_read_receipt_after_change")
                .contains("drop trigger if exists broadcast_companion_chat_message_after_insert")
                .contains("drop function if exists bodeul.broadcast_companion_realtime_change()");
    }

    @Test
    void privilegedBootstrapGrantsOnlyRealtimeSchemaUsageWhenAvailable() throws IOException {
        String bootstrap = readPath("db/bootstrap/001_database_access.sql");
        String rollback = readPath("db/bootstrap/rollback/001_database_access_rollback.sql");

        assertThat(bootstrap)
                .contains("to_regprocedure('realtime.send(jsonb,text,text,boolean)')")
                .contains("grant usage on schema realtime to bodeul_migration")
                .doesNotContain("grant insert on table realtime.messages to bodeul_migration");
        assertThat(rollback)
                .contains("revoke usage on schema realtime from bodeul_migration");
    }

    private String read(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("테스트 리소스를 찾을 수 없습니다: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readRollback(String fileName) throws IOException {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("db", "rollback", fileName),
                StandardCharsets.UTF_8);
    }

    private String readPath(String path) throws IOException {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of(path),
                StandardCharsets.UTF_8);
    }
}
