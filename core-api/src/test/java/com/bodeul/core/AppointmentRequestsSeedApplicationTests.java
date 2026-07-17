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

class AppointmentRequestsSeedApplicationTests {

    @TempDir
    Path tempDirectory;

    @Test
    void validPreviewSeedExecutesOnlyTheValidatedUpserts() throws Exception {
        String sensitiveValue = "private-patient-value";
        Path seedFile = writeSeed(validSeedSql(sensitiveValue));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        AtomicInteger executedUpserts = new AtomicInteger();

        int exitCode = AppointmentRequestsSeedApplication.run(
                environment(seedFile),
                printStream(output),
                printStream(error),
                (databaseConfig, upserts) -> executedUpserts.set(upserts.size()));

        assertThat(exitCode).isZero();
        assertThat(executedUpserts).hasValue(1);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("적용 문장: 1건")
                .doesNotContain(sensitiveValue);
        assertThat(error.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void sensitiveTextThatLooksLikeSqlRemainsData() throws Exception {
        Path seedFile = writeSeed(validSeedSql("drop table bodeul.app_users; -- 데이터 문자열"));
        AtomicInteger executedUpserts = new AtomicInteger();

        int exitCode = AppointmentRequestsSeedApplication.run(
                environment(seedFile),
                printStream(new ByteArrayOutputStream()),
                printStream(new ByteArrayOutputStream()),
                (databaseConfig, upserts) -> executedUpserts.set(upserts.size()));

        assertThat(exitCode).isZero();
        assertThat(executedUpserts).hasValue(1);
    }

    @Test
    void deleteStatementsAreRejected() throws Exception {
        String invalidSql = """
                begin;
                set local role bodeul_migration;
                delete from bodeul.appointment_requests where firestore_id = 'private-id';
                commit;
                """;
        Path seedFile = writeSeed(invalidSql);
        AtomicInteger executedUpserts = new AtomicInteger();

        int exitCode = AppointmentRequestsSeedApplication.run(
                environment(seedFile),
                printStream(new ByteArrayOutputStream()),
                printStream(new ByteArrayOutputStream()),
                (databaseConfig, upserts) -> executedUpserts.incrementAndGet());

        assertThat(exitCode).isEqualTo(2);
        assertThat(executedUpserts).hasValue(0);
    }

    @Test
    void otherBodeulTablesAreRejected() throws Exception {
        Path seedFile = writeSeed(validSeedSql("private-patient-value")
                .replace("bodeul.appointment_requests", "bodeul.app_users"));
        AtomicInteger executedUpserts = new AtomicInteger();

        int exitCode = AppointmentRequestsSeedApplication.run(
                environment(seedFile),
                printStream(new ByteArrayOutputStream()),
                printStream(new ByteArrayOutputStream()),
                (databaseConfig, upserts) -> executedUpserts.incrementAndGet());

        assertThat(exitCode).isEqualTo(2);
        assertThat(executedUpserts).hasValue(0);
    }

    @Test
    void databaseErrorsDoNotExposeSqlOrExceptionMessages() throws Exception {
        String sensitiveValue = "private-patient-value";
        Path seedFile = writeSeed(validSeedSql(sensitiveValue));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = AppointmentRequestsSeedApplication.run(
                environment(seedFile),
                printStream(output),
                printStream(error),
                (databaseConfig, upserts) -> {
                    throw new SQLException("failed near " + sensitiveValue, "42501", 7);
                });

        assertThat(exitCode).isEqualTo(3);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(error.toString(StandardCharsets.UTF_8))
                .contains("SQLSTATE=42501")
                .contains("vendorCode=7")
                .doesNotContain(sensitiveValue)
                .doesNotContain("failed near");
    }

    private Path writeSeed(String sql) throws Exception {
        Path seedFile = tempDirectory.resolve("appointment-requests-seed.sql");
        Files.writeString(seedFile, sql, StandardCharsets.UTF_8);
        return seedFile;
    }

    private static Map<String, String> environment(Path seedFile) {
        return Map.of(
                AppointmentRequestsSeedApplication.SEED_FILE_ENV, seedFile.toString(),
                AppointmentRequestsSeedApplication.JDBC_URL_ENV, "jdbc:postgresql://preview.invalid:5432/postgres",
                AppointmentRequestsSeedApplication.DB_USERNAME_ENV, "migration-user",
                AppointmentRequestsSeedApplication.DB_PASSWORD_ENV, "migration-password");
    }

    private static PrintStream printStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String validSeedSql(String sensitiveValue) {
        String escapedValue = sensitiveValue.replace("'", "''");
        return """
                -- 예약 요청 preview 백필
                begin;
                set local role bodeul_migration;
                insert into bodeul.appointment_requests ("id", "firestore_id", "patient_name")
                values ('00000000-0000-5000-8000-000000000001', 'request-1', '%s')
                on conflict (firestore_id) do update set "patient_name" = excluded."patient_name", imported_at = now();
                commit;
                """.formatted(escapedValue);
    }
}
