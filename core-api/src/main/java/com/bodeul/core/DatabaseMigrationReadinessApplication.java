package com.bodeul.core;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.postgresql.Driver;
import org.postgresql.PGProperty;

public final class DatabaseMigrationReadinessApplication {

    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String PASSWORD_ENV = "MIGRATION_DB_PASSWORD";
    static final String EXPECTED_PROJECT_REF_ENV = "EXPECTED_SUPABASE_PROJECT_REF";

    static final Set<String> ALLOWED_SCHEMA_VERSIONS = Set.of("13", "14", "15");
    static final List<String> EXPECTED_COMMON_COLUMNS = List.of(
            "transaction_is_read_only",
            "latest_successful_version",
            "failed_migration_count",
            "hospital_guide_count",
            "companion_session_count",
            "active_companion_session_count");
    static final List<String> EXPECTED_V13_COLUMNS = List.of("v14_backfill_candidate_count");
    static final List<String> EXPECTED_POST_V14_COLUMNS = List.of("unresolved_legacy_snapshot_count");

    private static final Pattern PROJECT_REF_PATTERN = Pattern.compile("[a-z0-9]{20}");
    private static final String COMMON_READINESS_QUERY = """
            select
                current_setting('transaction_read_only')::boolean as transaction_is_read_only,
                (
                    select history.version
                    from bodeul.flyway_schema_history history
                    where history.success
                      and history.version is not null
                    order by history.installed_rank desc
                    limit 1
                ) as latest_successful_version,
                (
                    select count(*)::bigint
                    from bodeul.flyway_schema_history history
                    where not history.success
                ) as failed_migration_count,
                (select count(*)::bigint from bodeul.hospital_guides) as hospital_guide_count,
                (select count(*)::bigint from bodeul.companion_sessions) as companion_session_count,
                (
                    select count(*)::bigint
                    from bodeul.companion_sessions session
                    where session.current_status not in ('COMPLETED', 'CANCELED')
                ) as active_companion_session_count
            """;
    private static final String V13_BACKFILL_QUERY = """
            select count(*)::bigint as v14_backfill_candidate_count
            from bodeul.companion_sessions session
            where session.firestore_id is null
              and session.current_step_order between 0 and 7
            """;
    private static final String POST_V14_UNRESOLVED_QUERY = """
            select count(*)::bigint as unresolved_legacy_snapshot_count
            from bodeul.companion_sessions session
            where session.guide_snapshot_source = 'UNRESOLVED_LEGACY'
            """;

    private DatabaseMigrationReadinessApplication() {
    }

    public static void main(String[] args) {
        ExecutionMode executionMode = executionMode(args);
        Map<String, String> environment = System.getenv();
        TargetConnectionSettings target = requiredTargetSettings(environment);

        validateConnectionTarget(target.jdbcUrl(), target.username(), target.expectedProjectRef());
        if (executionMode == ExecutionMode.TARGET_ONLY) {
            System.out.println("운영 DB 연결 대상 검증을 통과했습니다.");
            return;
        }

        String password = requiredEnvironment(environment, PASSWORD_ENV);

        DatabaseMigrationReadiness readiness;
        try (Connection connection = DriverManager.getConnection(
                target.jdbcUrl(),
                target.username(),
                password)) {
            readiness = runVerification(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("운영 DB migration 사전 점검을 수행할 수 없습니다.");
        }

        System.out.println("운영 DB migration 사전 점검을 통과했습니다.");
        System.out.println("latest_successful_version=" + readiness.latestSuccessfulVersion());
        System.out.println("failed_migration_count=" + readiness.failedMigrationCount());
        System.out.println("hospital_guide_count=" + readiness.hospitalGuideCount());
        System.out.println("companion_session_count=" + readiness.companionSessionCount());
        System.out.println("active_companion_session_count=" + readiness.activeCompanionSessionCount());
        if (readiness.v14BackfillCandidateCount() != null) {
            System.out.println("v14_backfill_candidate_count=" + readiness.v14BackfillCandidateCount());
        }
        if (readiness.unresolvedLegacySnapshotCount() != null) {
            System.out.println("unresolved_legacy_snapshot_count=" + readiness.unresolvedLegacySnapshotCount());
        }
    }

    static ExecutionMode executionMode(String[] args) {
        if (args.length == 0) {
            return ExecutionMode.FULL_READINESS;
        }
        if (args.length == 1 && "--target-only".equals(args[0])) {
            return ExecutionMode.TARGET_ONLY;
        }
        throw new IllegalStateException("지원하지 않는 DB 사전 점검 실행 방식입니다.");
    }

    static TargetConnectionSettings requiredTargetSettings(Map<String, String> environment) {
        return new TargetConnectionSettings(
                requiredEnvironment(environment, JDBC_URL_ENV),
                requiredEnvironment(environment, USERNAME_ENV),
                requiredEnvironment(environment, EXPECTED_PROJECT_REF_ENV));
    }

    static void validateConnectionTarget(String jdbcUrl, String username, String expectedProjectRef) {
        if (expectedProjectRef == null || !PROJECT_REF_PATTERN.matcher(expectedProjectRef).matches()) {
            throw new IllegalStateException("운영 Supabase 프로젝트 확인값이 유효하지 않습니다.");
        }
        if (jdbcUrl == null || username == null) {
            throw new IllegalStateException("운영 Supabase DB 연결 대상을 확인할 수 없습니다.");
        }

        try {
            validateRawConnectionTarget(jdbcUrl, username, expectedProjectRef);

            Properties baseProperties = new Properties();
            PGProperty.USER.set(baseProperties, username);
            Properties parsedProperties = Driver.parseURL(jdbcUrl, baseProperties);
            if (parsedProperties == null) {
                throw invalidConnectionTarget();
            }

            String parsedHost = PGProperty.PG_HOST.getOrNull(parsedProperties);
            String parsedPort = PGProperty.PG_PORT.getOrNull(parsedProperties);
            String parsedDatabase = PGProperty.PG_DBNAME.getOrNull(parsedProperties);
            String parsedUsername = PGProperty.USER.getOrNull(parsedProperties);
            String parsedPassword = PGProperty.PASSWORD.getOrNull(parsedProperties);
            String parsedSslMode = PGProperty.SSL_MODE.getOrNull(parsedProperties);
            if (parsedHost == null
                    || parsedPort == null
                    || !"postgres".equals(parsedDatabase)
                    || !username.equals(parsedUsername)
                    || parsedPassword != null
                    || !"require".equals(parsedSslMode)) {
                throw invalidConnectionTarget();
            }

            if (!isAllowedConnectionTarget(parsedHost, parsedPort, username, expectedProjectRef)) {
                throw invalidConnectionTarget();
            }
        } catch (RuntimeException exception) {
            throw invalidConnectionTarget();
        }
    }

    private static void validateRawConnectionTarget(
            String jdbcUrl,
            String username,
            String expectedProjectRef) {
        if (!jdbcUrl.startsWith("jdbc:")) {
            throw invalidConnectionTarget();
        }

        URI databaseUri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = databaseUri.getHost();
        int port = databaseUri.getPort();
        String rawAuthority = databaseUri.getRawAuthority();
        if (!"postgresql".equals(databaseUri.getScheme())
                || databaseUri.getRawUserInfo() != null
                || databaseUri.getRawFragment() != null
                || host == null
                || host.isBlank()
                || port < 0
                || !"/postgres".equals(databaseUri.getRawPath())
                || !"sslmode=require".equals(databaseUri.getRawQuery())
                || !((host + ":" + port).equals(rawAuthority))
                || !isAllowedConnectionTarget(host, Integer.toString(port), username, expectedProjectRef)) {
            throw invalidConnectionTarget();
        }
    }

    private static boolean isAllowedConnectionTarget(
            String host,
            String port,
            String username,
            String expectedProjectRef) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean directTarget = normalizedHost.equals("db." + expectedProjectRef + ".supabase.co")
                && "bodeul_migrator".equals(username)
                && "5432".equals(port);
        boolean poolerTarget = normalizedHost.endsWith(".pooler.supabase.com")
                && ("bodeul_migrator." + expectedProjectRef).equals(username)
                && ("5432".equals(port) || "6543".equals(port));
        return directTarget || poolerTarget;
    }

    private static IllegalStateException invalidConnectionTarget() {
        return new IllegalStateException("운영 Supabase DB 연결 대상을 확인할 수 없습니다.");
    }

    static DatabaseMigrationReadiness runVerification(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("set transaction read only");
            }
            return verify(connection);
        } finally {
            connection.rollback();
        }
    }

    static DatabaseMigrationReadiness verify(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set local role bodeul_migration");
        }

        CommonReadiness common = readCommonReadiness(connection);
        if ("13".equals(common.latestSuccessfulVersion())) {
            long candidateCount = readSingleCount(
                    connection,
                    V13_BACKFILL_QUERY,
                    EXPECTED_V13_COLUMNS,
                    "v14_backfill_candidate_count");
            return common.withVersionSpecificCounts(candidateCount, null);
        }

        long unresolvedCount = readSingleCount(
                connection,
                POST_V14_UNRESOLVED_QUERY,
                EXPECTED_POST_V14_COLUMNS,
                "unresolved_legacy_snapshot_count");
        return common.withVersionSpecificCounts(null, unresolvedCount);
    }

    private static CommonReadiness readCommonReadiness(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COMMON_READINESS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            verifyColumns(resultSet.getMetaData(), EXPECTED_COMMON_COLUMNS);
            if (!resultSet.next()) {
                throw new IllegalStateException("운영 DB migration 공통 점검 결과가 없습니다.");
            }

            Boolean transactionReadOnly = resultSet.getObject("transaction_is_read_only", Boolean.class);
            if (!Boolean.TRUE.equals(transactionReadOnly)) {
                throw new IllegalStateException("DB 트랜잭션이 읽기 전용이 아닙니다.");
            }

            String latestSuccessfulVersion = resultSet.getString("latest_successful_version");
            if (!ALLOWED_SCHEMA_VERSIONS.contains(latestSuccessfulVersion)) {
                throw new IllegalStateException("허용하지 않은 Flyway 최신 성공 버전입니다.");
            }

            long failedMigrationCount = requiredCount(resultSet, "failed_migration_count");
            if (failedMigrationCount != 0) {
                throw new IllegalStateException("실패한 Flyway migration 이력이 있습니다.");
            }

            CommonReadiness common = new CommonReadiness(
                    latestSuccessfulVersion,
                    failedMigrationCount,
                    requiredCount(resultSet, "hospital_guide_count"),
                    requiredCount(resultSet, "companion_session_count"),
                    requiredCount(resultSet, "active_companion_session_count"));
            if (resultSet.next()) {
                throw new IllegalStateException("운영 DB migration 공통 점검 결과가 한 행을 초과했습니다.");
            }
            return common;
        }
    }

    private static long readSingleCount(
            Connection connection,
            String query,
            List<String> expectedColumns,
            String countColumn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            verifyColumns(resultSet.getMetaData(), expectedColumns);
            if (!resultSet.next()) {
                throw new IllegalStateException("운영 DB migration 버전별 점검 결과가 없습니다.");
            }
            long count = requiredCount(resultSet, countColumn);
            if (resultSet.next()) {
                throw new IllegalStateException("운영 DB migration 버전별 점검 결과가 한 행을 초과했습니다.");
            }
            return count;
        }
    }

    private static void verifyColumns(ResultSetMetaData metadata, List<String> expectedColumns) throws SQLException {
        if (metadata.getColumnCount() != expectedColumns.size()) {
            throw new IllegalStateException("운영 DB migration 사전 점검 열 개수가 일치하지 않습니다.");
        }
        for (int index = 1; index <= expectedColumns.size(); index++) {
            String expected = expectedColumns.get(index - 1);
            if (!expected.equals(metadata.getColumnLabel(index))) {
                throw new IllegalStateException("운영 DB migration 사전 점검 열 계약이 일치하지 않습니다.");
            }
        }
    }

    private static long requiredCount(ResultSet resultSet, String column) throws SQLException {
        Long value = resultSet.getObject(column, Long.class);
        if (value == null || value < 0) {
            throw new IllegalStateException("운영 DB migration 집계값이 유효하지 않습니다.");
        }
        return value;
    }

    static String requiredEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }

    enum ExecutionMode {
        FULL_READINESS,
        TARGET_ONLY
    }

    record TargetConnectionSettings(String jdbcUrl, String username, String expectedProjectRef) {
    }

    private record CommonReadiness(
            String latestSuccessfulVersion,
            long failedMigrationCount,
            long hospitalGuideCount,
            long companionSessionCount,
            long activeCompanionSessionCount) {

        private DatabaseMigrationReadiness withVersionSpecificCounts(
                Long v14BackfillCandidateCount,
                Long unresolvedLegacySnapshotCount) {
            return new DatabaseMigrationReadiness(
                    latestSuccessfulVersion,
                    failedMigrationCount,
                    hospitalGuideCount,
                    companionSessionCount,
                    activeCompanionSessionCount,
                    v14BackfillCandidateCount,
                    unresolvedLegacySnapshotCount);
        }
    }

    record DatabaseMigrationReadiness(
            String latestSuccessfulVersion,
            long failedMigrationCount,
            long hospitalGuideCount,
            long companionSessionCount,
            long activeCompanionSessionCount,
            Long v14BackfillCandidateCount,
            Long unresolvedLegacySnapshotCount) {
    }
}
