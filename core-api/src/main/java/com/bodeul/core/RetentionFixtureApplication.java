package com.bodeul.core;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

public final class RetentionFixtureApplication {

    static final String ACTION_ENV = "RETENTION_FIXTURE_ACTION";
    static final String TARGET_ENV = "MIGRATION_TARGET";
    static final String CONFIRM_PROJECT_ENV = "RETENTION_FIXTURE_CONFIRM_PROJECT";
    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String DB_USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String DB_PASSWORD_ENV = "MIGRATION_DB_PASSWORD";

    static final String PREVIEW_TARGET = "preview";
    static final String PREVIEW_PROJECT = "bodeul-dev";
    static final String PREVIEW_DATABASE_REF = "parpdzttloacinyvhwmx";

    static final String PATIENT_ID = "22222222-2222-4222-8222-222222222201";
    static final String MANAGER_ID = "22222222-2222-4222-8222-222222222202";
    static final String APPOINTMENT_ID = "22222222-2222-4222-8222-222222222210";
    static final String SESSION_ID = "22222222-2222-4222-8222-222222222222";
    static final String MESSAGE_ID = "22222222-2222-4222-8222-222222222230";
    static final String CLIENT_MESSAGE_ID = "33333333-3333-4333-8333-333333333333";
    static final String EXPIRED_ATTACHMENT_ID = "22222222-2222-4222-8222-222222222240";
    static final String HELD_ATTACHMENT_ID = "22222222-2222-4222-8222-222222222241";

    static final String EXPIRED_STORAGE_PATH = "companion-chat-attachments/" + SESSION_ID + "/"
            + CLIENT_MESSAGE_ID + "/0-1ae61c9134c1f8f2464341fda80ed3e9dc0c576059f0b0a5f0ea20721d0956a1.pdf";
    static final String HELD_STORAGE_PATH = "companion-chat-attachments/" + SESSION_ID + "/"
            + CLIENT_MESSAGE_ID + "/1-eae9f590639701e3712051dc97e7756746bffe000ca76f813d77885f5da1cdc3.pdf";

    private RetentionFixtureApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err, RetentionFixtureApplication::execute);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            Map<String, String> environment,
            PrintStream standardOutput,
            PrintStream errorOutput,
            FixtureExecutor executor
    ) {
        try {
            FixtureAction action = fixtureAction(requiredEnvironment(environment, ACTION_ENV));
            String target = requiredEnvironment(environment, TARGET_ENV);
            String confirmedProject = requiredEnvironment(environment, CONFIRM_PROJECT_ENV);
            if (!PREVIEW_TARGET.equals(target) || !PREVIEW_PROJECT.equals(confirmedProject)) {
                throw new FixtureValidationException("preview 개발 프로젝트 확인값이 일치하지 않습니다.");
            }

            String jdbcUrl = requiredEnvironment(environment, JDBC_URL_ENV);
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new FixtureValidationException("PostgreSQL JDBC URL만 사용할 수 있습니다.");
            }
            String databaseUsername = requiredEnvironment(environment, DB_USERNAME_ENV);
            if (!jdbcUrl.contains(PREVIEW_DATABASE_REF)
                    && !databaseUsername.endsWith("." + PREVIEW_DATABASE_REF)) {
                throw new FixtureValidationException("개발 DB 식별자를 확인할 수 없습니다.");
            }
            DatabaseConfig databaseConfig = new DatabaseConfig(
                    jdbcUrl,
                    databaseUsername,
                    requiredEnvironment(environment, DB_PASSWORD_ENV));

            FixtureSummary summary = executor.execute(databaseConfig, action);
            standardOutput.println(summary.format());
            return 0;
        } catch (FixtureValidationException exception) {
            errorOutput.println("자동 파기 fixture 작업을 중단했습니다. " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            reportExecutionFailure(exception, errorOutput);
            return 3;
        }
    }

    private static FixtureAction fixtureAction(String value) throws FixtureValidationException {
        try {
            return FixtureAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new FixtureValidationException("지원하는 작업은 setup, status, cleanup입니다.");
        }
    }

    private static String requiredEnvironment(Map<String, String> environment, String name)
            throws FixtureValidationException {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new FixtureValidationException(name + " 환경변수가 비어 있습니다.");
        }
        return value.trim();
    }

    private static FixtureSummary execute(DatabaseConfig config, FixtureAction action) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                config.jdbcUrl(), config.username(), config.password())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(60);
                statement.execute("set local role bodeul_migration");

                int affectedRows = switch (action) {
                    case SETUP -> setup(statement);
                    case STATUS -> 0;
                    case CLEANUP -> cleanup(statement);
                };
                FixtureSummary summary = status(statement, action, affectedRows);
                connection.commit();
                return summary;
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

    private static int setup(Statement statement) throws SQLException {
        FixtureSummary existing = status(statement, FixtureAction.STATUS, 0);
        if (existing.fixtureRows() != 0) {
            throw new SQLException("retention fixture already exists", "P0001");
        }

        int affectedRows = 0;
        affectedRows += statement.executeUpdate("""
                insert into bodeul.app_users (id, firebase_uid, role, created_at, updated_at)
                values
                    ('%s', 'retention-fixture-core-patient', 'PATIENT', now() - interval '40 days', now()),
                    ('%s', 'retention-fixture-core-manager', 'MANAGER', now() - interval '40 days', now())
                """.formatted(PATIENT_ID, MANAGER_ID));
        affectedRows += statement.executeUpdate("""
                insert into bodeul.appointment_requests (
                    id, firestore_id, patient_user_id, manager_user_id, requester_user_id, requester_role,
                    hospital_name, department_name, appointment_at, appointment_at_epoch_millis,
                    appointment_date_key, mobility_support_code, trip_type_code,
                    manager_gender_preference_code, status, payment_method_code, coupon_code,
                    payment_status_code, created_at, updated_at
                ) values (
                    '%s', 'retention-fixture-core-appointment', '%s', '%s', '%s', 'PATIENT',
                    'Retention Fixture', '검증', now() + interval '1 day',
                    (extract(epoch from now() + interval '1 day') * 1000)::bigint,
                    to_char((now() + interval '1 day') at time zone 'Asia/Seoul', 'YYYY-MM-DD'),
                    'INDEPENDENT', 'ONE_WAY', 'ANY', 'COMPLETED', 'ON_SITE', 'NONE', 'DEFERRED',
                    now() - interval '40 days', now()
                )
                """.formatted(APPOINTMENT_ID, PATIENT_ID, MANAGER_ID, PATIENT_ID));
        affectedRows += statement.executeUpdate("""
                insert into bodeul.companion_sessions (
                    id, firestore_id, appointment_request_id, manager_user_id, current_status,
                    completed_at, created_at, updated_at
                ) values (
                    '%s', 'retention-fixture-core-session', '%s', '%s', 'COMPLETED',
                    now() - interval '31 days', now() - interval '40 days', now()
                )
                """.formatted(SESSION_ID, APPOINTMENT_ID, MANAGER_ID));
        affectedRows += statement.executeUpdate("""
                insert into bodeul.companion_chat_messages (
                    id, companion_session_id, client_message_id, sender_user_id, sender_role,
                    body, sent_at, expires_at, legal_hold_until, created_at
                ) values (
                    '%s', '%s', '%s', '%s', 'PATIENT',
                    'retention fixture message', now() - interval '40 days', now() - interval '1 day',
                    now() + interval '7 days', now() - interval '40 days'
                )
                """.formatted(MESSAGE_ID, SESSION_ID, CLIENT_MESSAGE_ID, PATIENT_ID));
        affectedRows += statement.executeUpdate("""
                insert into bodeul.companion_chat_attachments (
                    id, chat_message_id, storage_path, file_name, content_type, size_bytes,
                    status, expires_at, legal_hold_until, created_at
                ) values
                    ('%s', '%s', '%s', 'retention-expired.pdf', 'application/pdf', 50,
                     'ACTIVE', now() - interval '1 day', null, now() - interval '40 days'),
                    ('%s', '%s', '%s', 'retention-legal-hold.pdf', 'application/pdf', 53,
                     'ACTIVE', now() - interval '1 day', now() + interval '7 days', now() - interval '40 days')
                """.formatted(
                EXPIRED_ATTACHMENT_ID, MESSAGE_ID, EXPIRED_STORAGE_PATH,
                HELD_ATTACHMENT_ID, MESSAGE_ID, HELD_STORAGE_PATH));
        return affectedRows;
    }

    private static int cleanup(Statement statement) throws SQLException {
        int affectedRows = 0;
        affectedRows += statement.executeUpdate("""
                delete from bodeul.companion_chat_attachments
                where id in ('%s', '%s')
                """.formatted(EXPIRED_ATTACHMENT_ID, HELD_ATTACHMENT_ID));
        affectedRows += statement.executeUpdate("""
                delete from bodeul.companion_chat_messages where id = '%s'
                """.formatted(MESSAGE_ID));
        affectedRows += statement.executeUpdate("""
                delete from bodeul.companion_sessions where id = '%s'
                """.formatted(SESSION_ID));
        affectedRows += statement.executeUpdate("""
                delete from bodeul.appointment_requests where id = '%s'
                """.formatted(APPOINTMENT_ID));
        affectedRows += statement.executeUpdate("""
                delete from bodeul.app_users where id in ('%s', '%s')
                """.formatted(PATIENT_ID, MANAGER_ID));
        return affectedRows;
    }

    private static FixtureSummary status(Statement statement, FixtureAction action, int affectedRows)
            throws SQLException {
        String query = """
                select
                    (
                        (select count(*) from bodeul.app_users where id in ('%s', '%s'))
                        + (select count(*) from bodeul.appointment_requests where id = '%s')
                        + (select count(*) from bodeul.companion_sessions where id = '%s')
                        + (select count(*) from bodeul.companion_chat_messages where id = '%s')
                        + (select count(*) from bodeul.companion_chat_attachments where id in ('%s', '%s'))
                    )::integer as fixture_rows,
                    coalesce((select status from bodeul.companion_chat_attachments where id = '%s'), 'MISSING')
                        as expired_status,
                    coalesce((select storage_path like 'deleted/%%'
                              from bodeul.companion_chat_attachments where id = '%s'), false)
                        as expired_path_redacted,
                    coalesce((select deleted_at is not null
                              from bodeul.companion_chat_attachments where id = '%s'), false)
                        as expired_deleted_at_set,
                    coalesce((select size_bytes = 0
                              from bodeul.companion_chat_attachments where id = '%s'), false)
                        as expired_size_cleared,
                    coalesce((select status from bodeul.companion_chat_attachments where id = '%s'), 'MISSING')
                        as held_status,
                    coalesce((select storage_path = '%s'
                              from bodeul.companion_chat_attachments where id = '%s'), false)
                        as held_path_preserved,
                    preview.attachment_candidates::integer,
                    preview.legal_hold_skips::integer
                from bodeul.preview_expired_companion_data(now()) preview
                """.formatted(
                PATIENT_ID, MANAGER_ID, APPOINTMENT_ID, SESSION_ID, MESSAGE_ID,
                EXPIRED_ATTACHMENT_ID, HELD_ATTACHMENT_ID,
                EXPIRED_ATTACHMENT_ID, EXPIRED_ATTACHMENT_ID, EXPIRED_ATTACHMENT_ID,
                EXPIRED_ATTACHMENT_ID, HELD_ATTACHMENT_ID, HELD_STORAGE_PATH, HELD_ATTACHMENT_ID);

        try (ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new SQLException("retention fixture status unavailable", "P0002");
            }
            return new FixtureSummary(
                    action,
                    affectedRows,
                    result.getInt("fixture_rows"),
                    result.getString("expired_status"),
                    result.getBoolean("expired_path_redacted"),
                    result.getBoolean("expired_deleted_at_set"),
                    result.getBoolean("expired_size_cleared"),
                    result.getString("held_status"),
                    result.getBoolean("held_path_preserved"),
                    result.getInt("attachment_candidates"),
                    result.getInt("legal_hold_skips"));
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
                    "자동 파기 fixture DB 실행에 실패했습니다. SQLSTATE=%s, vendorCode=%d%n",
                    sqlState,
                    sqlException.getErrorCode());
            return;
        }
        errorOutput.println("자동 파기 fixture 실행에 실패했습니다. 오류 유형="
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

    enum FixtureAction {
        SETUP,
        STATUS,
        CLEANUP
    }

    record DatabaseConfig(String jdbcUrl, String username, String password) {
    }

    record FixtureSummary(
            FixtureAction action,
            int affectedRows,
            int fixtureRows,
            String expiredStatus,
            boolean expiredPathRedacted,
            boolean expiredDeletedAtSet,
            boolean expiredSizeCleared,
            String heldStatus,
            boolean heldPathPreserved,
            int attachmentCandidates,
            int legalHoldSkips
    ) {
        String format() {
            return String.format(
                    Locale.ROOT,
                    "자동 파기 fixture action=%s affectedRows=%d fixtureRows=%d "
                            + "expiredStatus=%s expiredPathRedacted=%s expiredDeletedAtSet=%s "
                            + "expiredSizeCleared=%s heldStatus=%s heldPathPreserved=%s "
                            + "attachmentCandidates=%d legalHoldSkips=%d",
                    action,
                    affectedRows,
                    fixtureRows,
                    expiredStatus,
                    expiredPathRedacted,
                    expiredDeletedAtSet,
                    expiredSizeCleared,
                    heldStatus,
                    heldPathPreserved,
                    attachmentCandidates,
                    legalHoldSkips);
        }
    }

    @FunctionalInterface
    interface FixtureExecutor {
        FixtureSummary execute(DatabaseConfig databaseConfig, FixtureAction action) throws Exception;
    }

    static final class FixtureValidationException extends Exception {
        FixtureValidationException(String message) {
            super(message);
        }
    }
}
