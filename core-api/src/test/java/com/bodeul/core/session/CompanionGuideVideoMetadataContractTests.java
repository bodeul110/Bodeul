package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionGuideVideoMetadataContractTests {

    @Test
    void legacyFourFieldRecordsKeepNullableVideoMetadata() {
        var repositoryRecord = new CompanionSessionRepository.GuideStepRecord(
                "STEP_1", 1, "기존 단계", "기존 설명");
        var apiView = new CompanionSessionService.GuideStepView(
                "STEP_1", 1, "기존 단계", "기존 설명");

        assertThat(repositoryRecord.videoAssetId()).isNull();
        assertThat(repositoryRecord.videoAssetVersion()).isNull();
        assertThat(repositoryRecord.videoFallbackText()).isNull();
        assertThat(apiView.videoAssetId()).isNull();
        assertThat(apiView.videoAssetVersion()).isNull();
        assertThat(apiView.videoFallbackText()).isNull();
    }

    @Test
    void migrationAddsOnlyOptionalVideoMetadataValidation() throws IOException {
        String sql = migrationSql().toLowerCase();

        assertThat(sql)
                .contains("create function bodeul.is_valid_guide_step_media_v1")
                .contains("videoassetid")
                .contains("videoassetversion")
                .contains("videofallbacktext")
                .contains("ck_hospital_guides_step_media_shape")
                .contains("ck_companion_sessions_guide_media_shape")
                .contains("step_contract_version <> 1")
                .contains("guide_step_contract_version is distinct from 1")
                .contains("not valid")
                .contains("validate constraint")
                .doesNotContain("insert into")
                .doesNotContain("create table");
    }

    @Test
    void rollbackRemovesOnlyVideoMetadataValidation() throws IOException {
        String rollback = fileSql(
                "db/rollback/V21__remove_companion_guide_video_metadata_validation.sql"
        ).toLowerCase();

        assertThat(rollback)
                .contains("drop constraint if exists ck_companion_sessions_guide_media_shape")
                .contains("drop constraint if exists ck_hospital_guides_step_media_shape")
                .contains("drop function if exists bodeul.is_valid_guide_step_media_v1(jsonb)")
                .doesNotContain("drop column")
                .doesNotContain("delete from");
        assertThat(rollback.stripLeading()).startsWith("begin;");
        assertThat(rollback.stripTrailing()).endsWith("commit;");
    }

    @Test
    void migrationRunnerVerifiesUpgradeAndRollbackPaths() throws IOException {
        String runner = fileSql(
                "db/verification/verify_companion_guide_snapshot_migration.sh"
        ).toLowerCase();
        String checks = fileSql(
                "db/verification/016_companion_guide_video_metadata_checks.sql"
        ).toLowerCase();
        String rollbackChecks = fileSql(
                "db/verification/017_companion_guide_video_metadata_rollback_checks.sql"
        ).toLowerCase();

        assertThat(runner)
                .contains("016_companion_guide_video_metadata_checks.sql")
                .contains("v21__remove_companion_guide_video_metadata_validation.sql")
                .contains("017_companion_guide_video_metadata_rollback_checks.sql");
        assertThat(checks)
                .contains("기존 4필드 가이드 snapshot")
                .contains("버전 없는 영상 자산 id")
                .contains("대체 안내 없는 영상 메타데이터")
                .contains("문자열이 아닌 영상 대체 안내")
                .contains("빈 영상 대체 안내");
        assertThat(rollbackChecks)
                .contains("영상 메타데이터 검증 함수가 롤백 후 남아 있습니다.")
                .contains("영상 메타데이터 제약이 롤백 후 남아 있습니다.");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V21__validate_companion_guide_video_metadata.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
    }

    private String fileSql(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
