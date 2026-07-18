package com.bodeul.core;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompanionSessionSeedApplication {

    static final String SEED_FILE_ENV = "COMPANION_SESSION_SEED_SQL_FILE";
    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String DB_USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String DB_PASSWORD_ENV = "MIGRATION_DB_PASSWORD";

    private static final long MAX_SEED_BYTES = 1024L * 1024L;
    private static final int MAX_UPSERTS = 300;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern UPSERT_SHAPE = Pattern.compile(
            "^insert\\s+into\\s+(bodeul\\.(?:companion_sessions|session_reports|appointment_follow_ups))"
                    + "\\s*\\(.+\\)\\s+values\\s*\\(.+\\)\\s+on\\s+conflict"
                    + "\\s*\\(\\s*\"?([a-z_]+)\"?\\s*\\)\\s+do\\s+update\\s+set\\s+.+$");
    private static final Pattern FORBIDDEN_KEYWORD = Pattern.compile(
            "\\b(create|alter|drop|truncate|grant|revoke|copy|delete|select|merge|call|execute|returning|with|"
                    + "vacuum|analyze|comment|refresh|lock|listen|notify|prepare|deallocate)\\b");
    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\s*\\.");
    private static final Pattern QUOTED_QUALIFIER = Pattern.compile("\"[^\"]+\"\\s*\\.");
    private static final Pattern RESTRICTED_FUNCTION = Pattern.compile(
            "\\b(pg_[a-z0-9_]+|current_setting|set_config)\\s*\\(");
    private static final Set<String> ALLOWED_QUALIFIERS = Set.of("bodeul", "excluded");
    private static final Map<String, String> CONFLICT_COLUMNS = Map.of(
            "bodeul.companion_sessions", "firestore_id",
            "bodeul.session_reports", "firestore_id",
            "bodeul.appointment_follow_ups", "appointment_request_id");
    private static final Map<String, Integer> TABLE_ORDER = Map.of(
            "bodeul.companion_sessions", 0,
            "bodeul.session_reports", 1,
            "bodeul.appointment_follow_ups", 2);

    private CompanionSessionSeedApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err,
                CompanionSessionSeedApplication::executeSeed);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            Map<String, String> environment,
            PrintStream standardOutput,
            PrintStream errorOutput,
            SeedExecutor executor
    ) {
        try {
            String jdbcUrl = requiredEnvironment(environment, JDBC_URL_ENV);
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new SeedValidationException("PostgreSQL JDBC URL만 사용할 수 있습니다.");
            }
            DatabaseConfig databaseConfig = new DatabaseConfig(
                    jdbcUrl,
                    requiredEnvironment(environment, DB_USERNAME_ENV),
                    requiredEnvironment(environment, DB_PASSWORD_ENV));
            Path seedPath = seedPath(requiredEnvironment(environment, SEED_FILE_ENV));
            List<String> upserts = validateSeedSql(readSeedSql(seedPath));

            executor.execute(databaseConfig, upserts);
            standardOutput.printf("동행 세션 preview 백필을 완료했습니다. 적용 문장: %d건%n", upserts.size());
            return 0;
        } catch (SeedValidationException exception) {
            errorOutput.println("동행 세션 preview 백필을 중단했습니다. " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            reportExecutionFailure(exception, errorOutput);
            return 3;
        }
    }

    static List<String> validateSeedSql(String sql) throws SeedValidationException {
        List<String> statements = splitSqlStatements(sql);
        if (statements.size() < 4) {
            throw new SeedValidationException("트랜잭션과 동행 세션 upsert 문장이 모두 필요합니다.");
        }
        if (statements.size() > MAX_UPSERTS + 3) {
            throw new SeedValidationException("한 번에 적용할 수 있는 upsert 수를 초과했습니다.");
        }
        if (!"begin".equals(normalizeForValidation(statements.get(0)))) {
            throw new SeedValidationException("첫 문장은 begin이어야 합니다.");
        }
        if (!"set local role bodeul_migration".equals(normalizeForValidation(statements.get(1)))) {
            throw new SeedValidationException("migration role 전환 문장이 필요합니다.");
        }
        if (!"commit".equals(normalizeForValidation(statements.get(statements.size() - 1)))) {
            throw new SeedValidationException("마지막 문장은 commit이어야 합니다.");
        }

        List<String> upserts = new ArrayList<>();
        Map<String, Integer> tableCounts = new HashMap<>();
        int previousOrder = -1;
        for (int index = 2; index < statements.size() - 1; index++) {
            String statement = statements.get(index);
            String table = validateUpsert(statement);
            int order = TABLE_ORDER.get(table);
            if (order < previousOrder) {
                throw new SeedValidationException("FK 순서와 다른 테이블 upsert가 포함되어 있습니다.");
            }
            previousOrder = order;
            tableCounts.merge(table, 1, Integer::sum);
            upserts.add(statement);
        }
        if (tableCounts.getOrDefault("bodeul.companion_sessions", 0) == 0) {
            throw new SeedValidationException("적용할 동행 세션 upsert가 없습니다.");
        }
        return List.copyOf(upserts);
    }

    private static String validateUpsert(String statement) throws SeedValidationException {
        String normalized = normalizeForValidation(statement);
        Matcher shapeMatcher = UPSERT_SHAPE.matcher(normalized);
        if (!shapeMatcher.matches()) {
            throw new SeedValidationException("허용된 동행 세션 upsert 형식이 아닙니다.");
        }
        String table = shapeMatcher.group(1);
        String conflictColumn = shapeMatcher.group(2);
        if (!CONFLICT_COLUMNS.get(table).equals(conflictColumn)) {
            throw new SeedValidationException("테이블의 고정 conflict key와 일치하지 않습니다.");
        }
        if (!normalized.contains("\"imported_at\"") || !normalized.contains("\"imported_at\" = now()")) {
            throw new SeedValidationException("백필 시각 기록이 누락됐습니다.");
        }
        if (FORBIDDEN_KEYWORD.matcher(normalized).find()
                || RESTRICTED_FUNCTION.matcher(normalized).find()
                || QUOTED_QUALIFIER.matcher(normalized).find()
                || normalized.indexOf(';') >= 0
                || normalized.indexOf('$') >= 0
                || normalized.indexOf('\\') >= 0) {
            throw new SeedValidationException("허용되지 않은 SQL 구문이 포함되어 있습니다.");
        }
        if (countWord(normalized, "insert") != 1
                || countWord(normalized, "into") != 1
                || countWord(normalized, "values") != 1
                || countWord(normalized, "conflict") != 1
                || countWord(normalized, "update") != 1
                || countWord(normalized, "set") != 1
                || countOccurrences(normalized, table) != 1) {
            throw new SeedValidationException("동행 세션 백필 범위를 벗어났습니다.");
        }

        Matcher qualifierMatcher = QUALIFIED_IDENTIFIER.matcher(normalized);
        while (qualifierMatcher.find()) {
            if (!ALLOWED_QUALIFIERS.contains(qualifierMatcher.group(1))) {
                throw new SeedValidationException("다른 schema 또는 객체 참조는 허용되지 않습니다.");
            }
        }
        return table;
    }

    private static Path seedPath(String configuredPath) throws SeedValidationException {
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new SeedValidationException("seed 파일이 일반 파일이 아닙니다.");
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new SeedValidationException("seed 파일 경로 형식이 올바르지 않습니다.");
        }
    }

    private static String readSeedSql(Path path) throws Exception {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_SEED_BYTES) {
            throw new SeedValidationException("seed 파일 크기가 허용 범위를 벗어났습니다.");
        }
        byte[] bytes = Files.readAllBytes(path);
        try {
            String sql = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return sql.startsWith("\uFEFF") ? sql.substring(1) : sql;
        } catch (CharacterCodingException exception) {
            throw new SeedValidationException("seed 파일이 올바른 UTF-8이 아닙니다.");
        }
    }

    private static List<String> splitSqlStatements(String sql) throws SeedValidationException {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        ScanState state = ScanState.NORMAL;
        int blockCommentDepth = 0;

        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            switch (state) {
                case NORMAL -> {
                    if (character == '-' && next == '-') {
                        current.append(' ');
                        state = ScanState.LINE_COMMENT;
                        index++;
                    } else if (character == '/' && next == '*') {
                        current.append(' ');
                        state = ScanState.BLOCK_COMMENT;
                        blockCommentDepth = 1;
                        index++;
                    } else if (character == '\'') {
                        current.append(character);
                        state = ScanState.SINGLE_QUOTE;
                    } else if (character == '"') {
                        current.append(character);
                        state = ScanState.DOUBLE_QUOTE;
                    } else if (character == ';') {
                        addStatement(statements, current);
                    } else {
                        current.append(character);
                    }
                }
                case LINE_COMMENT -> {
                    if (character == '\n' || character == '\r') {
                        current.append(character);
                        state = ScanState.NORMAL;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (character == '/' && next == '*') {
                        blockCommentDepth++;
                        index++;
                    } else if (character == '*' && next == '/') {
                        blockCommentDepth--;
                        index++;
                        if (blockCommentDepth == 0) {
                            current.append(' ');
                            state = ScanState.NORMAL;
                        }
                    }
                }
                case SINGLE_QUOTE -> {
                    current.append(character);
                    if (character == '\'' && next == '\'') {
                        current.append(next);
                        index++;
                    } else if (character == '\'') {
                        state = ScanState.NORMAL;
                    }
                }
                case DOUBLE_QUOTE -> {
                    current.append(character);
                    if (character == '"' && next == '"') {
                        current.append(next);
                        index++;
                    } else if (character == '"') {
                        state = ScanState.NORMAL;
                    }
                }
            }
        }
        if (state == ScanState.LINE_COMMENT) {
            state = ScanState.NORMAL;
        }
        if (state != ScanState.NORMAL) {
            throw new SeedValidationException("닫히지 않은 문자열 또는 주석이 있습니다.");
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    private static String normalizeForValidation(String statement) throws SeedValidationException {
        StringBuilder sanitized = new StringBuilder(statement.length());
        boolean inString = false;
        for (int index = 0; index < statement.length(); index++) {
            char character = statement.charAt(index);
            char next = index + 1 < statement.length() ? statement.charAt(index + 1) : '\0';
            if (!inString && character == '\'') {
                sanitized.append("''");
                inString = true;
            } else if (inString && character == '\'' && next == '\'') {
                index++;
            } else if (inString && character == '\'') {
                inString = false;
            } else if (!inString) {
                sanitized.append(character);
            }
        }
        if (inString) {
            throw new SeedValidationException("닫히지 않은 문자열이 있습니다.");
        }
        return WHITESPACE.matcher(sanitized.toString().toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static int countWord(String value, String word) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countOccurrences(String value, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }

    private static String requiredEnvironment(Map<String, String> environment, String name)
            throws SeedValidationException {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new SeedValidationException(name + " 환경변수가 비어 있습니다.");
        }
        return value;
    }

    private static void executeSeed(DatabaseConfig databaseConfig, List<String> upserts) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseConfig.jdbcUrl(), databaseConfig.username(), databaseConfig.password())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(60);
                statement.execute("set local role bodeul_migration");
                for (String upsert : upserts) {
                    int affectedRows = statement.executeUpdate(upsert);
                    if (affectedRows != 1) {
                        throw new SQLException("unexpected affected row count", "P0001");
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    private static void reportExecutionFailure(Exception exception, PrintStream errorOutput) {
        SQLException sqlException = findSqlException(exception);
        if (sqlException != null) {
            String sqlState = sqlException.getSQLState();
            if (sqlState == null || !sqlState.matches("[0-9A-Z]{5}")) {
                sqlState = "unknown";
            }
            errorOutput.printf(
                    "동행 세션 preview 백필 DB 실행에 실패했습니다. SQLSTATE=%s, vendorCode=%d%n",
                    sqlState,
                    sqlException.getErrorCode());
            return;
        }
        errorOutput.println("동행 세션 preview 백필 실행에 실패했습니다. 오류 유형="
                + exception.getClass().getSimpleName());
    }

    private static SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    record DatabaseConfig(String jdbcUrl, String username, String password) {
    }

    @FunctionalInterface
    interface SeedExecutor {
        void execute(DatabaseConfig databaseConfig, List<String> upserts) throws Exception;
    }

    static final class SeedValidationException extends Exception {
        SeedValidationException(String message) {
            super(message);
        }
    }

    private enum ScanState {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE
    }
}
