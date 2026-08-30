package com.bodeul.core.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRbacMigrationContractTests {

    @Test
    void migrationAddsThreeRolesExpiringBreakGlassAndAppendOnlyAudit() throws IOException {
        String sql = new ClassPathResource(
                "db/migration/V20__add_admin_rbac_and_access_audit.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql)
                .contains("admin_role in ('super_admin', 'operations', 'developer')")
                .contains("admin_user_id <> approved_by_admin_user_id")
                .contains("expires_at <= granted_at + interval '60 minutes'")
                .contains("create unique index ux_admin_break_glass_unrevoked")
                .contains("create table bodeul.admin_access_audits")
                .contains("create function bodeul.resolve_admin_authorization")
                .contains("create function bodeul.record_admin_access_audit")
                .contains("create unique index ux_admin_access_audits_operation")
                .contains("같은 감사 작업 id를 다른 내용으로 재사용할 수 없습니다.")
                .contains("p_metadata ->> 'actoradminrole'")
                .contains("v_is_manager_review_outbox")
                .contains("심사 감사의 관리자 역할이 필요합니다.")
                .contains("심사 감사의 관리자 역할이 올바르지 않습니다.")
                .contains("v_outbox_admin_role is distinct from v_admin_role")
                .contains("심사 감사의 관리자 역할이 현재 활성 역할과 일치하지 않습니다.")
                .contains("심사 감사의 payload hash가 올바르지 않습니다.")
                .contains("p_action = 'update'")
                .contains("p_resource_type = 'manager_review'")
                .contains("p_outcome = 'allowed'")
                .contains("create function bodeul.preview_expired_admin_access_audits")
                .contains("create function bodeul.purge_expired_admin_access_audits")
                .contains("p_as_of - interval '1 year'")
                .contains("add column admin_audit_candidates integer not null default 0")
                .contains("add column admin_audits_deleted integer not null default 0")
                .contains("'adminauditcandidates'")
                .contains("'adminauditsdeleted'")
                .contains("coalesce((p_counts ->> 'adminauditcandidates')::integer, 0)")
                .contains("coalesce((p_counts ->> 'adminauditsdeleted')::integer, 0)")
                .contains("admin_audit_candidates bigint")
                .contains("admin_audits_deleted bigint")
                .contains("create function bodeul.set_admin_role_assignment")
                .contains("create function bodeul.revoke_admin_role_assignment")
                .contains("create function bodeul.grant_admin_break_glass")
                .contains("create function bodeul.revoke_admin_break_glass")
                .contains("perform pg_advisory_xact_lock(110349)")
                .contains("where id = p_grant_id")
                .contains("create function bodeul.list_admin_role_assignments")
                .contains("create function bodeul.list_admin_access_audits")
                .contains("security definer")
                .contains("set search_path = bodeul, pg_temp")
                .contains("grant execute on function bodeul.resolve_admin_authorization")
                .contains("to bodeul_admin_runtime")
                .contains("companion_assignment_admin_role_guard")
                .doesNotContain("grant select on table bodeul.admin_access_audits")
                .doesNotContain("to bodeul_core_runtime;")
                .doesNotContain("to authenticated;")
                .doesNotContain("to service_role;");
    }

    @Test
    void migrationFailsClosedForMissingRoleAndSensitiveAccess() throws IOException {
        String sql = new ClassPathResource(
                "db/migration/V20__add_admin_rbac_and_access_audit.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql)
                .contains("활성 관리자 세부 역할이 필요합니다.")
                .contains("p_action = 'raw_view'")
                .contains("p_action = 'download'")
                .contains("v_admin_role <> 'super_admin'")
                .contains("유효한 긴급 접근 승인이 필요합니다.")
                .contains("마지막 최고 관리자 권한은 변경할 수 없습니다.")
                .contains("마지막 최고 관리자 권한은 회수할 수 없습니다.")
                .contains("pg_advisory_xact_lock(110349)")
                .contains("app_user.role = 'admin'");
    }

    @Test
    void rollbackRemovesOnlyV20ObjectsInDependencyOrder() throws IOException {
        String rollback = fileSql("db/rollback/V20__remove_admin_rbac_and_access_audit.sql")
                .toLowerCase();

        assertThat(rollback)
                .contains("set local role bodeul_migration;")
                .contains("if exists (select 1 from bodeul.admin_access_audits)")
                .contains("or exists (select 1 from bodeul.admin_break_glass_grants)")
                .contains("or exists (select 1 from bodeul.admin_role_assignments)")
                .contains("run.admin_audit_candidates <> 0")
                .contains("run.admin_audits_deleted <> 0")
                .contains("관리자 권한 또는 감사 이력이 남아 있어 v20 롤백을 중단합니다.")
                .contains("관리자 감사 파기 집계가 남아 있어 v20 롤백을 중단합니다.")
                .contains("drop trigger if exists companion_assignment_admin_role_guard")
                .contains("drop function if exists bodeul.enforce_assignment_actor_admin_role()")
                .contains("drop function bodeul.list_admin_access_audits(uuid, integer)")
                .contains("drop function bodeul.revoke_admin_break_glass(uuid, uuid, text)")
                .contains("drop function bodeul.purge_expired_admin_access_audits(timestamptz, integer)")
                .contains("drop function bodeul.preview_expired_admin_access_audits(timestamptz)")
                .contains("drop function bodeul.record_admin_access_audit(uuid, text, text, text, text, text, jsonb, uuid)")
                .contains("drop function bodeul.resolve_admin_authorization(text)")
                .contains("drop constraint ck_retention_job_runs_admin_audit_counts")
                .contains("drop column admin_audit_candidates")
                .contains("drop column admin_audits_deleted")
                .contains("'adminauditcandidates'")
                .contains("'adminauditsdeleted'")
                .contains("v20 롤백 뒤에는 관리자 감사 파기 집계를 저장할 수 없습니다.")
                .contains("drop table bodeul.admin_access_audits")
                .contains("drop table bodeul.admin_break_glass_grants")
                .contains("drop table bodeul.admin_role_assignments")
                .doesNotContain("drop table bodeul.app_users")
                .doesNotContain("delete from");

        assertThat(rollback.stripLeading()).startsWith("begin;");
        assertThat(rollback.stripTrailing()).endsWith("commit;");

        assertThat(fileSql("db/verification/013_admin_rbac_rollback_checks.sql").toLowerCase())
                .contains("관리자 rbac 테이블이 롤백 후 남아 있습니다.")
                .contains("관리자 rbac 함수가 롤백 후 남아 있습니다.")
                .contains("동행 배정 관리자 역할 트리거가 롤백 후 남아 있습니다.")
                .contains("관리자 감사 파기 집계 컬럼이 롤백 후 남아 있습니다.")
                .contains("v20 functions 집계 키를 포함한 롤백 호환 완료 기록에 실패했습니다.")
                .contains("롤백 뒤 0이 아닌 관리자 감사 파기 집계가 거부되지 않았습니다.");
    }

    @Test
    void rollbackFailureVerificationChecksTransactionAtomicity() throws IOException {
        String failureFixture = fileSql(
                "db/verification/014_admin_rbac_rollback_failure_fixture.sql"
        ).toLowerCase();
        String atomicityVerification = fileSql(
                "db/verification/015_admin_rbac_rollback_atomicity_checks.sql"
        ).toLowerCase();
        String runner = fileSql(
                "db/verification/verify_companion_guide_snapshot_migration.sh"
        ).toLowerCase();

        assertThat(failureFixture)
                .contains("create view bodeul.verify_v20_rollback_dependency")
                .contains("bodeul.resolve_admin_authorization");

        assertThat(atomicityVerification)
                .contains("실패한 v20 롤백이 관리자 rbac 테이블을 일부 제거했습니다.")
                .contains("실패한 v20 롤백이 관리자 감사 파기 집계 컬럼을 일부 제거했습니다.")
                .contains("실패한 v20 롤백이 관리자 보안 함수를 일부 제거했습니다.")
                .contains("실패한 v20 롤백이 retention 월간 집계 함수 계약을 변경했습니다.")
                .contains("실패한 v20 롤백이 관리자 런타임 함수 권한을 회수했습니다.")
                .contains("실패한 v20 롤백이 관리자 역할 검증 트리거를 제거했습니다.")
                .contains("drop view bodeul.verify_v20_rollback_dependency");

        assertThat(runner)
                .contains("migrate_database bodeul_appointment_public_code 19")
                .contains("migrate_database bodeul_admin_rbac")
                .contains("--file db/verification/014_admin_rbac_rollback_failure_fixture.sql")
                .contains("if psql --dbname bodeul_admin_rbac")
                .contains("의존 객체가 있는 v20 롤백이 예상과 달리 성공했습니다.")
                .contains("--file db/verification/015_admin_rbac_rollback_atomicity_checks.sql");
    }

    @Test
    void verificationCoversNewLegacyAndRollbackRetentionPayloads() throws IOException {
        String migrationVerification = fileSql("db/verification/012_admin_rbac_checks.sql")
                .toLowerCase();
        String rollbackVerification = fileSql("db/verification/013_admin_rbac_rollback_checks.sql")
                .toLowerCase();

        assertThat(migrationVerification)
                .contains("v20 파기 집계 22개 키를 완료 기록에 반영하지 못했습니다.")
                .contains("v20에서 기존 functions 20개 집계 키 완료 기록에 실패했습니다.")
                .contains("알 수 없는 파기 집계 키가 거부되지 않았습니다.")
                .contains("현재 operations 역할의 심사 outbox 감사를 기록하지 못했습니다.")
                .contains("patient 계정의 심사 outbox 감사가 거부되지 않았습니다.")
                .contains("현재 db 역할과 불일치한 심사 outbox 역할이 거부되지 않았습니다.")
                .contains("심사 outbox 역할 metadata가 다른 민감 감사에 적용되었습니다.")
                .contains("개발 관리자 역할 metadata가 심사 성공 감사에 허용되었습니다.")
                .contains("payload hash가 없는 심사 outbox metadata가 허용되었습니다.")
                .contains("관리자 역할이 없는 심사 outbox 감사가 거부되지 않았습니다.")
                .contains("이전 긴급 접근 id가 현재 활성 권한을 회수했습니다.")
                .contains("run.admin_audit_candidates = 11")
                .contains("run.admin_audits_deleted = 22");

        assertThat(rollbackVerification)
                .contains("v20 functions 집계 키를 포함한 롤백 호환 완료 기록에 실패했습니다.")
                .contains("롤백 뒤 0이 아닌 관리자 감사 파기 집계가 거부되지 않았습니다.")
                .contains("column_name in ('admin_audit_candidates', 'admin_audits_deleted')");
    }

    private String fileSql(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
