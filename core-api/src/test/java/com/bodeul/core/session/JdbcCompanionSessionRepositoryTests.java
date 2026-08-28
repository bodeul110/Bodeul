package com.bodeul.core.session;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCompanionSessionRepositoryTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID MANAGER_ID = UUID.fromString("fdb39fea-f2da-408e-bf46-77dbf2265a73");
    private static final UUID PATIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");
    private static final UUID GUARDIAN_ID = UUID.fromString("6b82d10f-8f20-4a77-b9b4-055a346b689d");
    private static final UUID GUIDE_ID = UUID.fromString("45bd0403-59a7-449a-90f6-fae10c79da30");

    private NamedParameterJdbcTemplate jdbcTemplate;
    private JdbcCompanionSessionRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repository = new JdbcCompanionSessionRepository(
                jdbcTemplate,
                new ObjectMapper(),
                properties(false));
    }

    @Test
    void sessionLookupUsesFrozenSnapshotWithoutLiveGuideJoin() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CompanionSessionRepository.SessionRecord>>any());

        repository.findById(SESSION_ID);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<CompanionSessionRepository.SessionRecord>>any());
        assertThat(sql.getValue())
                .contains("session.guide_id")
                .contains("session.guide_revision")
                .contains("session.guide_step_contract_version")
                .contains("session.guide_steps_snapshot")
                .contains("session.guide_snapshot_source")
                .doesNotContain("hospital_guides")
                .doesNotContain("guide.steps");
    }

    @Test
    void snapshotMapperPreservesAllStepsAndUnknownCode() throws Exception {
        var mapper = sessionMapperCaptor();
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(MapSqlParameterSource.class),
                mapper.capture());
        repository.findById(SESSION_ID);

        ResultSet resultSet = resultSetWithSnapshot(snapshotJson(14), 4L);
        CompanionSessionRepository.SessionRecord session = mapper.getValue().mapRow(resultSet, 0);

        assertThat(session.guideSnapshot().guideId()).isEqualTo(GUIDE_ID);
        assertThat(session.guideSnapshot().guideRevision()).isEqualTo(4L);
        assertThat(session.guideSnapshot().steps()).hasSize(14);
        assertThat(session.guideSnapshot().steps().get(13).code()).isEqualTo("UNLISTED_EXTENSION");
        assertThat(session.guideSnapshot().steps().get(13).title()).isEqualTo("단계 14");
    }

    @Test
    void nullableGuideRevisionIsNotConvertedToZero() throws Exception {
        var mapper = sessionMapperCaptor();
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(MapSqlParameterSource.class),
                mapper.capture());
        repository.findById(SESSION_ID);

        CompanionSessionRepository.SessionRecord session = mapper.getValue()
                .mapRow(resultSetWithSnapshot("[]", null), 0);

        assertThat(session.guideSnapshot().guideRevision()).isNull();
    }

    @Test
    void advanceUpdateDefendsSnapshotRangeAndOptimisticVersion() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(1, 0);

        repository.advance(SESSION_ID, MANAGER_ID, 3, APPOINTMENT_ID);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var parameters = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(
                sql.capture(),
                parameters.capture());
        assertThat(sql.getAllValues().get(1))
                .contains("version = :expectedVersion")
                .contains("current_status not in ('COMPLETED', 'CANCELED')")
                .contains("guide_steps_snapshot is not null")
                .contains("current_step_order < jsonb_array_length(guide_steps_snapshot)")
                .contains("guide_snapshot_source = 'HOSPITAL_GUIDE_STEP_CODE_V1'")
                .contains("guide_snapshot_source = 'LEGACY_CORE_7_V1'")
                .contains("bodeul.is_valid_guide_steps_v1(guide_steps_snapshot)")
                .contains("not :preConsultationEnforcement")
                .contains("<> 'PRE_CONSULTATION'")
                .contains("or pre_consultation_confirmed");
        assertThat(parameters.getAllValues().get(1).getValue("preConsultationEnforcement"))
                .isEqualTo(false);
    }

    @Test
    void enabledPreConsultationEnforcementIsBoundToRaceSafeAdvanceGuard() {
        repository = new JdbcCompanionSessionRepository(
                jdbcTemplate,
                new ObjectMapper(),
                properties(true));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(1, 0);

        repository.advance(SESSION_ID, MANAGER_ID, 3, APPOINTMENT_ID);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var parameters = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues().get(1))
                .contains("not :preConsultationEnforcement")
                .contains("<> 'PRE_CONSULTATION'")
                .contains("or pre_consultation_confirmed");
        assertThat(parameters.getAllValues().get(1).getValue("preConsultationEnforcement"))
                .isEqualTo(true);
    }

    private ResultSet resultSetWithSnapshot(String snapshotJson, Long revision) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(SESSION_ID);
        when(resultSet.getObject("appointment_request_id", UUID.class)).thenReturn(APPOINTMENT_ID);
        when(resultSet.getObject("manager_user_id", UUID.class)).thenReturn(MANAGER_ID);
        when(resultSet.getObject("patient_user_id", UUID.class)).thenReturn(PATIENT_ID);
        when(resultSet.getObject("guardian_user_id", UUID.class)).thenReturn(GUARDIAN_ID);
        when(resultSet.getObject("guide_id", UUID.class)).thenReturn(GUIDE_ID);
        when(resultSet.getObject("guide_revision")).thenReturn(revision);
        when(resultSet.getObject("guide_step_contract_version")).thenReturn(1);
        when(resultSet.getString("guide_steps_snapshot")).thenReturn(snapshotJson);
        when(resultSet.getString("guide_snapshot_source"))
                .thenReturn("HOSPITAL_GUIDE_STEP_CODE_V1");
        when(resultSet.getInt("total_step_count")).thenReturn(snapshotJson.equals("[]") ? 0 : 14);
        when(resultSet.getString("current_status")).thenReturn("READY");
        return resultSet;
    }

    private CompanionSessionProperties properties(boolean preConsultationEnforcement) {
        CompanionSessionProperties properties = new CompanionSessionProperties();
        properties.setPreConsultationEnforcement(preConsultationEnforcement);
        return properties;
    }

    private String snapshotJson(int count) throws Exception {
        List<Map<String, Object>> steps = IntStream.rangeClosed(1, count)
                .mapToObj(order -> Map.<String, Object>of(
                        "code", order == count ? "UNLISTED_EXTENSION" : "STEP_" + order,
                        "order", order,
                        "title", "단계 " + order,
                        "description", "설명 " + order))
                .toList();
        return new ObjectMapper().writeValueAsString(steps);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private org.mockito.ArgumentCaptor<RowMapper<CompanionSessionRepository.SessionRecord>>
            sessionMapperCaptor() {
        return (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(RowMapper.class);
    }
}
