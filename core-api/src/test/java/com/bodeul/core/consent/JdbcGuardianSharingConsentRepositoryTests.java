package com.bodeul.core.consent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcGuardianSharingConsentRepositoryTests {

    private static final UUID CONSENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000027");
    private static final UUID APPOINTMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000027");
    private static final UUID PATIENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000027");
    private static final UUID GUARDIAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000028");
    private static final Instant GRANTED_AT = Instant.parse("2026-09-05T06:00:00.123456Z");
    private static final Instant EXPIRES_AT = GRANTED_AT.plus(7, ChronoUnit.DAYS);
    private static final Instant CARE_ENDED_AT = GRANTED_AT.plus(1, ChronoUnit.HOURS);

    private PreparedStatement statement;
    private JdbcGuardianSharingConsentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        repository = new JdbcGuardianSharingConsentRepository(
                JdbcClient.create(dataSource), new ObjectMapper());
    }

    @Test
    void grantBindsAllInstantsAsUtcOffsetDateTime() throws Exception {
        stubGrantResult();

        assertThat(repository.grant(grant()).grantedAt()).isEqualTo(GRANTED_AT);

        verify(statement).setObject(8, GRANTED_AT.atOffset(ZoneOffset.UTC));
        verify(statement).setObject(9, GRANTED_AT.atOffset(ZoneOffset.UTC));
        verify(statement).setObject(10, EXPIRES_AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    void revokeBindsInstantAsUtcOffsetDateTime() throws Exception {
        stubGrantResult();

        assertThat(repository.revoke(APPOINTMENT_ID, PATIENT_ID, CARE_ENDED_AT, 0)).isPresent();

        verify(statement).setObject(2, CARE_ENDED_AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    void careBoundaryBindsBothTimestampOccurrencesEvenWithoutConsentRows() throws Exception {
        // 동의 행이 없어도 SQL 실행 전 매개변수를 바인딩하므로 예약 취소 경로를 함께 보호한다.
        when(statement.executeUpdate()).thenReturn(0);

        repository.finalizeExpiryAfterCareBoundary(APPOINTMENT_ID, CARE_ENDED_AT);

        verify(statement).setObject(1, CARE_ENDED_AT.atOffset(ZoneOffset.UTC));
        verify(statement).setObject(2, CARE_ENDED_AT.atOffset(ZoneOffset.UTC));
        verify(statement).executeUpdate();
    }

    @Test
    void auditEventBindsBothInstantsAsUtcOffsetDateTime() throws Exception {
        repository.appendEvent(
                grant(), GuardianSharingConsentRepository.EventAction.REVOKED,
                PATIENT_ID, CARE_ENDED_AT);

        verify(statement).setObject(9, GRANTED_AT.atOffset(ZoneOffset.UTC));
        verify(statement).setObject(10, CARE_ENDED_AT.atOffset(ZoneOffset.UTC));
    }

    private AdultPatientGuardianSharingPolicy.Grant grant() {
        return new AdultPatientGuardianSharingPolicy.Grant(
                CONSENT_ID, APPOINTMENT_ID, PATIENT_ID, GUARDIAN_ID,
                Set.of(AdultPatientGuardianSharingPolicy.InformationScope.APPOINTMENT),
                "test-policy", PATIENT_ID, GRANTED_AT, EXPIRES_AT, null, null, 0);
    }

    private void stubGrantResult() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject("id", UUID.class)).thenReturn(CONSENT_ID);
        when(resultSet.getObject("appointment_request_id", UUID.class)).thenReturn(APPOINTMENT_ID);
        when(resultSet.getObject("patient_user_id", UUID.class)).thenReturn(PATIENT_ID);
        when(resultSet.getObject("guardian_user_id", UUID.class)).thenReturn(GUARDIAN_ID);
        when(resultSet.getObject("granted_by_user_id", UUID.class)).thenReturn(PATIENT_ID);
        when(resultSet.getString("scopes_json")).thenReturn("[\"APPOINTMENT\"]");
        when(resultSet.getString("policy_version")).thenReturn("test-policy");
        when(resultSet.getTimestamp("granted_at")).thenReturn(Timestamp.from(GRANTED_AT));
        when(resultSet.getTimestamp("expires_at")).thenReturn(Timestamp.from(EXPIRES_AT));
    }
}
