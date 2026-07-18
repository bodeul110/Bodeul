drop trigger if exists schedule_companion_realtime_expiry_after_session_end
    on bodeul.companion_sessions;
drop function if exists bodeul.schedule_companion_realtime_expiry();
drop function if exists bodeul.record_companion_location(
    uuid,
    uuid,
    uuid,
    double precision,
    double precision,
    timestamptz
);
drop table if exists bodeul.companion_session_locations;
drop table if exists bodeul.companion_chat_read_receipts;
drop table if exists bodeul.companion_chat_attachments;
drop table if exists bodeul.companion_chat_messages;
