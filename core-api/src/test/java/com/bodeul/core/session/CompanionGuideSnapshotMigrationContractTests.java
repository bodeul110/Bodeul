package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionGuideSnapshotMigrationContractTests {

    @Test
    void migrationAddsARevisionedGuideContractWithoutBreakingLegacyRows() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.is_valid_guide_steps_v1")
                .contains("add column revision bigint not null default 1")
                .contains("add column step_contract_version smallint not null default 0")
                .contains("step_contract_version = 0")
                .contains("step_contract_version = 1")
                .contains("with ordinality as entry(step, position)")
                .contains("count(distinct entry.step ->> 'code')")
                .contains("create trigger bump_hospital_guide_revision_before_update")
                .doesNotContain("MEETING_CONFIRMATION")
                .doesNotContain("CARE_ENDED");
    }

    @Test
    void sessionSnapshotIsAdditiveAndImmutableForRollingCoreApi() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("add column guide_id uuid")
                .contains("add column guide_revision bigint")
                .contains("add column guide_step_contract_version smallint")
                .contains("add column guide_steps_snapshot jsonb")
                .contains("add column guide_snapshot_source text not null default 'UNRESOLVED_LEGACY'")
                .contains("and guide_steps_snapshot is not null")
                .contains(") is true)")
                .contains("create index ix_companion_sessions_guide_revision")
                .contains("'LEGACY_CORE_7_V1'")
                .contains("where firestore_id is null")
                .contains("and current_step_order between 0 and 7")
                .contains("create trigger prevent_companion_guide_snapshot_change_before_update")
                .doesNotContain("current_step_order <= jsonb_array_length")
                .doesNotContain("grant update (guide_id")
                .doesNotContain("grant update (guide_steps_snapshot");
    }

    @Test
    void assignmentKeepsItsPublicIdentityAndCopiesOneGuideRow() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create or replace function bodeul.assign_companion_session(")
                .contains("p_expected_appointment_version bigint")
                .contains("p_reason text default ''")
                .contains("for update")
                .contains("guide.step_contract_version")
                .contains("guide_steps_snapshot")
                .contains("guide_snapshot_source")
                .contains("security definer")
                .contains("owner to bodeul_migration")
                .contains("to bodeul_admin_runtime")
                .doesNotContain("to bodeul_core_runtime;\ngrant execute on function bodeul.assign_companion_session");
    }

    @Test
    void rollbackStopsBeforeDiscardingNewGuideDataAndRestoresTheOldFunctionFirst() throws IOException {
        String sql = rollbackSql();

        int functionRestore = sql.indexOf("create or replace function bodeul.assign_companion_session(");
        int sessionColumnsDrop = sql.indexOf("drop column if exists guide_snapshot_source");
        int validatorDrop = sql.indexOf("drop function if exists bodeul.is_valid_guide_steps_v1(jsonb)");

        assertThat(sql)
                .contains("where step_contract_version = 1 or revision > 1")
                .contains("'HOSPITAL_GUIDE_STEP_CODE_V1'")
                .contains("'LEGACY_HOSPITAL_GUIDE_V0'")
                .contains("'GUIDE_NOT_FOUND'")
                .contains("drop index if exists bodeul.ix_companion_sessions_guide_revision")
                .contains("rollback을 중단합니다")
                .contains("grant execute on function bodeul.assign_companion_session")
                .doesNotContain("LEGACY_CORE_7_V1',\n            'UNRESOLVED_LEGACY'");
        assertThat(functionRestore).isGreaterThanOrEqualTo(0);
        assertThat(functionRestore).isLessThan(sessionColumnsDrop);
        assertThat(sessionColumnsDrop).isLessThan(validatorDrop);
    }

    @Test
    void ciScenarioExecutesEmptyUpgradeAndRollbackPaths() throws IOException {
        String script = fileSql("db/verification/verify_companion_guide_snapshot_migration.sh");

        assertThat(script)
                .contains("migrate_database bodeul_guide_empty")
                .contains("migrate_database bodeul_guide_upgrade 13")
                .contains("005_companion_guide_snapshot_legacy_fixture.sql")
                .contains("006_companion_guide_snapshot_checks.sql")
                .contains("007_companion_guide_snapshot_upgrade_checks.sql")
                .contains("V14__restore_live_companion_guides.sql")
                .contains("009_companion_guide_snapshot_rollback_checks.sql");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V14__freeze_companion_guide_snapshots.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }

    private String rollbackSql() throws IOException {
        return fileSql("db/rollback/V14__restore_live_companion_guides.sql");
    }

    private String fileSql(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
