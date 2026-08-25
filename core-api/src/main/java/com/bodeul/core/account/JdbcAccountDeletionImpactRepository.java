package com.bodeul.core.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAccountDeletionImpactRepository implements AccountDeletionImpactRepository {

    private static final String INSPECT_ACCOUNT = """
            select *
            from bodeul.account_deletion_postgres_inventory(:userId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAccountDeletionImpactRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostgreSqlImpact inspect(UUID userId) {
        PostgreSqlImpact impact = jdbcTemplate.queryForObject(
                INSPECT_ACCOUNT,
                new MapSqlParameterSource("userId", userId),
                (resultSet, rowNumber) -> new PostgreSqlImpact(
                        requiredCount(resultSet, "profile_count"),
                        requiredCount(resultSet, "appointment_count"),
                        requiredCount(resultSet, "active_appointment_count"),
                        requiredCount(resultSet, "companion_session_count"),
                        requiredCount(resultSet, "active_companion_session_count"),
                        requiredCount(resultSet, "session_report_count"),
                        requiredCount(resultSet, "appointment_follow_up_count"),
                        requiredCount(resultSet, "assignment_audit_count"),
                        requiredCount(resultSet, "related_chat_message_count"),
                        requiredCount(resultSet, "sent_chat_message_count"),
                        requiredCount(resultSet, "related_chat_attachment_count"),
                        requiredCount(resultSet, "related_chat_read_receipt_count"),
                        requiredCount(resultSet, "related_location_count"),
                        requiredCount(resultSet, "active_legal_hold_count")));
        if (impact == null) {
            throw new DataRetrievalFailureException("계정 삭제 영향도 집계 결과를 확인할 수 없습니다.");
        }
        return impact;
    }

    private long requiredCount(ResultSet resultSet, String column) throws SQLException {
        Long count = resultSet.getObject(column, Long.class);
        if (count == null || count < 0) {
            throw new DataRetrievalFailureException(
                    "계정 삭제 영향도 집계 열을 확인할 수 없습니다: " + column);
        }
        return count;
    }
}
