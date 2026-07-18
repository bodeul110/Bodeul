package com.bodeul.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionSessionSeedApplicationTests {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsOnlyOrderedUpsertsForTheThreeOperationalTables() throws Exception {
        var upserts = CompanionSessionSeedApplication.validateSeedSql(validSql());

        assertThat(upserts).hasSize(3);
        assertThat(upserts.get(0)).contains("bodeul.companion_sessions");
        assertThat(upserts.get(1)).contains("bodeul.session_reports");
        assertThat(upserts.get(2)).contains("bodeul.appointment_follow_ups");
    }

    @Test
    void rejectsDeleteAndOtherSchemaReferences() {
        String invalid = validSql().replace(
                "insert into bodeul.session_reports",
                "delete from public.session_reports; insert into bodeul.session_reports");

        assertThatThrownBy(() -> CompanionSessionSeedApplication.validateSeedSql(invalid))
                .isInstanceOf(CompanionSessionSeedApplication.SeedValidationException.class);
    }

    @Test
    void rejectsWrongConflictKeyAndForeignKeyOrder() {
        String wrongConflict = validSql().replace(
                "on conflict (\"firestore_id\")",
                "on conflict (\"id\")");
        assertThatThrownBy(() -> CompanionSessionSeedApplication.validateSeedSql(wrongConflict))
                .isInstanceOf(CompanionSessionSeedApplication.SeedValidationException.class)
                .hasMessageContaining("conflict key");

        String wrongOrder = validSql().replace(
                sessionUpsert() + System.lineSeparator() + reportUpsert(),
                reportUpsert() + System.lineSeparator() + sessionUpsert());
        assertThatThrownBy(() -> CompanionSessionSeedApplication.validateSeedSql(wrongOrder))
                .isInstanceOf(CompanionSessionSeedApplication.SeedValidationException.class)
                .hasMessageContaining("FK 순서");
    }

    @Test
    void runsValidatedStatementsWithoutPrintingSecrets() throws Exception {
        Path seedFile = tempDirectory.resolve("session-seed.sql");
        Files.writeString(seedFile, validSql(), StandardCharsets.UTF_8);
        AtomicInteger executedStatements = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = CompanionSessionSeedApplication.run(
                environment(seedFile),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, upserts) -> executedStatements.set(upserts.size()));

        assertThat(exitCode).isZero();
        assertThat(executedStatements).hasValue(3);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("적용 문장: 3건");
        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("migration-password");
        assertThat(error.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void databaseErrorsExposeOnlySqlState() throws Exception {
        Path seedFile = tempDirectory.resolve("session-seed.sql");
        Files.writeString(seedFile, validSql(), StandardCharsets.UTF_8);
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = CompanionSessionSeedApplication.run(
                environment(seedFile),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, upserts) -> {
                    throw new SQLException("sensitive row detail", "23503", 0);
                });

        assertThat(exitCode).isEqualTo(3);
        assertThat(error.toString(StandardCharsets.UTF_8))
                .contains("SQLSTATE=23503")
                .doesNotContain("sensitive row detail")
                .doesNotContain("migration-password");
    }

    private Map<String, String> environment(Path seedFile) {
        return Map.of(
                CompanionSessionSeedApplication.SEED_FILE_ENV, seedFile.toString(),
                CompanionSessionSeedApplication.JDBC_URL_ENV, "jdbc:postgresql://preview.invalid:5432/postgres",
                CompanionSessionSeedApplication.DB_USERNAME_ENV, "migration-user",
                CompanionSessionSeedApplication.DB_PASSWORD_ENV, "migration-password");
    }

    private String validSql() {
        return String.join(System.lineSeparator(),
                "begin;",
                "set local role bodeul_migration;",
                sessionUpsert(),
                reportUpsert(),
                followUpUpsert(),
                "commit;",
                "");
    }

    private String sessionUpsert() {
        return "insert into bodeul.companion_sessions (\"id\", \"firestore_id\", \"imported_at\") "
                + "values ('00000000-0000-0000-0000-000000000001', 'session-1', now()) "
                + "on conflict (\"firestore_id\") do update set \"imported_at\" = now();";
    }

    private String reportUpsert() {
        return "insert into bodeul.session_reports (\"id\", \"firestore_id\", \"imported_at\") "
                + "values ('00000000-0000-0000-0000-000000000002', 'report-1', now()) "
                + "on conflict (\"firestore_id\") do update set \"imported_at\" = now();";
    }

    private String followUpUpsert() {
        return "insert into bodeul.appointment_follow_ups (\"appointment_request_id\", \"imported_at\") "
                + "values ('00000000-0000-0000-0000-000000000003', now()) "
                + "on conflict (\"appointment_request_id\") do update set \"imported_at\" = now();";
    }
}
