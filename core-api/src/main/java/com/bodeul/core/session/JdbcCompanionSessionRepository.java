package com.bodeul.core.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.session.CompanionSessionRepository.GuideSnapshotRecord;
import com.bodeul.core.session.CompanionSessionRepository.GuideStepRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcCompanionSessionRepository implements CompanionSessionRepository {

    private static final String SESSION_SELECT = """
            select
                session.id,
                session.firestore_id,
                session.appointment_request_id,
                session.manager_user_id,
                appointment.patient_user_id,
                appointment.guardian_user_id,
                session.current_step_order,
                coalesce(jsonb_array_length(session.guide_steps_snapshot), 0) as total_step_count,
                session.guide_id,
                session.guide_revision,
                session.guide_step_contract_version,
                session.guide_steps_snapshot::text as guide_steps_snapshot,
                session.guide_snapshot_source,
                session.current_status,
                session.guardian_update,
                session.location_summary,
                session.field_photo_note,
                session.medication_note,
                session.pharmacy_summary,
                session.pre_consultation_confirmed,
                session.prescription_collected,
                session.pharmacy_completed,
                session.medication_guidance_completed,
                session.live_location_sharing_active,
                session.live_location_sharing_started_at,
                session.location_alert_stage,
                session.location_alert_sent_at,
                session.version,
                session.started_at,
                session.completed_at,
                session.canceled_at
            from bodeul.companion_sessions session
            join bodeul.appointment_requests appointment
              on appointment.id = session.appointment_request_id
            """;

    private static final String REPORT_SELECT = """
            select
                id,
                firestore_id,
                companion_session_id,
                summary,
                treatment_notes,
                medication_notes,
                medication_name,
                medication_change_summary,
                medication_schedule_note,
                medication_comparison_decision_code,
                medication_comparison_note,
                next_visit_at,
                next_visit_note,
                version
            from bodeul.session_reports
            """;

    private static final RowMapper<ReportRecord> REPORT_MAPPER =
            (resultSet, rowNumber) -> mapReport(resultSet);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<SessionRecord> sessionMapper;
    private final boolean preConsultationEnforcement;

    JdbcCompanionSessionRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CompanionSessionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sessionMapper = (resultSet, rowNumber) -> mapSession(resultSet);
        this.preConsultationEnforcement = properties.isPreConsultationEnforcement();
    }

    @Override
    public List<SessionRecord> findAllForUser(UUID userId, AppUserRole role) {
        String userColumn = switch (role) {
            case PATIENT -> "appointment.patient_user_id";
            case GUARDIAN -> "appointment.guardian_user_id";
            case MANAGER -> "session.manager_user_id";
            default -> throw new IllegalArgumentException("지원하지 않는 동행 세션 조회 역할입니다.");
        };
        return jdbcTemplate.query(
                SESSION_SELECT
                        + "where " + userColumn + " = :userId "
                        + "order by appointment.appointment_at desc, session.created_at desc limit 100",
                new MapSqlParameterSource("userId", userId),
                sessionMapper);
    }

    @Override
    public Optional<SessionRecord> findById(UUID sessionId) {
        return querySession(
                SESSION_SELECT + "where session.id = :sessionId limit 1",
                new MapSqlParameterSource("sessionId", sessionId));
    }

    @Override
    public Optional<ReportRecord> findReportBySessionId(UUID sessionId) {
        return queryReport(
                REPORT_SELECT + "where companion_session_id = :sessionId limit 1",
                new MapSqlParameterSource("sessionId", sessionId));
    }

    @Override
    public Optional<SessionRecord> updateDetails(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            SessionPatch patch) {
        String sql = """
                update bodeul.companion_sessions
                set guardian_update = coalesce(:guardianUpdate, guardian_update),
                    location_summary = coalesce(:locationSummary, location_summary),
                    field_photo_note = coalesce(:fieldPhotoNote, field_photo_note),
                    medication_note = coalesce(:medicationNote, medication_note),
                    pharmacy_summary = coalesce(:pharmacySummary, pharmacy_summary),
                    pre_consultation_confirmed = coalesce(
                        :preConsultationConfirmed,
                        pre_consultation_confirmed
                    ),
                    prescription_collected = coalesce(:prescriptionCollected, prescription_collected),
                    pharmacy_completed = coalesce(:pharmacyCompleted, pharmacy_completed),
                    medication_guidance_completed = coalesce(
                        :medicationGuidanceCompleted,
                        medication_guidance_completed
                    ),
                    live_location_sharing_active = coalesce(
                        :liveLocationSharingActive,
                        live_location_sharing_active
                    ),
                    live_location_sharing_started_at = case
                        when :liveLocationSharingActive = true
                            then coalesce(live_location_sharing_started_at, now())
                        when :liveLocationSharingActive = false then null
                        else live_location_sharing_started_at
                    end,
                    location_alert_stage = coalesce(:locationAlertStage, location_alert_stage),
                    location_alert_sent_at = case
                        when :locationAlertStage = 'none' then null
                        when :locationAlertStage is not null
                             and :locationAlertStage <> location_alert_stage then now()
                        else location_alert_sent_at
                    end,
                    updated_at = now(),
                    version = version + 1
                where id = :sessionId
                  and manager_user_id = :managerUserId
                  and current_status not in ('COMPLETED', 'CANCELED')
                  and version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("managerUserId", managerUserId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("guardianUpdate", patch.guardianUpdate(), Types.VARCHAR)
                .addValue("locationSummary", patch.locationSummary(), Types.VARCHAR)
                .addValue("fieldPhotoNote", patch.fieldPhotoNote(), Types.VARCHAR)
                .addValue("medicationNote", patch.medicationNote(), Types.VARCHAR)
                .addValue("pharmacySummary", patch.pharmacySummary(), Types.VARCHAR)
                .addValue(
                        "preConsultationConfirmed",
                        patch.preConsultationConfirmed(),
                        Types.BOOLEAN)
                .addValue("prescriptionCollected", patch.prescriptionCollected(), Types.BOOLEAN)
                .addValue("pharmacyCompleted", patch.pharmacyCompleted(), Types.BOOLEAN)
                .addValue(
                        "medicationGuidanceCompleted",
                        patch.medicationGuidanceCompleted(),
                        Types.BOOLEAN)
                .addValue(
                        "liveLocationSharingActive",
                        patch.liveLocationSharingActive(),
                        Types.BOOLEAN)
                .addValue("locationAlertStage", patch.locationAlertStage(), Types.VARCHAR));
        return updated == 1 ? findById(sessionId) : Optional.empty();
    }

    @Override
    public Optional<SessionRecord> advance(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId) {
        if (!markAppointmentInProgress(appointmentRequestId)) {
            return Optional.empty();
        }

        String sql = """
                update bodeul.companion_sessions
                set current_step_order = current_step_order + 1,
                    current_status = case
                        when current_step_order + 1 <= 1 then 'MEETING'
                        when current_step_order + 1 = 2 then 'WAITING'
                        when current_step_order + 1 <= 4 then 'IN_TREATMENT'
                        else 'PAYMENT'
                    end,
                    started_at = coalesce(started_at, now()),
                    updated_at = now(),
                    version = version + 1
                where id = :sessionId
                  and manager_user_id = :managerUserId
                  and current_status not in ('COMPLETED', 'CANCELED')
                  and version = :expectedVersion
                  and guide_steps_snapshot is not null
                  and jsonb_typeof(guide_steps_snapshot) = 'array'
                  and current_step_order >= 0
                  and current_step_order < jsonb_array_length(guide_steps_snapshot)
                  and (
                      (
                          guide_snapshot_source = 'HOSPITAL_GUIDE_STEP_CODE_V1'
                          and guide_step_contract_version = 1
                      )
                      or guide_snapshot_source = 'LEGACY_CORE_7_V1'
                  )
                  and bodeul.is_valid_guide_steps_v1(guide_steps_snapshot)
                  and (
                      not :preConsultationEnforcement
                      or current_step_order = 0
                      or guide_steps_snapshot -> (current_step_order - 1) ->> 'code'
                          <> 'PRE_CONSULTATION'
                      or pre_consultation_confirmed
                  )
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("managerUserId", managerUserId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("preConsultationEnforcement", preConsultationEnforcement));
        return updated == 1 ? findById(sessionId) : Optional.empty();
    }

    @Override
    public Optional<CompletionRecord> completeWithReport(
            UUID sessionId,
            UUID managerUserId,
            long expectedVersion,
            UUID appointmentRequestId,
            ReportMutation report) {
        if (!markAppointmentCompleted(appointmentRequestId)) {
            return Optional.empty();
        }

        ReportRecord savedReport = upsertReport(sessionId, report);
        String sql = """
                update bodeul.companion_sessions
                set current_status = 'COMPLETED',
                    medication_note = :medicationNotes,
                    completed_at = now(),
                    updated_at = now(),
                    version = version + 1
                where id = :sessionId
                  and manager_user_id = :managerUserId
                  and current_status not in ('COMPLETED', 'CANCELED')
                  and version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("managerUserId", managerUserId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("medicationNotes", report.medicationNotes()));
        if (updated != 1) {
            return Optional.empty();
        }
        return findById(sessionId)
                .map(session -> new CompletionRecord(session, savedReport));
    }

    private boolean markAppointmentInProgress(UUID appointmentRequestId) {
        String sql = """
                update bodeul.appointment_requests
                set status = 'IN_PROGRESS',
                    updated_at = now(),
                    version = version + case when status = 'MATCHED' then 1 else 0 end
                where id = :appointmentRequestId
                  and status in ('MATCHED', 'IN_PROGRESS')
                """;
        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource("appointmentRequestId", appointmentRequestId)) == 1;
    }

    private boolean markAppointmentCompleted(UUID appointmentRequestId) {
        String sql = """
                update bodeul.appointment_requests
                set status = 'COMPLETED',
                    updated_at = now(),
                    version = version + 1
                where id = :appointmentRequestId
                  and status in ('MATCHED', 'IN_PROGRESS')
                """;
        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource("appointmentRequestId", appointmentRequestId)) == 1;
    }

    private ReportRecord upsertReport(UUID sessionId, ReportMutation report) {
        String sql = """
                insert into bodeul.session_reports (
                    companion_session_id,
                    summary,
                    treatment_notes,
                    medication_notes,
                    medication_name,
                    medication_change_summary,
                    medication_schedule_note,
                    medication_comparison_decision_code,
                    medication_comparison_note,
                    next_visit_at,
                    next_visit_note
                ) values (
                    :sessionId,
                    :summary,
                    :treatmentNotes,
                    :medicationNotes,
                    :medicationName,
                    :medicationChangeSummary,
                    :medicationScheduleNote,
                    :medicationComparisonDecisionCode,
                    :medicationComparisonNote,
                    :nextVisitAt,
                    :nextVisitNote
                )
                on conflict (companion_session_id) do update
                set summary = excluded.summary,
                    treatment_notes = excluded.treatment_notes,
                    medication_notes = excluded.medication_notes,
                    medication_name = excluded.medication_name,
                    medication_change_summary = excluded.medication_change_summary,
                    medication_schedule_note = excluded.medication_schedule_note,
                    medication_comparison_decision_code = excluded.medication_comparison_decision_code,
                    medication_comparison_note = excluded.medication_comparison_note,
                    next_visit_at = excluded.next_visit_at,
                    next_visit_note = excluded.next_visit_note,
                    updated_at = now(),
                    version = bodeul.session_reports.version + 1
                returning id
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("summary", report.summary())
                .addValue("treatmentNotes", report.treatmentNotes())
                .addValue("medicationNotes", report.medicationNotes())
                .addValue("medicationName", report.medicationName())
                .addValue("medicationChangeSummary", report.medicationChangeSummary())
                .addValue("medicationScheduleNote", report.medicationScheduleNote())
                .addValue("medicationComparisonDecisionCode", report.medicationComparisonDecisionCode())
                .addValue("medicationComparisonNote", report.medicationComparisonNote())
                .addValue(
                        "nextVisitAt",
                        report.nextVisitAt() == null ? null : Timestamp.from(report.nextVisitAt()),
                        Types.TIMESTAMP)
                .addValue("nextVisitNote", report.nextVisitNote());
        UUID reportId = jdbcTemplate.queryForObject(sql, parameters, UUID.class);
        if (reportId == null) {
            throw new DataRetrievalFailureException("저장된 동행 리포트 ID를 확인할 수 없습니다.");
        }
        return findReportBySessionId(sessionId)
                .orElseThrow(() -> new DataRetrievalFailureException(
                        "저장된 동행 리포트를 확인할 수 없습니다."));
    }

    private Optional<SessionRecord> querySession(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, sessionMapper)
                .stream()
                .findFirst();
    }

    private Optional<ReportRecord> queryReport(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, REPORT_MAPPER)
                .stream()
                .findFirst();
    }

    private SessionRecord mapSession(ResultSet resultSet) throws SQLException {
        String snapshotJson = resultSet.getString("guide_steps_snapshot");
        GuideSnapshotRecord guideSnapshot = new GuideSnapshotRecord(
                resultSet.getObject("guide_id", UUID.class),
                nullableLong(resultSet, "guide_revision"),
                nullableInteger(resultSet, "guide_step_contract_version"),
                resultSet.getString("guide_snapshot_source"),
                snapshotJson != null,
                parseGuideSteps(snapshotJson));
        return new SessionRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("firestore_id"),
                resultSet.getObject("appointment_request_id", UUID.class),
                resultSet.getObject("manager_user_id", UUID.class),
                resultSet.getObject("patient_user_id", UUID.class),
                resultSet.getObject("guardian_user_id", UUID.class),
                resultSet.getInt("current_step_order"),
                resultSet.getInt("total_step_count"),
                guideSnapshot,
                resultSet.getString("current_status"),
                resultSet.getString("guardian_update"),
                resultSet.getString("location_summary"),
                resultSet.getString("field_photo_note"),
                resultSet.getString("medication_note"),
                resultSet.getString("pharmacy_summary"),
                resultSet.getBoolean("pre_consultation_confirmed"),
                resultSet.getBoolean("prescription_collected"),
                resultSet.getBoolean("pharmacy_completed"),
                resultSet.getBoolean("medication_guidance_completed"),
                resultSet.getBoolean("live_location_sharing_active"),
                instant(resultSet, "live_location_sharing_started_at"),
                resultSet.getString("location_alert_stage"),
                instant(resultSet, "location_alert_sent_at"),
                resultSet.getLong("version"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"),
                instant(resultSet, "canceled_at"));
    }

    private List<GuideStepRecord> parseGuideSteps(String snapshotJson) throws SQLException {
        if (snapshotJson == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<GuideStepRecord> steps = new ArrayList<>(root.size());
            for (JsonNode step : root) {
                steps.add(new GuideStepRecord(
                        text(step, "code"),
                        integer(step, "order"),
                        text(step, "title"),
                        text(step, "description")));
            }
            return List.copyOf(steps);
        } catch (JsonProcessingException exception) {
            throw new SQLException("동행 가이드 snapshot JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private int integer(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : 0;
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static ReportRecord mapReport(ResultSet resultSet) throws SQLException {
        return new ReportRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("firestore_id"),
                resultSet.getObject("companion_session_id", UUID.class),
                resultSet.getString("summary"),
                resultSet.getString("treatment_notes"),
                resultSet.getString("medication_notes"),
                resultSet.getString("medication_name"),
                resultSet.getString("medication_change_summary"),
                resultSet.getString("medication_schedule_note"),
                resultSet.getString("medication_comparison_decision_code"),
                resultSet.getString("medication_comparison_note"),
                instant(resultSet, "next_visit_at"),
                resultSet.getString("next_visit_note"),
                resultSet.getLong("version"));
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
