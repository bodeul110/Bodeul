package com.bodeul.core.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
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
                coalesce(jsonb_array_length(guide.steps), 0) as total_step_count,
                session.current_status,
                session.guardian_update,
                session.location_summary,
                session.field_photo_note,
                session.medication_note,
                session.pharmacy_summary,
                session.prescription_collected,
                session.pharmacy_completed,
                session.medication_guidance_completed,
                session.version,
                session.started_at,
                session.completed_at,
                session.canceled_at
            from bodeul.companion_sessions session
            join bodeul.appointment_requests appointment
              on appointment.id = session.appointment_request_id
            left join bodeul.hospital_guides guide
              on guide.hospital_name = appointment.hospital_name
             and guide.department_name = appointment.department_name
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

    private static final RowMapper<SessionRecord> SESSION_MAPPER =
            (resultSet, rowNumber) -> mapSession(resultSet);
    private static final RowMapper<ReportRecord> REPORT_MAPPER =
            (resultSet, rowNumber) -> mapReport(resultSet);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcCompanionSessionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                SESSION_MAPPER);
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
                    prescription_collected = coalesce(:prescriptionCollected, prescription_collected),
                    pharmacy_completed = coalesce(:pharmacyCompleted, pharmacy_completed),
                    medication_guidance_completed = coalesce(
                        :medicationGuidanceCompleted,
                        medication_guidance_completed
                    ),
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
                .addValue("prescriptionCollected", patch.prescriptionCollected(), Types.BOOLEAN)
                .addValue("pharmacyCompleted", patch.pharmacyCompleted(), Types.BOOLEAN)
                .addValue(
                        "medicationGuidanceCompleted",
                        patch.medicationGuidanceCompleted(),
                        Types.BOOLEAN));
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
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("managerUserId", managerUserId)
                .addValue("expectedVersion", expectedVersion));
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
        return jdbcTemplate.query(sql, parameters, SESSION_MAPPER)
                .stream()
                .findFirst();
    }

    private Optional<ReportRecord> queryReport(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, REPORT_MAPPER)
                .stream()
                .findFirst();
    }

    private static SessionRecord mapSession(ResultSet resultSet) throws SQLException {
        return new SessionRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("firestore_id"),
                resultSet.getObject("appointment_request_id", UUID.class),
                resultSet.getObject("manager_user_id", UUID.class),
                resultSet.getObject("patient_user_id", UUID.class),
                resultSet.getObject("guardian_user_id", UUID.class),
                resultSet.getInt("current_step_order"),
                resultSet.getInt("total_step_count"),
                resultSet.getString("current_status"),
                resultSet.getString("guardian_update"),
                resultSet.getString("location_summary"),
                resultSet.getString("field_photo_note"),
                resultSet.getString("medication_note"),
                resultSet.getString("pharmacy_summary"),
                resultSet.getBoolean("prescription_collected"),
                resultSet.getBoolean("pharmacy_completed"),
                resultSet.getBoolean("medication_guidance_completed"),
                resultSet.getLong("version"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"),
                instant(resultSet, "canceled_at"));
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
