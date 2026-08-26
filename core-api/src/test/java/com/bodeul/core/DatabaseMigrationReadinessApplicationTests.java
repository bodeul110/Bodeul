package com.bodeul.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseMigrationReadinessApplicationTests {

    private static final String EXPECTED_REF = "abcdefghijklmnopqrst";
    private static final String OTHER_REF = "zyxwvutsrqponmlkjihg";

    private Connection connection;
    private Statement transactionStatement;
    private PreparedStatement commonStatement;
    private PreparedStatement v13Statement;
    private PreparedStatement postV14Statement;
    private ResultSet commonResult;
    private ResultSet v13Result;
    private ResultSet postV14Result;
    private ResultSetMetaData commonMetadata;
    private ResultSetMetaData v13Metadata;
    private ResultSetMetaData postV14Metadata;

    @BeforeEach
    void setUp() throws Exception {
        connection = mock(Connection.class);
        transactionStatement = mock(Statement.class);
        commonStatement = mock(PreparedStatement.class);
        v13Statement = mock(PreparedStatement.class);
        postV14Statement = mock(PreparedStatement.class);
        commonResult = mock(ResultSet.class);
        v13Result = mock(ResultSet.class);
        postV14Result = mock(ResultSet.class);
        commonMetadata = mock(ResultSetMetaData.class);
        v13Metadata = mock(ResultSetMetaData.class);
        postV14Metadata = mock(ResultSetMetaData.class);

        when(connection.createStatement()).thenReturn(transactionStatement);
        when(connection.prepareStatement(contains("flyway_schema_history"))).thenReturn(commonStatement);
        when(connection.prepareStatement(contains("firestore_id is null"))).thenReturn(v13Statement);
        when(connection.prepareStatement(contains("guide_snapshot_source"))).thenReturn(postV14Statement);
        stubQuery(commonStatement, commonResult, commonMetadata,
                DatabaseMigrationReadinessApplication.EXPECTED_COMMON_COLUMNS);
        stubQuery(v13Statement, v13Result, v13Metadata,
                DatabaseMigrationReadinessApplication.EXPECTED_V13_COLUMNS);
        stubQuery(postV14Statement, postV14Result, postV14Metadata,
                DatabaseMigrationReadinessApplication.EXPECTED_POST_V14_COLUMNS);
        when(commonResult.getObject("transaction_is_read_only", Boolean.class)).thenReturn(true);
        when(commonResult.getString("latest_successful_version")).thenReturn("13");
        when(commonResult.getObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(v13Result.getObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(postV14Result.getObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    @Test
    void acceptsDirectHostForExpectedProject() {
        DatabaseMigrationReadinessApplication.validateConnectionTarget(
                "jdbc:postgresql://db." + EXPECTED_REF + ".supabase.co:5432/postgres?sslmode=require",
                "bodeul_migrator",
                EXPECTED_REF);
    }

    @Test
    void acceptsPoolerHostOnlyWithExpectedProjectUsernameSuffix() {
        DatabaseMigrationReadinessApplication.validateConnectionTarget(
                "jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres?sslmode=require",
                "bodeul_migrator." + EXPECTED_REF,
                EXPECTED_REF);
    }

    @Test
    void rejectsPgJdbcHostAndUserOverrides() {
        String directUrl = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:5432/postgres?sslmode=require";

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "&PGHOST=db." + OTHER_REF + ".supabase.co",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(OTHER_REF);
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "&user=bodeul_migrator." + OTHER_REF,
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(OTHER_REF);
    }

    @Test
    void rejectsPasswordDatabaseAndPortOverrides() {
        String directUrl = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:5432/postgres?sslmode=require";

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "&password=do-not-print",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("do-not-print");
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "&PGDBNAME=other_database",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("other_database");
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "&PGPORT=9999",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("9999");
    }

    @Test
    void rejectsMissingAndDisabledSslMode() {
        String directUrl = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:5432/postgres";

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl,
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl + "?sslmode=disable",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("disable");
    }

    @Test
    void rejectsUnexpectedMigrationLoginRoles() {
        String directUrl = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:5432/postgres?sslmode=require";
        String poolerUrl = "jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com"
                + ":6543/postgres?sslmode=require";

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                directUrl,
                "postgres",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("postgres");
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                poolerUrl,
                "other_role." + EXPECTED_REF,
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("other_role")
                .hasMessageNotContaining(EXPECTED_REF);
    }

    @Test
    void hidesMalformedPortAndQueryFromExceptionAndCause() {
        String malformedPort = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:not-a-port/postgres?sslmode=require";
        String malformedQuery = "jdbc:postgresql://db." + EXPECTED_REF
                + ".supabase.co:5432/postgres?sslmode=%ZZ";

        Throwable portFailure = catchThrowable(() ->
                DatabaseMigrationReadinessApplication.validateConnectionTarget(
                        malformedPort,
                        "bodeul_migrator",
                        EXPECTED_REF));
        Throwable queryFailure = catchThrowable(() ->
                DatabaseMigrationReadinessApplication.validateConnectionTarget(
                        malformedQuery,
                        "bodeul_migrator",
                        EXPECTED_REF));

        assertThat(portFailure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(malformedPort)
                .hasNoCause();
        assertThat(portFailure.toString())
                .doesNotContain(malformedPort)
                .doesNotContain("not-a-port")
                .doesNotContain(EXPECTED_REF);
        assertThat(queryFailure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(malformedQuery)
                .hasNoCause();
        assertThat(queryFailure.toString())
                .doesNotContain(malformedQuery)
                .doesNotContain("%ZZ")
                .doesNotContain(EXPECTED_REF);
    }

    @Test
    void rejectsNonCanonicalUrlsBeforePgjdbcCanLogTheirValues() {
        String marker = "pgjdbc-sensitive-marker";
        String directAuthority = "db." + EXPECTED_REF + ".supabase.co:5432";
        List<String> rejectedUrls = List.of(
                "jdbc:postgresql://" + directAuthority
                        + "/post%67res?sslmode=require#" + marker,
                "jdbc:postgresql://" + directAuthority
                        + "/postgres?sslmode=require&applicationName=" + marker,
                "jdbc:postgresql://" + directAuthority
                        + "/postgres?sslmode=require&sslmode=require&marker=" + marker,
                "jdbc:postgresql://" + marker + "@" + directAuthority
                        + "/postgres?sslmode=require",
                "jdbc:postgresql://" + directAuthority
                        + "/postgres?sslmode=require#" + marker,
                "jdbc:postgresql://db." + EXPECTED_REF + ".supabase.co," + marker
                        + ":5432/postgres?sslmode=require",
                "jdbc:postgresql://" + directAuthority
                        + "/postgres?sslmode=require&PGHOST=db." + EXPECTED_REF
                        + ".supabase.co," + marker);

        Logger pgLogger = Logger.getLogger("org.postgresql");
        Level previousLevel = pgLogger.getLevel();
        boolean previousUseParentHandlers = pgLogger.getUseParentHandlers();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        pgLogger.addHandler(handler);
        pgLogger.setLevel(Level.ALL);
        pgLogger.setUseParentHandlers(false);
        try {
            for (String rejectedUrl : rejectedUrls) {
                Throwable failure = catchThrowable(() ->
                        DatabaseMigrationReadinessApplication.validateConnectionTarget(
                                rejectedUrl,
                                "bodeul_migrator",
                                EXPECTED_REF));
                assertThat(failure)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageNotContaining(marker)
                        .hasNoCause();
                assertThat(failure.toString())
                        .doesNotContain(marker)
                        .doesNotContain(rejectedUrl);
            }
        } finally {
            pgLogger.removeHandler(handler);
            pgLogger.setLevel(previousLevel);
            pgLogger.setUseParentHandlers(previousUseParentHandlers);
        }

        assertThat(records).noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue());
        assertThat(records.stream().map(DatabaseMigrationReadinessApplicationTests::renderLogRecord))
                .noneMatch(rendered -> rendered.contains(marker));
    }

    @Test
    void targetOnlyModeRequiresNoPasswordAndRejectsEveryOtherArgumentShape() {
        String jdbcUrl = "jdbc:postgresql://db." + EXPECTED_REF + ".supabase.co/postgres";
        DatabaseMigrationReadinessApplication.TargetConnectionSettings settings =
                DatabaseMigrationReadinessApplication.requiredTargetSettings(Map.of(
                        DatabaseMigrationReadinessApplication.JDBC_URL_ENV, jdbcUrl,
                        DatabaseMigrationReadinessApplication.USERNAME_ENV, "bodeul_migrator",
                        DatabaseMigrationReadinessApplication.EXPECTED_PROJECT_REF_ENV, EXPECTED_REF));

        assertThat(DatabaseMigrationReadinessApplication.executionMode(new String[] {"--target-only"}))
                .isEqualTo(DatabaseMigrationReadinessApplication.ExecutionMode.TARGET_ONLY);
        assertThat(DatabaseMigrationReadinessApplication.executionMode(new String[0]))
                .isEqualTo(DatabaseMigrationReadinessApplication.ExecutionMode.FULL_READINESS);
        assertThat(settings.jdbcUrl()).isEqualTo(jdbcUrl);
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.executionMode(
                new String[] {"--target-only", "unexpected"}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.executionMode(
                new String[] {"--unknown"}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDifferentProjectWithoutPrintingConnectionValues() {
        String jdbcUrl = "jdbc:postgresql://db." + OTHER_REF + ".supabase.co:5432/postgres";
        String username = "bodeul_migrator." + OTHER_REF;

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                jdbcUrl,
                username,
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(jdbcUrl)
                .hasMessageNotContaining(username)
                .hasMessageNotContaining(EXPECTED_REF)
                .hasMessageNotContaining(OTHER_REF);
    }

    @Test
    void rejectsMalformedTargetAndProjectRef() {
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                "not-a-jdbc-url",
                "bodeul_migrator",
                EXPECTED_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-a-jdbc-url");
        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.validateConnectionTarget(
                "jdbc:postgresql://db.example.supabase.co/postgres",
                "bodeul_migrator",
                "INVALID_REF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("INVALID_REF");
    }

    @Test
    void usesReadOnlyTransactionAndV13TargetQueryThenAlwaysRollsBack() throws Exception {
        DatabaseMigrationReadinessApplication.DatabaseMigrationReadiness readiness =
                DatabaseMigrationReadinessApplication.runVerification(connection);

        assertThat(readiness.latestSuccessfulVersion()).isEqualTo("13");
        assertThat(readiness.v14BackfillCandidateCount()).isZero();
        assertThat(readiness.unresolvedLegacySnapshotCount()).isNull();
        verify(connection).setAutoCommit(false);
        verify(connection).setReadOnly(true);
        verify(transactionStatement).execute("set transaction read only");
        verify(transactionStatement).execute("set local role bodeul_migration");
        verify(commonStatement).executeQuery();
        verify(v13Statement).executeQuery();
        verify(connection).rollback();
    }

    @Test
    void usesUnresolvedSnapshotQueryAfterV14() throws Exception {
        when(commonResult.getString("latest_successful_version")).thenReturn("14");

        DatabaseMigrationReadinessApplication.DatabaseMigrationReadiness readiness =
                DatabaseMigrationReadinessApplication.verify(connection);

        assertThat(readiness.v14BackfillCandidateCount()).isNull();
        assertThat(readiness.unresolvedLegacySnapshotCount()).isZero();
        verify(postV14Statement).executeQuery();
    }

    @Test
    void rollsBackWhenVerificationFails() throws Exception {
        when(commonResult.getString("latest_successful_version")).thenReturn("12");

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.runVerification(connection))
                .isInstanceOf(IllegalStateException.class);

        verify(connection).rollback();
    }

    @Test
    void failsWhenCommonColumnsDrift() throws Exception {
        when(commonMetadata.getColumnLabel(1)).thenReturn("renamed_transaction_read_only");

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("열 계약");
    }

    @Test
    void failsWhenVersionSpecificColumnsDrift() throws Exception {
        when(v13Metadata.getColumnLabel(1)).thenReturn("renamed_candidate_count");

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("열 계약");
    }

    @Test
    void failsWhenLatestVersionIsOutsideMigrationWindowWithoutPrintingVersion() throws Exception {
        when(commonResult.getString("latest_successful_version")).thenReturn("16-secret");

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("16-secret");
    }

    @Test
    void failsWhenFlywayHistoryContainsFailureWithoutPrintingCount() throws Exception {
        when(commonResult.getObject("failed_migration_count", Long.class)).thenReturn(1L);

        assertThatThrownBy(() -> DatabaseMigrationReadinessApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("1");
    }

    private static void stubQuery(
            PreparedStatement statement,
            ResultSet resultSet,
            ResultSetMetaData metadata,
            List<String> expectedColumns) throws Exception {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(expectedColumns.size());
        when(metadata.getColumnLabel(anyInt())).thenAnswer(invocation ->
                expectedColumns.get(invocation.getArgument(0, Integer.class) - 1));
        when(resultSet.next()).thenReturn(true, false);
    }

    private static String renderLogRecord(LogRecord record) {
        return String.valueOf(record.getMessage())
                + Arrays.toString(record.getParameters())
                + String.valueOf(record.getThrown());
    }
}
