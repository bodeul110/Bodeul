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
                .contains("create table bodeul.companion_completion_v18_baseline")
                .contains("create table bodeul.companion_completion_v18_chat_expiry_baseline")
                .contains("create table bodeul.companion_completion_v18_attachment_expiry_baseline")
                .contains("create table bodeul.companion_completion_v18_location_expiry_baseline")
                .contains("create table bodeul.companion_completion_v18_consent_expiry_baseline")
                .contains("original_completed_at")
                .contains("expected_care_ended_at")
                .contains("expected_report_generation_status")
                .contains("on delete cascade")
                .contains("from bodeul.companion_completion_v18_baseline as baseline")
                .contains("original_expires_at")
                .contains("original_revoked_by_user_id")
                .contains("original_version")
                .contains("expected_expiry_finalized")
                .contains("expected_version")
                .contains("expected_updated_at")
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
                .contains("on delete set null")
                .contains("create or replace function bodeul.record_companion_location")
                .contains("select session.manager_user_id, session.current_status, session.care_ended_at")
                .contains("for update")
                .contains("v_care_ended_at is not null")
                .contains("v_session_status in ('care_ended', 'completed', 'canceled')")
                .contains("create function bodeul.guard_companion_chat_message_write")
                .contains("guard_companion_chat_message_write_before_insert")
                .contains("create function bodeul.guard_companion_chat_attachment_write")
                .contains("guard_companion_chat_attachment_write_before_insert")
                .contains("create or replace function bodeul.schedule_companion_realtime_expiry")
                .contains("create or replace function bodeul.broadcast_companion_realtime_change")
                .contains("session.care_ended_at is not null")
                .contains("create function bodeul.guard_guardian_consent_care_boundary")
                .contains("for update")
                .contains("guard_guardian_consent_care_boundary_before_write")
                .contains("새 보호자 정보공유 동의는 종료 전 활성 상태로만 만들 수 있습니다")
                .contains("동행 종료 전에는 보호자 동의 만료를 확정할 수 없습니다")
                .contains("보호자 정보공유 동의 철회는 환자가 최초 한 번만 기록")
                .contains("create function bodeul.finalize_guardian_consent_after_care_boundary")
                .contains("finalize_guardian_consent_after_care_boundary_update")
                .contains("v_care_boundary + interval '7 days'")
                .contains("v_retention_started_at := new.care_ended_at")
                .contains("after update of current_status, care_ended_at")
                .contains("v_retention_started_at + interval '180 days'")
                .contains("v_retention_started_at + interval '30 days'")
                .contains("v_retention_started_at + interval '24 hours'");
        assertThat(sql.indexOf("after update of current_status, care_ended_at"))
                .isLessThan(sql.indexOf("update bodeul.companion_sessions as session"));
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
                .contains("from bodeul.companion_completion_v18_baseline as baseline")
                .contains("session.care_ended_at is distinct from baseline.expected_care_ended_at")
                .contains("not exists")
                .contains("session.current_status = 'care_ended'")
                .contains("session.care_ended_at is not null")
                .contains("btrim(session.manager_journal) <> ''")
                .contains("session.report_generation_status <> 'not_requested'")
                .contains("session.report_generation_attempts <> 0")
                .contains("btrim(session.report_generation_last_error) <> ''")
                .contains("session.report_generation_updated_at is not null")
                .contains("drop table if exists bodeul.companion_session_artifact_operations")
                .contains("drop table if exists bodeul.companion_session_artifacts")
                .contains("set completed_at = baseline.original_completed_at")
                .contains("set expires_at = baseline.original_expires_at")
                .contains("consent.version is distinct from baseline.expected_version")
                .contains("migration 이후 보호자 동의가 변경")
                .contains("version = baseline.original_version")
                .contains("updated_at = baseline.original_updated_at")
                .contains("migration 이전 실시간 데이터가 이미 삭제")
                .contains("drop table bodeul.companion_completion_v18_location_expiry_baseline")
                .contains("drop table bodeul.companion_completion_v18_attachment_expiry_baseline")
                .contains("drop table bodeul.companion_completion_v18_chat_expiry_baseline")
                .contains("drop table bodeul.companion_completion_v18_consent_expiry_baseline")
                .contains("drop table bodeul.companion_completion_v18_baseline")
                .contains("begin;")
                .contains("commit;")
                .contains("drop column report_generation_status")
                .contains("drop column manager_journal")
                .contains("drop column care_ended_at")
                .contains("create or replace function bodeul.record_companion_location")
                .contains("session.current_status not in ('completed', 'canceled')")
                .contains("drop function if exists bodeul.guard_companion_chat_attachment_write")
                .contains("drop function if exists bodeul.guard_companion_chat_message_write")
                .contains("drop function if exists bodeul.guard_guardian_consent_care_boundary")
                .contains("drop function if exists bodeul.finalize_guardian_consent_after_care_boundary")
                .contains("create or replace function bodeul.schedule_companion_realtime_expiry")
                .contains("create or replace function bodeul.broadcast_companion_realtime_change")
                .contains("after update of current_status on bodeul.companion_sessions");
        assertThat(sql.indexOf("select 1 from bodeul.companion_session_artifacts"))
                .isLessThan(sql.indexOf("drop column care_ended_at"));
        assertThat(sql.indexOf("after update of current_status on bodeul.companion_sessions"))
                .isLessThan(sql.indexOf("drop column care_ended_at"));
        assertThat(sql.indexOf("begin;"))
                .isLessThan(sql.indexOf("drop table if exists bodeul.companion_session_artifacts"));
        assertThat(sql.indexOf("commit;"))
                .isGreaterThan(sql.indexOf("drop table bodeul.companion_completion_v18_baseline"));
    }

    @Test
    void verificationExercisesSlotUniquenessWithDifferentRequestIds() throws IOException {
        String sql = read("db/verification/013_companion_completion_checks.sql");

        assertThat(sql)
                .contains("40000000-0000-0000-0000-000000000001")
                .contains("40000000-0000-0000-0000-000000000002")
                .contains("서로 다른 요청 uuid의 payment_evidence item 0 중복")
                .contains("네 번째 prescription_image")
                .contains("care_ended_at 이후 채팅 저장이 허용되었습니다")
                .contains("care_ended_at 이후 첨부 저장이 허용되었습니다")
                .contains("care_ended_at 이후 위치 저장이 허용되었습니다")
                .contains("completed 전환이 care_ended 기준 보존 시각을 덮어썼습니다")
                .contains("care_ended 뒤 보호자 동의 재부여가 허용되었습니다")
                .contains("확정된 보호자 동의 만료 경계 초기화가 허용되었습니다")
                .contains("종료 뒤 보호자 동의 철회가 반영되지 않았습니다")
                .contains("새 동의를 확정 상태로 직접 생성할 수 있습니다")
                .contains("진행 중 동의 만료의 조기 확정이 허용되었습니다")
                .contains("확정된 동의 버전의 임의 변경이 허용되었습니다")
                .contains("확정된 보호자 동의 철회 해제가 허용되었습니다")
                .contains("exception when unique_violation");
    }

    @Test
    void rollbackVerificationConfirmsRealtimeGuardsAndFunctionsAreRestored() throws IOException {
        String sql = read("db/verification/014_companion_completion_rollback_checks.sql");

        assertThat(sql)
                .contains("guard_companion_chat_message_write")
                .contains("guard_companion_chat_attachment_write")
                .contains("pg_get_functiondef")
                .contains("bodeul.record_companion_location")
                .contains("bodeul.schedule_companion_realtime_expiry")
                .contains("update of current_status on")
                .contains("care_ended_at")
                .contains("보호자 동의 만료 상태가 복원되지 않았습니다")
                .contains("realtime 권한 helper 설명이 rollback 뒤 복원되지 않았습니다")
                .contains("migration 전 실시간 데이터의 만료시각이 복원되지 않았습니다");
    }

    @Test
    void privilegedRealtimeBootstrapRevokesManagerAfterCareEndAndHasRollback() throws IOException {
        String bootstrap = read("db/bootstrap/006_companion_completion_realtime_authorization.sql");
        String rollback = read(
                "db/bootstrap/rollback/006_companion_completion_realtime_authorization_rollback.sql");
        String scenario = read(
                "db/verification/015_companion_completion_realtime_authorization_scenarios.sql");
        String script = read("db/verification/verify_companion_completion_migration.sh");

        assertThat(bootstrap)
                .contains("session.care_ended_at is null")
                .contains("session.current_status not in ('care_ended', 'completed', 'canceled')")
                .contains("app_user.id = appointment.patient_user_id")
                .doesNotContain("appointment.guardian_user_id");
        assertThat(rollback)
                .contains("app_user.id in (session.manager_user_id, appointment.patient_user_id)")
                .contains("보호자 broadcast는 연결 권한 캐시 위험 때문에 거부")
                .doesNotContain("care_ended_at");
        assertThat(scenario)
                .contains("active_manager_allowed")
                .contains("care_ended_manager_denied")
                .contains("retained_patient_allowed")
                .contains("guardian_denied");
        assertThat(script)
                .contains("005_guardian_sharing_realtime_authorization.sql")
                .contains("006_companion_completion_realtime_authorization.sql")
                .contains("015_companion_completion_realtime_authorization_scenarios.sql")
                .contains("006_companion_completion_realtime_authorization_rollback.sql")
                .contains("expect_rollback_failure \"보호자 동의 변경\"");
        assertThat(script.indexOf("005_guardian_sharing_realtime_authorization.sql"))
                .isLessThan(script.indexOf("006_companion_completion_realtime_authorization.sql"));
    }

    private String read(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8).toLowerCase();
    }
}
