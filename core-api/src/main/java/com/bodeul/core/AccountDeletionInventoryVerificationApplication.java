package com.bodeul.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountDeletionInventoryVerificationApplication {

    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String PASSWORD_ENV = "MIGRATION_DB_PASSWORD";

    static final List<String> EXPECTED_INVENTORY_COLUMNS = List.of(
            "profile_count",
            "appointment_count",
            "active_appointment_count",
            "companion_session_count",
            "active_companion_session_count",
            "session_report_count",
            "appointment_follow_up_count",
            "assignment_audit_count",
            "related_chat_message_count",
            "sent_chat_message_count",
            "related_chat_attachment_count",
            "related_chat_read_receipt_count",
            "related_location_count",
            "active_legal_hold_count");
    private static final List<String> REQUIRED_CONTRACT_FLAGS = List.of(
            "transaction_is_read_only",
            "flyway_success",
            "owner_is_migration",
            "security_definer",
            "stable_function",
            "search_path_is_pinned",
            "core_runtime_can_execute",
            "admin_runtime_can_execute",
            "core_service_has_schema_usage",
            "admin_service_has_schema_usage",
            "core_service_can_execute",
            "admin_service_can_execute",
            "anon_cannot_execute",
            "authenticated_cannot_execute",
            "service_role_cannot_execute",
            "core_runtime_cannot_read_assignment_audit_columns",
            "core_service_cannot_read_assignment_audit_columns");

    private static final String VERIFY_CONTRACT = """
            select
                current_setting('transaction_read_only')::boolean as transaction_is_read_only,
                history.success as flyway_success,
                pg_get_userbyid(proc.proowner) = 'bodeul_migration' as owner_is_migration,
                proc.prosecdef as security_definer,
                proc.provolatile = 's' as stable_function,
                coalesce(proc.proconfig, '{}'::text[])
                    @> array['search_path=bodeul, pg_temp'] as search_path_is_pinned,
                has_function_privilege(
                    'bodeul_core_runtime',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as core_runtime_can_execute,
                has_function_privilege(
                    'bodeul_admin_runtime',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as admin_runtime_can_execute,
                has_schema_privilege(
                    'bodeul_core_service',
                    'bodeul',
                    'USAGE') as core_service_has_schema_usage,
                has_schema_privilege(
                    'bodeul_admin_service',
                    'bodeul',
                    'USAGE') as admin_service_has_schema_usage,
                has_function_privilege(
                    'bodeul_core_service',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as core_service_can_execute,
                has_function_privilege(
                    'bodeul_admin_service',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as admin_service_can_execute,
                not has_function_privilege(
                    'anon',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as anon_cannot_execute,
                not has_function_privilege(
                    'authenticated',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as authenticated_cannot_execute,
                not has_function_privilege(
                    'service_role',
                    'bodeul.account_deletion_postgres_inventory(uuid)',
                    'EXECUTE') as service_role_cannot_execute,
                not has_any_column_privilege(
                    'bodeul_core_runtime',
                    'bodeul.companion_session_assignment_audits',
                    'SELECT') as core_runtime_cannot_read_assignment_audit_columns,
                not has_any_column_privilege(
                    'bodeul_core_service',
                    'bodeul.companion_session_assignment_audits',
                    'SELECT') as core_service_cannot_read_assignment_audit_columns
            from bodeul.flyway_schema_history history
            join pg_proc proc
              on proc.oid = 'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
            where history.version = '15'
            order by history.installed_rank desc
            limit 1
            """;

    private static final String VERIFY_SYNTHETIC_INVENTORY = """
            select *
            from bodeul.account_deletion_postgres_inventory(cast(? as uuid))
            """;

    private AccountDeletionInventoryVerificationApplication() {
    }

    public static void main(String[] args) {
        Map<String, String> environment = System.getenv();
        String jdbcUrl = requiredEnvironment(environment, JDBC_URL_ENV);
        String username = requiredEnvironment(environment, USERNAME_ENV);
        String password = requiredEnvironment(environment, PASSWORD_ENV);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            runVerification(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("계정 삭제 영향도 DB 계약을 검증할 수 없습니다.");
        }

        System.out.println("계정 삭제 영향도 DB 계약 검증을 통과했습니다.");
    }

    static void runVerification(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set transaction read only");
            }
            verify(connection);
        } finally {
            connection.rollback();
        }
    }

    static void verify(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set local role bodeul_migration");
        }
        verifyContract(connection);
        verifySyntheticInventory(connection);
    }

    private static void verifyContract(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(VERIFY_CONTRACT);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new IllegalStateException("Flyway V15 함수 계약을 찾을 수 없습니다.");
            }
            for (String flag : REQUIRED_CONTRACT_FLAGS) {
                if (!resultSet.getBoolean(flag)) {
                    throw new IllegalStateException("계정 삭제 영향도 권한 계약이 일치하지 않습니다: " + flag);
                }
            }
            if (resultSet.next()) {
                throw new IllegalStateException("Flyway V15 함수 계약이 중복 조회됐습니다.");
            }
        }
    }

    private static void verifySyntheticInventory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(VERIFY_SYNTHETIC_INVENTORY)) {
            statement.setObject(1, UUID.randomUUID());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("합성 계정 영향도 결과를 확인할 수 없습니다.");
                }
                verifyInventoryColumns(resultSet.getMetaData());
                for (String column : EXPECTED_INVENTORY_COLUMNS) {
                    Long count = resultSet.getObject(column, Long.class);
                    if (count == null || count != 0) {
                        throw new IllegalStateException(
                                "합성 계정 영향도 집계값이 0이 아닙니다: " + column);
                    }
                }
                if (resultSet.next()) {
                    throw new IllegalStateException("합성 계정 영향도 결과가 한 행을 초과했습니다.");
                }
            }
        }
    }

    private static void verifyInventoryColumns(ResultSetMetaData metadata) throws SQLException {
        if (metadata.getColumnCount() != EXPECTED_INVENTORY_COLUMNS.size()) {
            throw new IllegalStateException("합성 계정 영향도 반환 열 개수가 일치하지 않습니다.");
        }
        for (int index = 1; index <= EXPECTED_INVENTORY_COLUMNS.size(); index++) {
            String expected = EXPECTED_INVENTORY_COLUMNS.get(index - 1);
            if (!expected.equals(metadata.getColumnLabel(index))) {
                throw new IllegalStateException(
                        "합성 계정 영향도 반환 열이 일치하지 않습니다: " + expected);
            }
        }
    }

    static String requiredEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
