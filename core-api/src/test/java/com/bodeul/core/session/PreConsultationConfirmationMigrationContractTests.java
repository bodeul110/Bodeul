package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class PreConsultationConfirmationMigrationContractTests {

    @Test
    void migrationAddsPersistentConfirmationWithNarrowRuntimeWriteGrant() throws IOException {
        String sql = new ClassPathResource(
                "db/migration/V16__add_pre_consultation_confirmation.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("add column pre_consultation_confirmed boolean not null default false")
                .contains("grant update (pre_consultation_confirmed)")
                .contains("to bodeul_core_runtime")
                .doesNotContain("grant update on table")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role");
    }

    @Test
    void rollbackAndVerificationCoverTheAddedColumn() throws IOException {
        String rollback = fileSql("db/rollback/V16__remove_pre_consultation_confirmation.sql");
        String verification = fileSql("db/verification/011_pre_consultation_confirmation_checks.sql");

        assertThat(rollback)
                .contains("drop column if exists pre_consultation_confirmed");
        assertThat(verification)
                .contains("column_name = 'pre_consultation_confirmed'")
                .contains("has_column_privilege")
                .contains("'UPDATE'");
    }

    private String fileSql(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (Files.notExists(path)) {
            path = Path.of("core-api").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
