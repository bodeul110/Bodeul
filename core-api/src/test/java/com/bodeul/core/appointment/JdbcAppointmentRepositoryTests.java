package com.bodeul.core.appointment;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAppointmentRepositoryTests {

    private static final UUID APPOINTMENT_ID = UUID.fromString("9d22571c-a7b0-432e-8138-642fab9f828d");

    private NamedParameterJdbcTemplate jdbcTemplate;
    private JdbcAppointmentRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repository = new JdbcAppointmentRepository(jdbcTemplate);
    }

    @Test
    void careEndLookupUsesTheSessionBoundaryInsteadOfAppointmentStatus() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenReturn(true);

        assertThat(repository.hasCareEnded(APPOINTMENT_ID)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sql.capture(),
                parameters.capture(),
                eq(Boolean.class));
        assertThat(sql.getValue())
                .contains("from bodeul.companion_sessions session")
                .contains("session.appointment_request_id = :appointmentId")
                .contains("session.care_ended_at is not null")
                .doesNotContain("appointment.status");
        assertThat(parameters.getValue().getValue("appointmentId")).isEqualTo(APPOINTMENT_ID);
    }
}
