package com.bodeul.core.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentPublicCodeMigrationContractTests {

    @Test
    void migrationAddsImmutableUniqueCodeAndAuditedAdminLookup() throws IOException {
        String sql = fileSql("src/main/resources/db/migration/V19__add_appointment_public_code.sql");

        assertThat(sql)
                .contains("add column public_code text")
                .contains("^bd-[a-z0-9]{6}$")
                .contains("uq_appointment_requests_public_code")
                .contains("appointment_requests_public_code_immutable")
                .contains("appointment_public_code_search_audit")
                .contains("encode(sha256(convert_to(normalized_code, 'utf8')), 'hex')")
                .contains("recent_searches >= 10")
                .contains("audit.outcome = 'rate_limited'")
                .contains("for attempt in 0..63 loop")
                .contains("search_appointment_by_public_code")
                .contains("alter function bodeul.search_appointment_by_public_code(uuid, text) owner to bodeul_migration")
                .contains("grant execute on function bodeul.search_appointment_by_public_code(uuid, text)")
                .doesNotContain("grant execute on function bodeul.search_appointment_by_public_code(uuid, text)\n    to bodeul_core_runtime");
    }

    @Test
    void rollbackRemovesLookupAuditAndCodeColumn() throws IOException {
        String sql = fileSql("db/rollback/V19__remove_appointment_public_code.sql");

        assertThat(sql)
                .contains("drop function if exists bodeul.search_appointment_by_public_code(uuid, text)")
                .contains("drop table if exists bodeul.appointment_public_code_search_audit")
                .contains("drop trigger if exists appointment_requests_public_code_immutable")
                .contains("drop column if exists public_code");

        assertThat(fileSql("db/verification/013_appointment_public_code_rollback_checks.sql"))
                .contains("예약 공개 코드 열이 롤백 후 남아 있습니다.")
                .contains("예약 공개 코드 검색 감사 테이블이 롤백 후 남아 있습니다.")
                .contains("예약 공개 코드 함수가 롤백 후 남아 있습니다.")
                .contains("예약 공개 코드 변경 금지 트리거가 롤백 후 남아 있습니다.");
    }

    @Test
    void developmentVerificationChecksBackfillImmutabilityAndLeastPrivilege() throws IOException {
        String sql = fileSql("db/verification/012_appointment_public_code_checks.sql");

        assertThat(sql)
                .contains("public_code !~ '^bd-[a-z0-9]{6}$'")
                .contains("having count(*) > 1")
                .contains("appointment_requests_public_code_immutable")
                .contains("appointment_public_code_search_audit")
                .contains("has_function_privilege")
                .contains("rollback;");
    }

    private String fileSql(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8).toLowerCase();
    }
}
