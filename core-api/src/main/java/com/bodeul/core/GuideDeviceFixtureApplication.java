package com.bodeul.core;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GuideDeviceFixtureApplication {

    static final String ACTION_ENV = "GUIDE_DEVICE_FIXTURE_ACTION";
    static final String TARGET_ENV = "MIGRATION_TARGET";
    static final String CONFIRM_PROJECT_ENV = "GUIDE_DEVICE_FIXTURE_CONFIRM_PROJECT";
    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String DB_USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String DB_PASSWORD_ENV = "MIGRATION_DB_PASSWORD";

    static final String PREVIEW_TARGET = "preview";
    static final String PREVIEW_PROJECT = "bodeul-dev";
    static final String PREVIEW_DATABASE_REF = "parpdzttloacinyvhwmx";

    static final String PATIENT_ID = "44444444-4444-4444-8444-444444444401";
    static final String GUIDE_ID = "44444444-4444-4444-8444-444444444402";
    static final String APPOINTMENT_ID = "44444444-4444-4444-8444-444444444403";
    static final String CLIENT_REQUEST_ID = "44444444-4444-4444-8444-444444444404";
    static final String PATIENT_FIREBASE_UID = "guide-device-fixture-patient";
    static final String MANAGER_EMAIL = "manager@bodeul.app";
    static final String ADMIN_EMAIL = "admin@bodeul.app";
    static final String HOSPITAL_NAME = "BoDeul 실기기 검증병원";
    static final String DEPARTMENT_NAME = "약국 이동 검증";

    static final String GUIDE_STEPS_JSON = """
            [
              {"code":"MEETING_CONFIRMATION","order":1,"title":"상봉 확인","description":"매니저와 환자의 상봉을 확인합니다."},
              {"code":"HOSPITAL_ROUTE","order":2,"title":"병원 이동","description":"병원 로비와 진료과까지 이동합니다."},
              {"code":"RECEPTION_QUEUE","order":3,"title":"접수와 대기","description":"접수 상태와 대기 순서를 확인합니다."},
              {"code":"VITALS_CHECK","order":4,"title":"기초 측정","description":"병원에서 확인한 기초 측정값을 기록합니다."},
              {"code":"PRE_CONSULTATION","order":5,"title":"진료 전 확인","description":"증상과 전달 사항을 진료 전에 확인합니다."},
              {"code":"CONSULTATION_SUPPORT","order":6,"title":"진료 동행","description":"진료 중 핵심 안내를 기록합니다."},
              {"code":"CONSULTATION_SUMMARY","order":7,"title":"진료 요약","description":"진료 요약을 검토하고 공유 내용을 확인합니다."},
              {"code":"PAYMENT_EVIDENCE","order":8,"title":"수납 증빙","description":"수납 완료와 결제 증빙을 확인합니다."},
              {"code":"PHARMACY_ROUTE","order":9,"title":"약국 이동","description":"처방전을 기준으로 주변 약국을 찾습니다."},
              {"code":"PRESCRIPTION_DOCUMENTS","order":10,"title":"처방 자료","description":"처방 관련 이미지 자료를 등록합니다."},
              {"code":"MEDICATION_CONFIRMATION","order":11,"title":"복약 확인","description":"약 수령과 복약 안내 완료 여부를 확인합니다."},
              {"code":"CARE_COMPLETION","order":12,"title":"동행 종료","description":"환자 상태와 인계 내용을 최종 확인합니다."},
              {"code":"MANAGER_JOURNAL","order":13,"title":"매니저 일지","description":"동행 내용을 정리하고 최종 일지를 작성합니다."}
            ]
            """;

    private GuideDeviceFixtureApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err, GuideDeviceFixtureApplication::execute);
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
            errorOutput.println("가이드 실기기 fixture 작업을 중단했습니다. " + exception.getMessage());
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
            }

            try {
                int affectedRows = switch (action) {
                    case SETUP -> setup(connection);
                    case STATUS -> 0;
                    case CLEANUP -> cleanup(connection);
                };
                FixtureSummary summary = status(connection, action, affectedRows);
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

    private static int setup(Connection connection) throws SQLException {
        FixtureSummary existing = status(connection, FixtureAction.STATUS, 0);
        if (existing.fixtureRows() != 0) {
            throw new SQLException("guide device fixture already exists", "P0001");
        }

        UUID managerUserId = findBaselineUser(connection, MANAGER_EMAIL, "MANAGER");
        UUID adminUserId = findBaselineUser(connection, ADMIN_EMAIL, "ADMIN");
        lockSessionWrites(connection);
        if (countActiveSessions(connection, managerUserId) != 0) {
            throw new SQLException("baseline manager already has an active session", "P0001");
        }

        int affectedRows = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into bodeul.app_users (
                    id, firebase_uid, role, name, email, phone, created_at, updated_at
                ) values (?::uuid, ?, 'PATIENT', '실기기 검증 환자', '', '', now(), now())
                """)) {
            statement.setString(1, PATIENT_ID);
            statement.setString(2, PATIENT_FIREBASE_UID);
            affectedRows += statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                insert into bodeul.hospital_guides (
                    id, hospital_name, department_name, steps,
                    revision, step_contract_version, created_at, updated_at
                ) values (?::uuid, ?, ?, ?::jsonb, 1, 1, now(), now())
                """)) {
            statement.setString(1, GUIDE_ID);
            statement.setString(2, HOSPITAL_NAME);
            statement.setString(3, DEPARTMENT_NAME);
            statement.setString(4, GUIDE_STEPS_JSON);
            affectedRows += statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                insert into bodeul.appointment_requests (
                    id, patient_user_id, requester_user_id, requester_role,
                    patient_name, hospital_name, department_name,
                    appointment_at, appointment_at_epoch_millis, appointment_date_key,
                    meeting_place, mobility_support_code, trip_type_code,
                    manager_gender_preference_code, status,
                    base_price, option_surcharge_price, coupon_discount_price, final_price,
                    payment_method_code, coupon_code, payment_status_code,
                    client_request_id, created_at, updated_at
                ) values (
                    ?::uuid, ?::uuid, ?::uuid, 'PATIENT',
                    '실기기 검증 환자', ?, ?,
                    now() + interval '1 day',
                    (extract(epoch from now() + interval '1 day') * 1000)::bigint,
                    to_char((now() + interval '1 day') at time zone 'Asia/Seoul', 'YYYY-MM-DD'),
                    '검증병원 1층 안내 데스크', 'INDEPENDENT', 'ONE_WAY',
                    'ANY', 'REQUESTED',
                    69000, 0, 0, 69000,
                    'ON_SITE', 'NONE', 'DEFERRED',
                    ?::uuid, now(), now()
                )
                """)) {
            statement.setString(1, APPOINTMENT_ID);
            statement.setString(2, PATIENT_ID);
            statement.setString(3, PATIENT_ID);
            statement.setString(4, HOSPITAL_NAME);
            statement.setString(5, DEPARTMENT_NAME);
            statement.setString(6, CLIENT_REQUEST_ID);
            affectedRows += statement.executeUpdate();
        }

        UUID sessionId;
        try (PreparedStatement statement = connection.prepareStatement(
                "select bodeul.assign_companion_session(?::uuid, ?::uuid, ?::uuid, 0, ?)")) {
            statement.setString(1, APPOINTMENT_ID);
            statement.setObject(2, managerUserId);
            statement.setObject(3, adminUserId);
            statement.setString(4, "PR #329 약국 딥링크 실기기 검증");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("assignment did not return a session", "P0002");
                }
                sessionId = resultSet.getObject(1, UUID.class);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                update bodeul.companion_sessions
                set current_step_order = 9,
                    current_status = 'PAYMENT',
                    started_at = now(),
                    updated_at = now(),
                    version = 9
                where id = ?::uuid
                """)) {
            statement.setObject(1, sessionId);
            affectedRows += requireSingleUpdate(statement, "fixture session progress");
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                update bodeul.appointment_requests
                set status = 'IN_PROGRESS', updated_at = now(), version = version + 1
                where id = ?::uuid and status = 'MATCHED'
                """)) {
            statement.setString(1, APPOINTMENT_ID);
            affectedRows += requireSingleUpdate(statement, "fixture appointment progress");
        }
        if (countActiveSessions(connection, managerUserId) != 1) {
            throw new SQLException("baseline manager active session changed during setup", "P0001");
        }
        return affectedRows;
    }

    private static void lockSessionWrites(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            statement.execute("lock table bodeul.companion_sessions in share row exclusive mode");
        }
    }

    private static UUID findBaselineUser(Connection connection, String email, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select id
                from bodeul.app_users
                where role = ? and lower(email) = ?
                order by id
                limit 2
                """)) {
            statement.setString(1, role);
            statement.setString(2, email.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("baseline user is missing", "P0002");
                }
                UUID userId = resultSet.getObject(1, UUID.class);
                if (resultSet.next()) {
                    throw new SQLException("baseline user is not unique", "P0003");
                }
                return userId;
            }
        }
    }

    private static int countActiveSessions(Connection connection, UUID managerUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from bodeul.companion_sessions
                where manager_user_id = ?::uuid
                  and current_status not in ('COMPLETED', 'CANCELED')
                """)) {
            statement.setObject(1, managerUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int requireSingleUpdate(PreparedStatement statement, String operation) throws SQLException {
        int affectedRows = statement.executeUpdate();
        if (affectedRows != 1) {
            throw new SQLException(operation + " affected unexpected rows", "P0002");
        }
        return affectedRows;
    }

    private static int cleanup(Connection connection) throws SQLException {
        lockSessionWrites(connection);
        requireOwnedFixture(connection);
        int attachmentCount = queryCount(connection, """
                select count(*)
                from bodeul.companion_chat_attachments attachment
                join bodeul.companion_chat_messages message on message.id = attachment.chat_message_id
                join bodeul.companion_sessions session on session.id = message.companion_session_id
                where session.appointment_request_id = ?::uuid
                """, APPOINTMENT_ID);
        if (attachmentCount != 0) {
            throw new SQLException("fixture has storage-backed attachments", "P0001");
        }

        int affectedRows = 0;
        affectedRows += executeUpdate(connection, """
                delete from bodeul.companion_chat_read_receipts
                where companion_session_id in (
                    select id from bodeul.companion_sessions
                    where appointment_request_id = ?::uuid
                )
                """, APPOINTMENT_ID);
        affectedRows += executeUpdate(connection, """
                delete from bodeul.companion_chat_messages
                where companion_session_id in (
                    select id from bodeul.companion_sessions
                    where appointment_request_id = ?::uuid
                )
                """, APPOINTMENT_ID);
        affectedRows += executeUpdate(connection, """
                delete from bodeul.companion_session_locations
                where companion_session_id in (
                    select id from bodeul.companion_sessions
                    where appointment_request_id = ?::uuid
                )
                """, APPOINTMENT_ID);
        affectedRows += executeUpdate(connection, """
                delete from bodeul.session_reports
                where companion_session_id in (
                    select id from bodeul.companion_sessions
                    where appointment_request_id = ?::uuid
                )
                """, APPOINTMENT_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.companion_session_assignment_audits where appointment_request_id = ?::uuid",
                APPOINTMENT_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.companion_sessions where appointment_request_id = ?::uuid",
                APPOINTMENT_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.appointment_follow_ups where appointment_request_id = ?::uuid",
                APPOINTMENT_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.appointment_requests where id = ?::uuid",
                APPOINTMENT_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.hospital_guides where id = ?::uuid",
                GUIDE_ID);
        affectedRows += executeUpdate(connection,
                "delete from bodeul.app_users where id = ?::uuid and firebase_uid = ?",
                PATIENT_ID,
                PATIENT_FIREBASE_UID);
        return affectedRows;
    }

    private static void requireOwnedFixture(Connection connection) throws SQLException {
        FixtureSummary summary = status(connection, FixtureAction.STATUS, 0);
        if (summary.fixtureRows() == 0) {
            return;
        }

        String query = """
                select (
                    (select count(*) from bodeul.app_users
                     where id = ?::uuid and firebase_uid = ? and role = 'PATIENT')
                    + (select count(*) from bodeul.hospital_guides
                       where id = ?::uuid and hospital_name = ? and department_name = ?
                         and step_contract_version = 1)
                    + (select count(*) from bodeul.appointment_requests
                       where id = ?::uuid and patient_user_id = ?::uuid
                         and requester_user_id = ?::uuid and client_request_id = ?::uuid
                         and hospital_name = ? and department_name = ?)
                    + (select count(*) from bodeul.companion_sessions
                       where appointment_request_id = ?::uuid and guide_id = ?::uuid
                         and guide_snapshot_source = 'HOSPITAL_GUIDE_STEP_CODE_V1')
                    + (select count(*) from bodeul.companion_session_assignment_audits
                       where appointment_request_id = ?::uuid
                         and reason = 'PR #329 약국 딥링크 실기기 검증')
                )::integer
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            statement.setString(index++, PATIENT_ID);
            statement.setString(index++, PATIENT_FIREBASE_UID);
            statement.setString(index++, GUIDE_ID);
            statement.setString(index++, HOSPITAL_NAME);
            statement.setString(index++, DEPARTMENT_NAME);
            statement.setString(index++, APPOINTMENT_ID);
            statement.setString(index++, PATIENT_ID);
            statement.setString(index++, PATIENT_ID);
            statement.setString(index++, CLIENT_REQUEST_ID);
            statement.setString(index++, HOSPITAL_NAME);
            statement.setString(index++, DEPARTMENT_NAME);
            statement.setString(index++, APPOINTMENT_ID);
            statement.setString(index++, GUIDE_ID);
            statement.setString(index, APPOINTMENT_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (summary.fixtureRows() != 5 || resultSet.getInt(1) != 5) {
                    throw new SQLException("fixture ownership markers do not match", "P0001");
                }
            }
        }
    }

    private static int queryCount(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int executeUpdate(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            return statement.executeUpdate();
        }
    }

    private static FixtureSummary status(Connection connection, FixtureAction action, int affectedRows)
            throws SQLException {
        String query = """
                select
                    (
                        (select count(*) from bodeul.app_users where id = ?::uuid)
                        + (select count(*) from bodeul.hospital_guides where id = ?::uuid)
                        + (select count(*) from bodeul.appointment_requests where id = ?::uuid)
                        + (select count(*) from bodeul.companion_sessions where appointment_request_id = ?::uuid)
                        + (select count(*) from bodeul.companion_session_assignment_audits
                           where appointment_request_id = ?::uuid)
                    )::integer as fixture_rows,
                    coalesce((select current_step_order from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), -1) as current_step_order,
                    coalesce((select guide_steps_snapshot -> (current_step_order - 1) ->> 'code'
                              from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), 'MISSING') as current_step_code,
                    coalesce((select guide_snapshot_source from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), 'MISSING') as snapshot_source,
                    coalesce((select jsonb_array_length(guide_steps_snapshot)
                              from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), 0) as step_count,
                    coalesce((select guide_step_contract_version from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), 0) as contract_version,
                    coalesce((select current_status from bodeul.companion_sessions
                              where appointment_request_id = ?::uuid), 'MISSING') as current_status
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, PATIENT_ID);
            statement.setString(2, GUIDE_ID);
            for (int index = 3; index <= 11; index++) {
                statement.setString(index, APPOINTMENT_ID);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                int fixtureRows = resultSet.getInt("fixture_rows");
                int currentStepOrder = resultSet.getInt("current_step_order");
                String currentStepCode = resultSet.getString("current_step_code");
                String snapshotSource = resultSet.getString("snapshot_source");
                int stepCount = resultSet.getInt("step_count");
                int contractVersion = resultSet.getInt("contract_version");
                String currentStatus = resultSet.getString("current_status");
                boolean ready = fixtureRows == 5
                        && currentStepOrder == 9
                        && "PHARMACY_ROUTE".equals(currentStepCode)
                        && "HOSPITAL_GUIDE_STEP_CODE_V1".equals(snapshotSource)
                        && stepCount == 13
                        && contractVersion == 1
                        && "PAYMENT".equals(currentStatus);
                return new FixtureSummary(
                        action,
                        affectedRows,
                        fixtureRows,
                        currentStepOrder,
                        currentStepCode,
                        snapshotSource,
                        stepCount,
                        contractVersion,
                        currentStatus,
                        ready);
            }
        }
    }

    private static void reportExecutionFailure(Exception exception, PrintStream errorOutput) {
        SQLException sqlException = findSqlException(exception);
        if (sqlException != null) {
            String sqlState = sqlException.getSQLState();
            errorOutput.println("가이드 실기기 fixture DB 작업에 실패했습니다. SQLSTATE="
                    + (sqlState == null ? "unknown" : sqlState));
            return;
        }
        errorOutput.println("가이드 실기기 fixture 작업에 실패했습니다. 원인 유형="
                + exception.getClass().getSimpleName());
    }

    private static SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
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
            int currentStepOrder,
            String currentStepCode,
            String snapshotSource,
            int stepCount,
            int contractVersion,
            String currentStatus,
            boolean ready
    ) {
        String format() {
            return ("action=%s affectedRows=%d fixtureRows=%d currentStepOrder=%d "
                    + "currentStepCode=%s snapshotSource=%s stepCount=%d contractVersion=%d "
                    + "currentStatus=%s ready=%s")
                    .formatted(
                            action,
                            affectedRows,
                            fixtureRows,
                            currentStepOrder,
                            currentStepCode,
                            snapshotSource,
                            stepCount,
                            contractVersion,
                            currentStatus,
                            ready);
        }
    }

    @FunctionalInterface
    interface FixtureExecutor {
        FixtureSummary execute(DatabaseConfig databaseConfig, FixtureAction action) throws Exception;
    }

    private static final class FixtureValidationException extends Exception {
        private FixtureValidationException(String message) {
            super(message);
        }
    }
}
