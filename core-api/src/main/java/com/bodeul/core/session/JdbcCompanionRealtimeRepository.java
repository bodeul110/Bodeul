package com.bodeul.core.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.session.CompanionRealtimeRepository.AttachmentMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.AttachmentRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.ChatMessageRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.LocationMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.LocationRecord;
import com.bodeul.core.session.CompanionRealtimeRepository.MessageMutation;
import com.bodeul.core.session.CompanionRealtimeRepository.MessageSaveResult;
import com.bodeul.core.session.CompanionRealtimeRepository.ReadReceiptRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcCompanionRealtimeRepository implements CompanionRealtimeRepository {

    private static final String MESSAGE_SELECT = """
            select
                id,
                companion_session_id,
                client_message_id,
                sender_user_id,
                sender_role,
                body,
                sent_at
            from bodeul.companion_chat_messages
            """;

    private static final String ATTACHMENT_SELECT = """
            select
                id,
                chat_message_id,
                storage_path,
                file_name,
                content_type,
                size_bytes
            from bodeul.companion_chat_attachments
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcCompanionRealtimeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ChatMessageRecord> findRecentMessages(UUID sessionId, int limit) {
        List<ChatMessageRecord> messages = new ArrayList<>(jdbcTemplate.query(
                MESSAGE_SELECT + """
                        where companion_session_id = :sessionId
                          and deleted_at is null
                        order by sent_at desc, id desc
                        limit :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> mapMessage(resultSet, List.of())));
        Collections.reverse(messages);
        return attachFiles(messages);
    }

    @Override
    public List<ReadReceiptRecord> findReadReceipts(UUID sessionId) {
        return jdbcTemplate.query(
                """
                        select companion_session_id, user_id, last_read_message_id, last_read_at
                        from bodeul.companion_chat_read_receipts
                        where companion_session_id = :sessionId
                        order by user_id
                        """,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNumber) -> mapReadReceipt(resultSet));
    }

    @Override
    public List<LocationRecord> findRecentLocations(UUID sessionId, int limit) {
        List<LocationRecord> locations = new ArrayList<>(jdbcTemplate.query(
                """
                        select
                            id,
                            companion_session_id,
                            client_location_id,
                            manager_user_id,
                            latitude,
                            longitude,
                            captured_at
                        from bodeul.companion_session_locations
                        where companion_session_id = :sessionId
                          and deleted_at is null
                        order by captured_at desc, id desc
                        limit :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> mapLocation(resultSet)));
        Collections.reverse(locations);
        return locations;
    }

    @Override
    public MessageSaveResult saveMessage(MessageMutation mutation) {
        List<UUID> insertedIds = jdbcTemplate.query(
                """
                        insert into bodeul.companion_chat_messages (
                            companion_session_id,
                            client_message_id,
                            sender_user_id,
                            sender_role,
                            body,
                            sent_at
                        ) values (
                            :sessionId,
                            :clientMessageId,
                            :senderUserId,
                            :senderRole,
                            :body,
                            now()
                        )
                        on conflict (companion_session_id, client_message_id) do nothing
                        returning id
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", mutation.sessionId())
                        .addValue("clientMessageId", mutation.clientMessageId())
                        .addValue("senderUserId", mutation.senderUserId())
                        .addValue("senderRole", mutation.senderRole())
                        .addValue("body", mutation.body()),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        boolean created = !insertedIds.isEmpty();
        if (created) {
            UUID messageId = insertedIds.getFirst();
            for (AttachmentMutation attachment : mutation.attachments()) {
                insertAttachment(messageId, attachment);
            }
        }
        ChatMessageRecord message = findMessageByClientId(
                mutation.sessionId(),
                mutation.clientMessageId()).orElseThrow(() -> new DataRetrievalFailureException(
                        "저장된 동행 채팅 메시지를 다시 조회하지 못했습니다."));
        return new MessageSaveResult(message, created);
    }

    @Override
    public Optional<ReadReceiptRecord> upsertReadReceipt(
            UUID sessionId,
            UUID userId,
            UUID lastReadMessageId) {
        List<ReadReceiptRecord> receipts = jdbcTemplate.query(
                """
                        with target_message as (
                            select id, sent_at
                            from bodeul.companion_chat_messages
                            where companion_session_id = :sessionId
                              and id = :lastReadMessageId
                              and deleted_at is null
                        ), upserted as (
                            insert into bodeul.companion_chat_read_receipts (
                                companion_session_id,
                                user_id,
                                last_read_message_id,
                                last_read_at
                            )
                            select :sessionId, :userId, target.id, now()
                            from target_message target
                            on conflict (companion_session_id, user_id) do update
                            set last_read_message_id = excluded.last_read_message_id,
                                last_read_at = excluded.last_read_at,
                                updated_at = now()
                            where companion_chat_read_receipts.last_read_message_id is null
                               or exists (
                                   select 1
                                   from target_message target
                                   join bodeul.companion_chat_messages previous
                                     on previous.companion_session_id = :sessionId
                                    and previous.id = companion_chat_read_receipts.last_read_message_id
                                   where (target.sent_at, target.id) >= (previous.sent_at, previous.id)
                               )
                            returning companion_session_id, user_id, last_read_message_id, last_read_at
                        )
                        select companion_session_id, user_id, last_read_message_id, last_read_at
                        from upserted
                        union all
                        select companion_session_id, user_id, last_read_message_id, last_read_at
                        from bodeul.companion_chat_read_receipts
                        where companion_session_id = :sessionId
                          and user_id = :userId
                          and exists (select 1 from target_message)
                          and not exists (select 1 from upserted)
                        limit 1
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("userId", userId)
                        .addValue("lastReadMessageId", lastReadMessageId),
                (resultSet, rowNumber) -> mapReadReceipt(resultSet));
        return receipts.stream().findFirst();
    }

    @Override
    public Optional<LocationRecord> saveLocation(LocationMutation mutation) {
        List<UUID> ids = jdbcTemplate.query(
                """
                        select bodeul.record_companion_location(
                            :sessionId,
                            :clientLocationId,
                            :managerUserId,
                            :latitude,
                            :longitude,
                            :capturedAt
                        ) as id
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", mutation.sessionId())
                        .addValue("clientLocationId", mutation.clientLocationId())
                        .addValue("managerUserId", mutation.managerUserId())
                        .addValue("latitude", mutation.latitude())
                        .addValue("longitude", mutation.longitude())
                        .addValue("capturedAt", Timestamp.from(mutation.capturedAt())),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        if (ids.isEmpty() || ids.getFirst() == null) {
            return Optional.empty();
        }
        List<LocationRecord> locations = jdbcTemplate.query(
                """
                        select
                            id,
                            companion_session_id,
                            client_location_id,
                            manager_user_id,
                            latitude,
                            longitude,
                            captured_at
                        from bodeul.companion_session_locations
                        where id = :id
                          and companion_session_id = :sessionId
                          and deleted_at is null
                        limit 1
                        """,
                new MapSqlParameterSource()
                        .addValue("id", ids.getFirst())
                        .addValue("sessionId", mutation.sessionId()),
                (resultSet, rowNumber) -> mapLocation(resultSet));
        return locations.stream().findFirst();
    }

    private void insertAttachment(UUID messageId, AttachmentMutation attachment) {
        jdbcTemplate.update(
                """
                        insert into bodeul.companion_chat_attachments (
                            chat_message_id,
                            storage_path,
                            file_name,
                            content_type,
                            size_bytes
                        ) values (
                            :messageId,
                            :storagePath,
                            :fileName,
                            :contentType,
                            :sizeBytes
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("messageId", messageId)
                        .addValue("storagePath", attachment.storagePath())
                        .addValue("fileName", attachment.fileName())
                        .addValue("contentType", attachment.contentType())
                        .addValue("sizeBytes", attachment.sizeBytes()));
    }

    private Optional<ChatMessageRecord> findMessageByClientId(
            UUID sessionId,
            UUID clientMessageId) {
        List<ChatMessageRecord> messages = jdbcTemplate.query(
                MESSAGE_SELECT + """
                        where companion_session_id = :sessionId
                          and client_message_id = :clientMessageId
                          and deleted_at is null
                        limit 1
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("clientMessageId", clientMessageId),
                (resultSet, rowNumber) -> mapMessage(resultSet, List.of()));
        return attachFiles(messages).stream().findFirst();
    }

    private List<ChatMessageRecord> attachFiles(List<ChatMessageRecord> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = messages.stream().map(ChatMessageRecord::id).toList();
        List<AttachmentRecord> attachments = jdbcTemplate.query(
                ATTACHMENT_SELECT + """
                        where chat_message_id in (:messageIds)
                          and deleted_at is null
                          and status = 'ACTIVE'
                        order by created_at, id
                        """,
                new MapSqlParameterSource("messageIds", messageIds),
                (resultSet, rowNumber) -> mapAttachment(resultSet));
        Map<UUID, List<AttachmentRecord>> grouped = new LinkedHashMap<>();
        for (AttachmentRecord attachment : attachments) {
            grouped.computeIfAbsent(attachment.chatMessageId(), ignored -> new ArrayList<>())
                    .add(attachment);
        }
        return messages.stream()
                .map(message -> new ChatMessageRecord(
                        message.id(),
                        message.sessionId(),
                        message.clientMessageId(),
                        message.senderUserId(),
                        message.senderRole(),
                        message.body(),
                        message.sentAt(),
                        List.copyOf(grouped.getOrDefault(message.id(), List.of()))))
                .toList();
    }

    private static ChatMessageRecord mapMessage(
            ResultSet resultSet,
            List<AttachmentRecord> attachments) throws SQLException {
        return new ChatMessageRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("companion_session_id", UUID.class),
                resultSet.getObject("client_message_id", UUID.class),
                resultSet.getObject("sender_user_id", UUID.class),
                resultSet.getString("sender_role"),
                resultSet.getString("body"),
                resultSet.getTimestamp("sent_at").toInstant(),
                attachments);
    }

    private static AttachmentRecord mapAttachment(ResultSet resultSet) throws SQLException {
        return new AttachmentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("chat_message_id", UUID.class),
                resultSet.getString("storage_path"),
                resultSet.getString("file_name"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"));
    }

    private static ReadReceiptRecord mapReadReceipt(ResultSet resultSet) throws SQLException {
        return new ReadReceiptRecord(
                resultSet.getObject("companion_session_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("last_read_message_id", UUID.class),
                resultSet.getTimestamp("last_read_at").toInstant());
    }

    private static LocationRecord mapLocation(ResultSet resultSet) throws SQLException {
        return new LocationRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("companion_session_id", UUID.class),
                resultSet.getObject("client_location_id", UUID.class),
                resultSet.getObject("manager_user_id", UUID.class),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getTimestamp("captured_at").toInstant());
    }
}
