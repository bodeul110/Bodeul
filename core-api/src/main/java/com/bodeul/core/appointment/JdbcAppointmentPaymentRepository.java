package com.bodeul.core.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAppointmentPaymentRepository implements AppointmentPaymentRepository {

    private static final RowMapper<BankTransferPaymentRecord> ROW_MAPPER =
            JdbcAppointmentPaymentRepository::mapPayment;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAppointmentPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BankTransferPaymentRecord> findForPatient(
            UUID appointmentId,
            UUID patientUserId) {
        return queryOne(
                """
                select *
                from bodeul.get_bank_transfer_payment(:appointmentId, :patientUserId)
                """,
                new MapSqlParameterSource()
                        .addValue("appointmentId", appointmentId)
                        .addValue("patientUserId", patientUserId));
    }

    @Override
    public Optional<BankTransferPaymentRecord> setDepositor(
            UUID appointmentId,
            UUID patientUserId,
            UUID operationId,
            long expectedPaymentVersion,
            String depositorName) {
        return queryOne(
                """
                select *
                from bodeul.set_bank_transfer_depositor(
                    :appointmentId,
                    :patientUserId,
                    :operationId,
                    :expectedPaymentVersion,
                    :depositorName
                )
                """,
                new MapSqlParameterSource()
                        .addValue("appointmentId", appointmentId)
                        .addValue("patientUserId", patientUserId)
                        .addValue("operationId", operationId)
                        .addValue("expectedPaymentVersion", expectedPaymentVersion)
                        .addValue("depositorName", depositorName));
    }

    private Optional<BankTransferPaymentRecord> queryOne(
            String sql,
            MapSqlParameterSource parameters) {
        List<BankTransferPaymentRecord> rows = jdbcTemplate.query(sql, parameters, ROW_MAPPER);
        return rows.stream().findFirst();
    }

    private static BankTransferPaymentRecord mapPayment(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new BankTransferPaymentRecord(
                resultSet.getObject("appointment_request_id", UUID.class),
                resultSet.getString("payment_method_code"),
                resultSet.getString("payment_status_code"),
                resultSet.getInt("expected_amount"),
                resultSet.getString("depositor_name"),
                nullableInstant(resultSet, "payment_due_at"),
                nullableInteger(resultSet, "received_amount"),
                nullableInstant(resultSet, "confirmed_at"),
                nullableInstant(resultSet, "refund_requested_at"),
                nullableInstant(resultSet, "refunded_at"),
                resultSet.getLong("payment_version"));
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
