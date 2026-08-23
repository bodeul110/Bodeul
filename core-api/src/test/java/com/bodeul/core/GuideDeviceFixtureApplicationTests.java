package com.bodeul.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuideDeviceFixtureApplicationTests {

    @Test
    void runsOnlyAgainstConfirmedPreviewProject() {
        AtomicReference<GuideDeviceFixtureApplication.FixtureAction> executedAction = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = GuideDeviceFixtureApplication.run(
                environment("setup"),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    executedAction.set(action);
                    return readySummary(action);
                });

        assertThat(exitCode).isZero();
        assertThat(executedAction).hasValue(GuideDeviceFixtureApplication.FixtureAction.SETUP);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("action=SETUP")
                .contains("currentStepCode=PHARMACY_ROUTE")
                .contains("ready=true")
                .doesNotContain("migration-password");
        assertThat(error.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void rejectsProductionAndWrongProjectConfirmation() {
        Map<String, String> production = new HashMap<>(environment("status"));
        production.put(GuideDeviceFixtureApplication.TARGET_ENV, "production");
        Map<String, String> wrongProject = new HashMap<>(environment("cleanup"));
        wrongProject.put(GuideDeviceFixtureApplication.CONFIRM_PROJECT_ENV, "bodeul-prod-110");

        assertThat(runWithoutDatabase(production)).isEqualTo(2);
        assertThat(runWithoutDatabase(wrongProject)).isEqualTo(2);
    }

    @Test
    void rejectsUnknownActionAndNonPostgresUrl() {
        Map<String, String> wrongDatabase = new HashMap<>(environment("status"));
        wrongDatabase.put(GuideDeviceFixtureApplication.JDBC_URL_ENV, "https://example.invalid/postgres");

        assertThat(runWithoutDatabase(environment("delete-all"))).isEqualTo(2);
        assertThat(runWithoutDatabase(wrongDatabase)).isEqualTo(2);
    }

    @Test
    void rejectsDatabaseWithoutPreviewProjectRef() {
        Map<String, String> wrongDatabase = new HashMap<>(environment("status"));
        wrongDatabase.put(
                GuideDeviceFixtureApplication.JDBC_URL_ENV,
                "jdbc:postgresql://production.invalid:5432/postgres");

        assertThat(runWithoutDatabase(wrongDatabase)).isEqualTo(2);
    }

    @Test
    void setupRequiresManagerFirebaseUid() {
        Map<String, String> missingManager = new HashMap<>(environment("setup"));
        missingManager.remove(GuideDeviceFixtureApplication.MANAGER_FIREBASE_UID_ENV);

        assertThat(runWithoutDatabase(missingManager)).isEqualTo(2);
    }

    @Test
    void databaseErrorsExposeOnlySqlState() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = GuideDeviceFixtureApplication.run(
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

    @Test
    void fixtureGuideHasThirteenOrderedStepsAndPharmacyAtNine() throws Exception {
        JsonNode steps = new ObjectMapper().readTree(GuideDeviceFixtureApplication.GUIDE_STEPS_JSON);

        assertThat(steps.isArray()).isTrue();
        assertThat(steps).hasSize(13);
        for (int index = 0; index < steps.size(); index++) {
            assertThat(steps.get(index).path("order").asInt()).isEqualTo(index + 1);
            assertThat(steps.get(index).path("code").asText()).isNotBlank();
            assertThat(steps.get(index).path("title").asText()).isNotBlank();
        }
        assertThat(steps.get(8).path("code").asText()).isEqualTo("PHARMACY_ROUTE");
    }

    private int runWithoutDatabase(Map<String, String> environment) {
        return GuideDeviceFixtureApplication.run(
                environment,
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()),
                (databaseConfig, action) -> {
                    throw new AssertionError("검증 실패 시 DB 실행기가 호출되면 안 됩니다.");
                });
    }

    private Map<String, String> environment(String action) {
        return Map.of(
                GuideDeviceFixtureApplication.ACTION_ENV, action,
                GuideDeviceFixtureApplication.TARGET_ENV, GuideDeviceFixtureApplication.PREVIEW_TARGET,
                GuideDeviceFixtureApplication.CONFIRM_PROJECT_ENV,
                GuideDeviceFixtureApplication.PREVIEW_PROJECT,
                GuideDeviceFixtureApplication.JDBC_URL_ENV,
                "jdbc:postgresql://db.parpdzttloacinyvhwmx.supabase.co:5432/postgres",
                GuideDeviceFixtureApplication.DB_USERNAME_ENV, "migration-user",
                GuideDeviceFixtureApplication.DB_PASSWORD_ENV, "migration-password",
                GuideDeviceFixtureApplication.MANAGER_FIREBASE_UID_ENV, "preview-manager-uid");
    }

    private GuideDeviceFixtureApplication.FixtureSummary readySummary(
            GuideDeviceFixtureApplication.FixtureAction action
    ) {
        return new GuideDeviceFixtureApplication.FixtureSummary(
                action,
                6,
                6,
                9,
                "PHARMACY_ROUTE",
                "HOSPITAL_GUIDE_STEP_CODE_V1",
                13,
                1,
                "PAYMENT",
                true);
    }
}
