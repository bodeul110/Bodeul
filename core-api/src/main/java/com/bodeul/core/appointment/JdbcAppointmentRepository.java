package com.bodeul.core.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAppointmentRepository implements AppointmentRepository {

    private static final String SELECT_COLUMNS = """
            select
                id,
                firestore_id,
                patient_user_id,
                guardian_user_id,
                manager_user_id,
                requester_user_id,
                requester_role,
                patient_name,
                patient_phone,
                patient_email,
                guardian_name,
                guardian_phone,
                guardian_email,
                hospital_name,
                department_name,
                hospital_latitude,
                hospital_longitude,
                appointment_at,
                meeting_place,
                special_notes,
                patient_condition_summary,
                medication_summary,
                mobility_support_code,
                trip_type_code,
                manager_gender_preference_code,
                status,
                base_price,
                option_surcharge_price,
                coupon_discount_price,
                final_price,
                payment_method_code,
                coupon_code,
                payment_status_code,
                payment_approval_code,
                payment_approved_at,
                payment_provider_label,
                version
            from bodeul.appointment_requests
            """;

    private static final String RETURNING_COLUMNS = SELECT_COLUMNS.substring(
            SELECT_COLUMNS.indexOf("select") + "select".length(),
            SELECT_COLUMNS.lastIndexOf("from bodeul.appointment_requests"));

    private static final RowMapper<AppointmentRecord> ROW_MAPPER =
            (resultSet, rowNumber) -> mapAppointment(resultSet);
    private static final String FOLLOW_UP_COLUMNS = """
            appointment_request_id,
            review_rating_code,
            review_saved_at,
            settlement_follow_up_status,
            settlement_follow_up_note,
            settlement_follow_up_saved_at,
            support_escalation_status,
            support_escalated_at,
            version
            """;
    private static final RowMapper<AppointmentFollowUpRecord> FOLLOW_UP_ROW_MAPPER =
            (resultSet, rowNumber) -> mapFollowUp(resultSet);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAppointmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AppointmentRecord> findAllForParticipant(UUID userId, AppUserRole role) {
        String participantColumn = switch (role) {
            case PATIENT -> "patient_user_id";
            case GUARDIAN -> "guardian_user_id";
            case MANAGER -> "manager_user_id";
            default -> throw new IllegalArgumentException("지원하지 않는 예약 조회 역할입니다.");
        };
        return jdbcTemplate.query(
                SELECT_COLUMNS
                        + "where " + participantColumn + " = :userId "
                        + "order by appointment_at desc, created_at desc limit 100",
                new MapSqlParameterSource("userId", userId),
                ROW_MAPPER);
    }

    @Override
    public Optional<AppointmentRecord> findById(UUID appointmentId) {
        return queryOne(
                SELECT_COLUMNS + "where id = :appointmentId limit 1",
                new MapSqlParameterSource("appointmentId", appointmentId));
    }

    @Override
    public Optional<AppointmentRecord> findByClientRequestId(
            UUID requesterUserId,
            UUID clientRequestId) {
        return queryOne(
                SELECT_COLUMNS
                        + "where requester_user_id = :requesterUserId "
                        + "and client_request_id = :clientRequestId limit 1",
                new MapSqlParameterSource()
                        .addValue("requesterUserId", requesterUserId)
                        .addValue("clientRequestId", clientRequestId));
    }

    @Override
    public Optional<AppointmentRecord> insert(AppointmentMutation mutation) {
        String sql = """
                insert into bodeul.appointment_requests (
                    client_request_id,
                    patient_user_id,
                    guardian_user_id,
                    requester_user_id,
                    requester_role,
                    patient_name,
                    patient_phone,
                    patient_email,
                    guardian_name,
                    guardian_phone,
                    guardian_email,
                    requester_name,
                    requester_phone,
                    hospital_name,
                    department_name,
                    hospital_latitude,
                    hospital_longitude,
                    appointment_at,
                    appointment_at_epoch_millis,
                    appointment_date_key,
                    meeting_place,
                    special_notes,
                    patient_condition_summary,
                    medication_summary,
                    mobility_support_code,
                    trip_type_code,
                    manager_gender_preference_code,
                    status,
                    base_price,
                    option_surcharge_price,
                    coupon_discount_price,
                    final_price,
                    payment_method_code,
                    coupon_code,
                    payment_status_code,
                    payment_approval_code,
                    payment_provider_label,
                    reminder_stages,
                    created_at,
                    updated_at
                ) values (
                    :clientRequestId,
                    :patientUserId,
                    :guardianUserId,
                    :requesterUserId,
                    :requesterRole,
                    :patientName,
                    :patientPhone,
                    :patientEmail,
                    :guardianName,
                    :guardianPhone,
                    :guardianEmail,
                    :requesterName,
                    :requesterPhone,
                    :hospitalName,
                    :departmentName,
                    :hospitalLatitude,
                    :hospitalLongitude,
                    :appointmentAt,
                    :appointmentAtEpochMillis,
                    :appointmentDateKey,
                    :meetingPlace,
                    :specialNotes,
                    :patientConditionSummary,
                    :medicationSummary,
                    :mobilitySupportCode,
                    :tripTypeCode,
                    :managerGenderPreferenceCode,
                    'REQUESTED',
                    :basePrice,
                    :optionSurchargePrice,
                    :couponDiscountPrice,
                    :finalPrice,
                    :paymentMethodCode,
                    :couponCode,
                    :paymentStatusCode,
                    '',
                    '',
                    '["D7", "D3", "D1"]'::jsonb,
                    now(),
                    now()
                )
                on conflict (requester_user_id, client_request_id)
                    where client_request_id is not null
                do nothing
                returning
                """ + RETURNING_COLUMNS;
        return queryOne(sql, parameters(mutation));
    }

    @Override
    public Optional<AppointmentRecord> update(
            UUID appointmentId,
            long expectedVersion,
            AppointmentMutation mutation) {
        String sql = """
                update bodeul.appointment_requests
                set patient_user_id = :patientUserId,
                    guardian_user_id = :guardianUserId,
                    patient_name = :patientName,
                    patient_phone = :patientPhone,
                    patient_email = :patientEmail,
                    guardian_name = :guardianName,
                    guardian_phone = :guardianPhone,
                    guardian_email = :guardianEmail,
                    hospital_name = :hospitalName,
                    department_name = :departmentName,
                    hospital_latitude = :hospitalLatitude,
                    hospital_longitude = :hospitalLongitude,
                    appointment_at = :appointmentAt,
                    appointment_at_epoch_millis = :appointmentAtEpochMillis,
                    appointment_date_key = :appointmentDateKey,
                    meeting_place = :meetingPlace,
                    special_notes = :specialNotes,
                    patient_condition_summary = :patientConditionSummary,
                    medication_summary = :medicationSummary,
                    mobility_support_code = :mobilitySupportCode,
                    trip_type_code = :tripTypeCode,
                    manager_gender_preference_code = :managerGenderPreferenceCode,
                    base_price = :basePrice,
                    option_surcharge_price = :optionSurchargePrice,
                    coupon_discount_price = :couponDiscountPrice,
                    final_price = :finalPrice,
                    payment_method_code = :paymentMethodCode,
                    coupon_code = :couponCode,
                    payment_status_code = :paymentStatusCode,
                    payment_approval_code = '',
                    payment_approved_at = null,
                    payment_provider_label = '',
                    updated_at = now(),
                    version = version + 1
                where id = :appointmentId
                  and status in ('REQUESTED', 'MATCHED')
                  and version = :expectedVersion
                returning
                """ + RETURNING_COLUMNS;
        MapSqlParameterSource parameters = parameters(mutation)
                .addValue("appointmentId", appointmentId)
                .addValue("expectedVersion", expectedVersion);
        return queryOne(sql, parameters);
    }

    @Override
    public Optional<AppointmentRecord> cancel(UUID appointmentId, long expectedVersion) {
        String sql = """
                update bodeul.appointment_requests
                set status = 'CANCELED',
                    updated_at = now(),
                    version = version + 1
                where id = :appointmentId
                  and status = 'REQUESTED'
                  and version = :expectedVersion
                returning
                """ + RETURNING_COLUMNS;
        return queryOne(
                sql,
                new MapSqlParameterSource()
                        .addValue("appointmentId", appointmentId)
                        .addValue("expectedVersion", expectedVersion));
    }

    @Override
    public boolean cancelActiveSession(UUID appointmentId) {
        String sql = """
                update bodeul.companion_sessions
                set current_status = 'CANCELED',
                    canceled_at = now(),
                    updated_at = now(),
                    version = version + 1
                where appointment_request_id = :appointmentId
                  and current_status not in ('COMPLETED', 'CANCELED')
                """;
        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource("appointmentId", appointmentId)) == 1;
    }

    @Override
    public Optional<AppointmentFollowUpRecord> findFollowUpByAppointmentId(UUID appointmentId) {
        String sql = "select " + FOLLOW_UP_COLUMNS
                + " from bodeul.appointment_follow_ups "
                + "where appointment_request_id = :appointmentId limit 1";
        return queryFollowUp(
                sql,
                new MapSqlParameterSource("appointmentId", appointmentId));
    }

    @Override
    public Optional<AppointmentFollowUpRecord> insertFollowUp(
            AppointmentFollowUpMutation mutation) {
        String sql = """
                insert into bodeul.appointment_follow_ups (
                    appointment_request_id,
                    review_rating_code,
                    review_saved_by_user_id,
                    review_saved_at,
                    settlement_follow_up_status,
                    settlement_follow_up_note,
                    settlement_follow_up_saved_by_user_id,
                    settlement_follow_up_saved_at,
                    support_escalation_status,
                    support_escalated_by_user_id,
                    support_escalated_at,
                    version,
                    updated_at
                ) values (
                    :appointmentId,
                    coalesce(:reviewRatingCode, ''),
                    case when :reviewProvided then :actorUserId else null end,
                    case when :reviewProvided then now() else null end,
                    coalesce(:settlementStatus, ''),
                    coalesce(:settlementNote, ''),
                    case when :settlementProvided then :actorUserId else null end,
                    case when :settlementProvided then now() else null end,
                    coalesce(:supportEscalationStatus, ''),
                    case when :supportProvided then :actorUserId else null end,
                    case when :supportProvided then now() else null end,
                    1,
                    now()
                )
                on conflict (appointment_request_id) do nothing
                returning
                """ + FOLLOW_UP_COLUMNS;
        return queryFollowUp(sql, followUpParameters(mutation));
    }

    @Override
    public Optional<AppointmentFollowUpRecord> updateFollowUp(
            AppointmentFollowUpMutation mutation) {
        String sql = """
                update bodeul.appointment_follow_ups
                set review_rating_code = coalesce(:reviewRatingCode, review_rating_code),
                    review_saved_by_user_id = case
                        when not :reviewProvided then review_saved_by_user_id
                        else :actorUserId
                    end,
                    review_saved_at = case
                        when not :reviewProvided then review_saved_at
                        else now()
                    end,
                    settlement_follow_up_status = coalesce(
                        :settlementStatus,
                        settlement_follow_up_status
                    ),
                    settlement_follow_up_note = case
                        when not :settlementProvided then settlement_follow_up_note
                        else :settlementNote
                    end,
                    settlement_follow_up_saved_by_user_id = case
                        when not :settlementProvided then settlement_follow_up_saved_by_user_id
                        else :actorUserId
                    end,
                    settlement_follow_up_saved_at = case
                        when not :settlementProvided then settlement_follow_up_saved_at
                        else now()
                    end,
                    support_escalation_status = coalesce(
                        :supportEscalationStatus,
                        support_escalation_status
                    ),
                    support_escalated_by_user_id = case
                        when not :supportProvided then support_escalated_by_user_id
                        else :actorUserId
                    end,
                    support_escalated_at = case
                        when not :supportProvided then support_escalated_at
                        else now()
                    end,
                    version = version + 1,
                    updated_at = now()
                where appointment_request_id = :appointmentId
                  and version = :expectedVersion
                returning
                """ + FOLLOW_UP_COLUMNS;
        return queryFollowUp(sql, followUpParameters(mutation));
    }

    private Optional<AppointmentRecord> queryOne(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private Optional<AppointmentFollowUpRecord> queryFollowUp(
            String sql,
            MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, FOLLOW_UP_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private MapSqlParameterSource parameters(AppointmentMutation mutation) {
        return new MapSqlParameterSource()
                .addValue("clientRequestId", mutation.clientRequestId())
                .addValue("patientUserId", mutation.patientUserId())
                .addValue("guardianUserId", mutation.guardianUserId())
                .addValue("requesterUserId", mutation.requesterUserId())
                .addValue("requesterRole", mutation.requesterRole().name())
                .addValue("patientName", mutation.patient().name())
                .addValue("patientPhone", mutation.patient().phone())
                .addValue("patientEmail", mutation.patient().email())
                .addValue("guardianName", mutation.guardian().name())
                .addValue("guardianPhone", mutation.guardian().phone())
                .addValue("guardianEmail", mutation.guardian().email())
                .addValue("requesterName", mutation.requester().name())
                .addValue("requesterPhone", mutation.requester().phone())
                .addValue("hospitalName", mutation.hospitalName())
                .addValue("departmentName", mutation.departmentName())
                .addValue("hospitalLatitude", mutation.hospitalLatitude())
                .addValue("hospitalLongitude", mutation.hospitalLongitude())
                .addValue("appointmentAt", Timestamp.from(mutation.appointmentAt()))
                .addValue("appointmentAtEpochMillis", mutation.appointmentAtEpochMillis())
                .addValue("appointmentDateKey", mutation.appointmentDateKey())
                .addValue("meetingPlace", mutation.meetingPlace())
                .addValue("specialNotes", mutation.specialNotes())
                .addValue("patientConditionSummary", mutation.patientConditionSummary())
                .addValue("medicationSummary", mutation.medicationSummary())
                .addValue("mobilitySupportCode", mutation.mobilitySupportCode())
                .addValue("tripTypeCode", mutation.tripTypeCode())
                .addValue("managerGenderPreferenceCode", mutation.managerGenderPreferenceCode())
                .addValue("basePrice", mutation.basePrice())
                .addValue("optionSurchargePrice", mutation.optionSurchargePrice())
                .addValue("couponDiscountPrice", mutation.couponDiscountPrice())
                .addValue("finalPrice", mutation.finalPrice())
                .addValue("paymentMethodCode", mutation.paymentMethodCode())
                .addValue("couponCode", mutation.couponCode())
                .addValue("paymentStatusCode", mutation.paymentStatusCode());
    }

    private MapSqlParameterSource followUpParameters(AppointmentFollowUpMutation mutation) {
        return new MapSqlParameterSource()
                .addValue("appointmentId", mutation.appointmentId())
                .addValue("actorUserId", mutation.actorUserId())
                .addValue("expectedVersion", mutation.expectedVersion())
                .addValue("reviewRatingCode", mutation.reviewRatingCode())
                .addValue("reviewProvided", mutation.reviewRatingCode() != null)
                .addValue("settlementStatus", mutation.settlementStatus())
                .addValue("settlementNote", mutation.settlementNote())
                .addValue("settlementProvided", mutation.settlementStatus() != null)
                .addValue("supportEscalationStatus", mutation.supportEscalationStatus())
                .addValue("supportProvided", mutation.supportEscalationStatus() != null);
    }

    private static AppointmentRecord mapAppointment(ResultSet resultSet) throws SQLException {
        return new AppointmentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("firestore_id"),
                resultSet.getObject("patient_user_id", UUID.class),
                resultSet.getObject("guardian_user_id", UUID.class),
                resultSet.getObject("manager_user_id", UUID.class),
                resultSet.getObject("requester_user_id", UUID.class),
                AppUserRole.valueOf(resultSet.getString("requester_role")),
                new ParticipantSnapshot(
                        resultSet.getString("patient_name"),
                        resultSet.getString("patient_phone"),
                        resultSet.getString("patient_email")),
                new ParticipantSnapshot(
                        resultSet.getString("guardian_name"),
                        resultSet.getString("guardian_phone"),
                        resultSet.getString("guardian_email")),
                resultSet.getString("hospital_name"),
                resultSet.getString("department_name"),
                resultSet.getDouble("hospital_latitude"),
                resultSet.getDouble("hospital_longitude"),
                resultSet.getTimestamp("appointment_at").toInstant(),
                resultSet.getString("meeting_place"),
                resultSet.getString("special_notes"),
                resultSet.getString("patient_condition_summary"),
                resultSet.getString("medication_summary"),
                resultSet.getString("mobility_support_code"),
                resultSet.getString("trip_type_code"),
                resultSet.getString("manager_gender_preference_code"),
                resultSet.getString("status"),
                resultSet.getInt("base_price"),
                resultSet.getInt("option_surcharge_price"),
                resultSet.getInt("coupon_discount_price"),
                resultSet.getInt("final_price"),
                resultSet.getString("payment_method_code"),
                resultSet.getString("coupon_code"),
                resultSet.getString("payment_status_code"),
                resultSet.getString("payment_approval_code"),
                nullableInstant(resultSet, "payment_approved_at"),
                resultSet.getString("payment_provider_label"),
                resultSet.getLong("version"));
    }

    private static AppointmentFollowUpRecord mapFollowUp(ResultSet resultSet) throws SQLException {
        return new AppointmentFollowUpRecord(
                resultSet.getObject("appointment_request_id", UUID.class),
                resultSet.getString("review_rating_code"),
                nullableInstant(resultSet, "review_saved_at"),
                resultSet.getString("settlement_follow_up_status"),
                resultSet.getString("settlement_follow_up_note"),
                nullableInstant(resultSet, "settlement_follow_up_saved_at"),
                resultSet.getString("support_escalation_status"),
                nullableInstant(resultSet, "support_escalated_at"),
                resultSet.getLong("version"));
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
