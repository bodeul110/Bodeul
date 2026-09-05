package com.bodeul.core.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBankTransferPaymentReadContractTests {

    @Test
    void readContractRestrictsRuntimeAndRecordsPurposeWithoutPrivateAuditMetadata() throws IOException {
        String sql = read("src/main/resources/db/migration/V23__add_admin_bank_transfer_payment_read.sql");
        assertThat(sql)
                .contains("assignment.revoked_at is null")
                .contains("assignment.admin_role in ('SUPER_ADMIN', 'OPERATIONS')")
                .contains("app_user.role = 'ADMIN'")
                .contains("for share of assignment, app_user")
                .contains("security definer")
                .contains("set search_path = bodeul, pg_temp")
                .contains("from public, anon, authenticated, service_role, bodeul_core_runtime")
                .contains("to bodeul_admin_runtime")
                .contains("record_admin_access_audit")
                .contains("'RAW_VIEW', 'APPOINTMENT_PAYMENT'")
                .contains("'ALLOWED', '{}'::jsonb")
                .contains("limit 21")
                .contains("recent.position <= 20")
                .contains("'hasMoreEvents', history.has_more")
                .doesNotContain("patient_phone", "patient_email", "depositor_name_fingerprint");
    }

    @Test
    void verifierChecksAuthorizationBoundedHistoryAndNonDestructiveRollback() throws IOException {
        String checks = read("db/verification/022_admin_bank_transfer_payment_read_checks.sql");
        assertThat(checks)
                .contains("has_function_privilege")
                .contains("운영 역할 없는 사용자")
                .contains("존재하지 않는 결제")
                .contains("최근 20건 제한")
                .contains("철회된 운영 역할")
                .contains("action = 'RAW_VIEW'");
        assertThat(read("db/verification/018_bank_transfer_payment_checks.sql"))
                .contains("\\ir 022_admin_bank_transfer_payment_read_checks.sql");
        assertThat(read("db/verification/verify_bank_transfer_payment_migration.sh"))
                .contains("V23__remove_admin_bank_transfer_payment_read.sql");
        assertThat(read("db/rollback/V23__remove_admin_bank_transfer_payment_read.sql"))
                .contains("begin;", "drop function bodeul.get_admin_bank_transfer_payment(uuid, uuid);", "commit;")
                .doesNotContain("drop table", "delete from", "cascade");
    }

    private String read(String file) throws IOException {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }
}
