package com.bodeul.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionInventoryVerificationApplicationTests {

    private Connection connection;
    private Statement roleStatement;
    private PreparedStatement contractStatement;
    private PreparedStatement inventoryStatement;
    private ResultSet contractResult;
    private ResultSet inventoryResult;
    private ResultSetMetaData inventoryMetadata;

    @BeforeEach
    void setUp() throws Exception {
        connection = mock(Connection.class);
        roleStatement = mock(Statement.class);
        contractStatement = mock(PreparedStatement.class);
        inventoryStatement = mock(PreparedStatement.class);
        contractResult = mock(ResultSet.class);
        inventoryResult = mock(ResultSet.class);
        inventoryMetadata = mock(ResultSetMetaData.class);

        when(connection.createStatement()).thenReturn(roleStatement);
        when(connection.prepareStatement(contains("flyway_schema_history"))).thenReturn(contractStatement);
        when(connection.prepareStatement(contains("account_deletion_postgres_inventory(cast")))
                .thenReturn(inventoryStatement);
        when(contractStatement.executeQuery()).thenReturn(contractResult);
        when(inventoryStatement.executeQuery()).thenReturn(inventoryResult);
        when(contractResult.next()).thenReturn(true, false);
        when(contractResult.getBoolean(anyString())).thenReturn(true);
        when(inventoryResult.next()).thenReturn(true, false);
        when(inventoryResult.getMetaData()).thenReturn(inventoryMetadata);
        when(inventoryMetadata.getColumnCount())
                .thenReturn(AccountDeletionInventoryVerificationApplication.EXPECTED_INVENTORY_COLUMNS.size());
        when(inventoryMetadata.getColumnLabel(anyInt())).thenAnswer(invocation ->
                AccountDeletionInventoryVerificationApplication.EXPECTED_INVENTORY_COLUMNS.get(
                        invocation.getArgument(0, Integer.class) - 1));
        when(inventoryResult.getObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    @Test
    void verifiesRoleContractAndSyntheticAggregateWithoutWritingData() throws Exception {
        AccountDeletionInventoryVerificationApplication.runVerification(connection);

        verify(connection).setAutoCommit(false);
        verify(connection).setReadOnly(true);
        verify(roleStatement).execute("set transaction read only");
        verify(roleStatement).execute("set local role bodeul_migration");
        verify(contractStatement).executeQuery();
        verify(inventoryStatement).executeQuery();
        verify(connection).rollback();
    }

    @Test
    void failsWhenAnyPrivilegeContractIsOpen() throws Exception {
        when(contractResult.getBoolean("core_service_can_execute")).thenReturn(false);

        assertThatThrownBy(() -> AccountDeletionInventoryVerificationApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core_service_can_execute");
    }

    @Test
    void failsWhenSyntheticAggregateContainsUnexpectedData() throws Exception {
        when(inventoryResult.getObject("profile_count", Long.class)).thenReturn(1L);

        assertThatThrownBy(() -> AccountDeletionInventoryVerificationApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0이 아닙니다");
    }

    @Test
    void failsWhenSyntheticAggregateColumnsDrift() throws Exception {
        when(inventoryMetadata.getColumnLabel(1)).thenReturn("renamed_profile_count");

        assertThatThrownBy(() -> AccountDeletionInventoryVerificationApplication.verify(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile_count");
    }

    @Test
    void requiresEveryMigrationCredentialWithoutEchoingItsValue() {
        assertThat(AccountDeletionInventoryVerificationApplication.requiredEnvironment(
                Map.of("NAME", "value"), "NAME")).isEqualTo("value");
        assertThatThrownBy(() -> AccountDeletionInventoryVerificationApplication.requiredEnvironment(
                Map.of("NAME", " "), "NAME"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NAME 환경변수가 필요합니다.");
    }
}
