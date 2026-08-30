package com.bodeul.core.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("database")
class JdbcCompanionSessionArtifactRepository implements CompanionSessionArtifactRepository {

    private static final String SELECT = """
            select id, companion_session_id, purpose, client_request_id, item_order,
                   storage_path, file_name, content_type, size_bytes, sha256,
                   uploaded_by_user_id, created_at
            from bodeul.companion_session_artifacts
            """;
    private static final RowMapper<ArtifactRecord> MAPPER =
            (resultSet, rowNumber) -> map(resultSet);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcCompanionSessionArtifactRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public ReplaceResult replace(
            UUID sessionId,
            String purpose,
            UUID clientRequestId,
            UUID uploadedByUserId,
            List<ArtifactMutation> artifacts) {
        lockWritableSession(sessionId, purpose);
        String payloadFingerprint = payloadFingerprint(artifacts);
        Optional<ArtifactOperation> operation = findOperation(
                sessionId,
                purpose,
                clientRequestId);
        if (operation.isPresent()) {
            if (operation.get().payloadFingerprint().equals(payloadFingerprint)) {
                return new ReplaceResult(
                        List.of(),
                        findCurrent(sessionId, purpose),
                        false);
            }
            throw CompanionSessionException.artifactIdempotencyConflict();
        }

        long resultRevision = nextRevision(sessionId, purpose);
        jdbcTemplate.update(
                """
                insert into bodeul.companion_session_artifact_operations (
                    companion_session_id, purpose, client_request_id,
                    payload_fingerprint, result_revision
                ) values (
                    :sessionId, :purpose, :clientRequestId,
                    :payloadFingerprint, :resultRevision
                )
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose)
                        .addValue("clientRequestId", clientRequestId)
                        .addValue("payloadFingerprint", payloadFingerprint)
                        .addValue("resultRevision", resultRevision));

        List<String> replacedPaths = jdbcTemplate.queryForList(
                """
                select storage_path
                from bodeul.companion_session_artifacts
                where companion_session_id = :sessionId and purpose = :purpose
                order by item_order
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose),
                String.class);
        jdbcTemplate.update(
                """
                delete from bodeul.companion_session_artifacts
                where companion_session_id = :sessionId and purpose = :purpose
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose));

        for (ArtifactMutation artifact : artifacts) {
            jdbcTemplate.update(
                    """
                    insert into bodeul.companion_session_artifacts (
                        companion_session_id, purpose, client_request_id, item_order,
                        storage_path, file_name, content_type, size_bytes, sha256,
                        uploaded_by_user_id
                    ) values (
                        :sessionId, :purpose, :clientRequestId, :itemOrder,
                        :storagePath, :fileName, :contentType, :sizeBytes, :sha256,
                        :uploadedByUserId
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("sessionId", sessionId)
                            .addValue("purpose", purpose)
                            .addValue("clientRequestId", clientRequestId)
                            .addValue("itemOrder", artifact.itemOrder())
                            .addValue("storagePath", artifact.storagePath())
                            .addValue("fileName", artifact.fileName())
                            .addValue("contentType", artifact.contentType())
                            .addValue("sizeBytes", artifact.sizeBytes())
                            .addValue("sha256", artifact.sha256())
                            .addValue("uploadedByUserId", uploadedByUserId));
        }
        return new ReplaceResult(
                List.copyOf(replacedPaths),
                findCurrent(sessionId, purpose),
                true);
    }

    @Override
    @Transactional
    public List<String> clear(UUID sessionId, String purpose) {
        lockWritableSession(sessionId, purpose);
        List<String> paths = jdbcTemplate.queryForList(
                """
                select storage_path
                from bodeul.companion_session_artifacts
                where companion_session_id = :sessionId and purpose = :purpose
                order by item_order
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose),
                String.class);
        jdbcTemplate.update(
                """
                delete from bodeul.companion_session_artifacts
                where companion_session_id = :sessionId and purpose = :purpose
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose));
        return List.copyOf(paths);
    }

    @Override
    public Optional<ArtifactRecord> findById(UUID sessionId, UUID artifactId) {
        return jdbcTemplate.query(
                        SELECT + "where companion_session_id = :sessionId and id = :artifactId",
                        new MapSqlParameterSource()
                                .addValue("sessionId", sessionId)
                                .addValue("artifactId", artifactId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    private List<ArtifactRecord> findCurrent(
            UUID sessionId,
            String purpose) {
        return jdbcTemplate.query(
                SELECT + """
                        where companion_session_id = :sessionId
                          and purpose = :purpose
                        order by item_order
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose),
                MAPPER);
    }

    private Optional<ArtifactOperation> findOperation(
            UUID sessionId,
            String purpose,
            UUID clientRequestId) {
        return jdbcTemplate.query(
                        """
                        select payload_fingerprint, result_revision
                        from bodeul.companion_session_artifact_operations
                        where companion_session_id = :sessionId
                          and purpose = :purpose
                          and client_request_id = :clientRequestId
                        """,
                        new MapSqlParameterSource()
                                .addValue("sessionId", sessionId)
                                .addValue("purpose", purpose)
                                .addValue("clientRequestId", clientRequestId),
                        (resultSet, rowNumber) -> new ArtifactOperation(
                                resultSet.getString("payload_fingerprint"),
                                resultSet.getLong("result_revision")))
                .stream()
                .findFirst();
    }

    private long nextRevision(UUID sessionId, String purpose) {
        Long revision = jdbcTemplate.queryForObject(
                """
                select coalesce(max(result_revision), 0) + 1
                from bodeul.companion_session_artifact_operations
                where companion_session_id = :sessionId and purpose = :purpose
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("purpose", purpose),
                Long.class);
        return revision == null ? 1L : revision;
    }

    private void lockWritableSession(UUID sessionId, String purpose) {
        String expectedStep = "PAYMENT_EVIDENCE".equals(purpose)
                ? "PAYMENT_EVIDENCE"
                : "PRESCRIPTION_DOCUMENTS";
        List<UUID> sessions = jdbcTemplate.queryForList(
                """
                select id
                from bodeul.companion_sessions
                where id = :sessionId
                  and current_status not in ('CARE_ENDED', 'COMPLETED', 'CANCELED')
                  and care_ended_at is null
                  and guide_steps_snapshot is not null
                  and jsonb_typeof(guide_steps_snapshot) = 'array'
                  and current_step_order between 1 and jsonb_array_length(guide_steps_snapshot)
                  and guide_steps_snapshot -> (current_step_order - 1) ->> 'code'
                      = :expectedStep
                for update
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("expectedStep", expectedStep),
                UUID.class);
        if (sessions.isEmpty()) {
            throw CompanionSessionException.stateConflict();
        }
    }

    private String payloadFingerprint(List<ArtifactMutation> artifacts) {
        StringBuilder canonical = new StringBuilder();
        for (ArtifactMutation artifact : artifacts) {
            canonical.append(artifact.itemOrder()).append('\n')
                    .append(artifact.storagePath()).append('\n')
                    .append(artifact.fileName()).append('\n')
                    .append(artifact.contentType()).append('\n')
                    .append(artifact.sizeBytes()).append('\n')
                    .append(artifact.sha256()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static ArtifactRecord map(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new ArtifactRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("companion_session_id", UUID.class),
                resultSet.getString("purpose"),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getInt("item_order"),
                resultSet.getString("storage_path"),
                resultSet.getString("file_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                resultSet.getObject("uploaded_by_user_id", UUID.class),
                createdAt == null ? null : createdAt.toInstant());
    }

    private record ArtifactOperation(String payloadFingerprint, long resultRevision) {
    }
}
