package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionCompletionMigrationContractTests {

    @Test
    void migrationSeparatesCareEndCompletionAndReportRetryState() throws IOException {
        String sql = read("src/main/resources/db/migration/"
                + "V18__separate_companion_care_completion.sql");

        assertThat(sql)
                .contains("'care_ended'")
                .contains("care_ended_at = coalesce")
                .contains("completed_at = coalesce")
                .contains("manager_journal text not null default ''")
                .contains("char_length(manager_journal) <= 300")
                .contains("'not_requested', 'pending', 'ready', 'failed'")
                .contains("create table bodeul.companion_session_artifacts")
                .contains("create table bodeul.companion_session_artifact_operations")
                .contains("'payment_evidence', 'prescription_image'")
                .contains("purpose = 'payment_evidence' and item_order = 0")
                .contains("purpose = 'prescription_image' and item_order between 0 and 2")
                .contains("uq_companion_session_artifacts_session_purpose_item")
                .contains("unique (companion_session_id, purpose, item_order)")
                .contains("size_bytes > 0 and size_bytes <= 10485760")
                .contains("sha256 ~ '^[0-9a-f]{64}$'")
                .contains("payload_fingerprint ~ '^[0-9a-f]{64}$'")
                .contains("on delete cascade")
                .contains("on delete set null");
        assertThat(sql.indexOf("update bodeul.companion_sessions as session"))
                .isLessThan(sql.indexOf("ck_companion_sessions_completion_timestamps"));
    }

    @Test
    void rollbackRemovesArtifactsBeforeCompletionColumns() throws IOException {
        String sql = read("db/rollback/V18__merge_companion_care_completion.sql");

        assertThat(sql)
                .contains("v18 rollback을 중단합니다")
                .contains("select 1 from bodeul.companion_session_artifacts")
                .contains("from bodeul.companion_session_artifact_operations")
                .contains("drop table if exists bodeul.companion_session_artifact_operations")
                .contains("drop table if exists bodeul.companion_session_artifacts")
                .contains("where current_status = 'care_ended'")
                .contains("drop column report_generation_status")
                .contains("drop column manager_journal")
                .contains("drop column care_ended_at");
        assertThat(sql.indexOf("select 1 from bodeul.companion_session_artifacts"))
                .isLessThan(sql.indexOf("drop column care_ended_at"));
    }

    @Test
    void verificationExercisesSlotUniquenessWithDifferentRequestIds() throws IOException {
        String sql = read("db/verification/013_companion_completion_checks.sql");

        assertThat(sql)
                .contains("40000000-0000-0000-0000-000000000001")
                .contains("40000000-0000-0000-0000-000000000002")
                .contains("서로 다른 요청 uuid의 payment_evidence item 0 중복")
                .contains("네 번째 prescription_image")
                .contains("exception when unique_violation");
    }

    private String read(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8).toLowerCase();
    }
}
