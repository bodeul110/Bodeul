package com.bodeul.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionFixtureApplicationTests {

    @Test
    void runsOnlyAgainstConfirmedPreviewProject() {
        AtomicReference<RetentionFixtureApplication.FixtureAction> executedAction = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = RetentionFixtureApplication.run(
                environment("setup"),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    executedAction.set(action);
                    return summary(action);
                });

        assertThat(exitCode).isZero();
        assertThat(executedAction).hasValue(RetentionFixtureApplication.FixtureAction.SETUP);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("action=SETUP")
                .contains("attachmentCandidates=1")
                .doesNotContain("migration-password");
        assertThat(error.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void rejectsProductionAndWrongProjectConfirmation() {
        Map<String, String> production = new HashMap<>(environment("status"));
        production.put(RetentionFixtureApplication.TARGET_ENV, "production");
        Map<String, String> wrongProject = new HashMap<>(environment("cleanup"));
        wrongProject.put(RetentionFixtureApplication.CONFIRM_PROJECT_ENV, "bodeul-prod-110");

        assertThat(runWithoutDatabase(production)).isEqualTo(2);
        assertThat(runWithoutDatabase(wrongProject)).isEqualTo(2);
    }

    @Test
    void rejectsUnknownAction() {
        assertThat(runWithoutDatabase(environment("delete-all"))).isEqualTo(2);
    }

    @Test
    void rejectsDatabaseWithoutPreviewProjectRef() {
        Map<String, String> wrongDatabase = new HashMap<>(environment("status"));
        wrongDatabase.put(
                RetentionFixtureApplication.JDBC_URL_ENV,
                "jdbc:postgresql://production.invalid:5432/postgres");

        assertThat(runWithoutDatabase(wrongDatabase)).isEqualTo(2);
    }

    @Test
    void databaseErrorsExposeOnlySqlState() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = RetentionFixtureApplication.run(
                environment("setup"),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    throw new SQLException("sensitive row detail", "23503", 0);
                });

        assertThat(exitCode).isEqualTo(3);
        assertThat(error.toString(StandardCharsets.UTF_8))
                .contains("SQLSTATE=23503")
                .doesNotContain("sensitive row detail")
                .doesNotContain("migration-password");
    }

    private int runWithoutDatabase(Map<String, String> environment) {
        return RetentionFixtureApplication.run(
                environment,
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()),
                (databaseConfig, action) -> {
                    throw new AssertionError("검증 실패 시 DB 실행기가 호출되면 안 됩니다.");
                });
    }

    private Map<String, String> environment(String action) {
        return Map.of(
                RetentionFixtureApplication.ACTION_ENV, action,
                RetentionFixtureApplication.TARGET_ENV, RetentionFixtureApplication.PREVIEW_TARGET,
                RetentionFixtureApplication.CONFIRM_PROJECT_ENV, RetentionFixtureApplication.PREVIEW_PROJECT,
                RetentionFixtureApplication.JDBC_URL_ENV,
                "jdbc:postgresql://db.parpdzttloacinyvhwmx.supabase.co:5432/postgres",
                RetentionFixtureApplication.DB_USERNAME_ENV, "migration-user",
                RetentionFixtureApplication.DB_PASSWORD_ENV, "migration-password");
    }

    private RetentionFixtureApplication.FixtureSummary summary(
            RetentionFixtureApplication.FixtureAction action
    ) {
        return new RetentionFixtureApplication.FixtureSummary(
                action,
                7,
                7,
                "ACTIVE",
                false,
                false,
                false,
                "ACTIVE",
                true,
                1,
                2);
    }
}
