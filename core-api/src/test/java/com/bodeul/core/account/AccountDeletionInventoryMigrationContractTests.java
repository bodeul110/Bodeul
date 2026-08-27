package com.bodeul.core.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionInventoryMigrationContractTests {

    @Test
    void inventoryFunctionReturnsCountsWithoutExpandingCoreTablePrivileges() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create function bodeul.account_deletion_postgres_inventory")
                .contains("security definer")
                .contains("set search_path = bodeul, pg_temp")
                .contains("companion_session_assignment_audits")
                .contains("message.sender_user_id = p_user_id")
                .contains("location.manager_user_id = p_user_id")
                .contains("follow_up.review_saved_by_user_id = p_user_id")
                .contains("follow_up.settlement_follow_up_saved_by_user_id = p_user_id")
                .contains("follow_up.support_escalated_by_user_id = p_user_id")
                .contains("receipt.last_read_message_id in (select message.id from related_messages message)")
                .contains("active_legal_hold_count")
                .contains("revoke all on function bodeul.account_deletion_postgres_inventory")
                .contains("grant execute on function bodeul.account_deletion_postgres_inventory")
                .contains("to bodeul_core_runtime, bodeul_admin_runtime")
                .doesNotContain("grant select on table bodeul.companion_session_assignment_audits to bodeul_core_runtime")
                .doesNotContain("patient_name")
                .doesNotContain("guardian_name")
                .doesNotContain("storage_path")
                .doesNotContain("select message.body");
    }

    @Test
    void rollbackRemovesOnlyTheReadOnlyInventoryFunction() throws IOException {
        String sql = fileSql("db/rollback/V15__remove_account_deletion_inventory.sql");

        assertThat(sql)
                .contains("revoke execute on function bodeul.account_deletion_postgres_inventory")
                .contains("drop function bodeul.account_deletion_postgres_inventory(uuid)")
                .doesNotContain("drop table")
                .doesNotContain("delete from");
    }

    private String migrationSql() throws IOException {
        return new ClassPathResource(
                "db/migration/V15__add_account_deletion_inventory.sql"
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
