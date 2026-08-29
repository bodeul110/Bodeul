package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCompanionSessionArtifactRepositoryTests {

    private static final UUID SESSION_ID = UUID.fromString(
            "1153394e-9106-4cd8-9339-c72ca0559485");

    @Test
    void clearRechecksWritableStepWhileHoldingSessionLock() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcCompanionSessionArtifactRepository repository =
                new JdbcCompanionSessionArtifactRepository(jdbcTemplate);
        when(jdbcTemplate.queryForList(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(UUID.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> repository.clear(SESSION_ID, "PAYMENT_EVIDENCE"))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture(), eq(UUID.class));
        assertThat(sql.getValue())
                .contains("current_status not in ('CARE_ENDED', 'COMPLETED', 'CANCELED')")
                .contains("care_ended_at is null")
                .contains("guide_steps_snapshot -> (current_step_order - 1) ->> 'code'")
                .contains("for update");
        assertThat(parameters.getValue().getValue("expectedStep"))
                .isEqualTo("PAYMENT_EVIDENCE");
        verify(jdbcTemplate, never()).update(
                anyString(),
                any(MapSqlParameterSource.class));
    }
}
