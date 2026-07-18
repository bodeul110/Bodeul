drop index if exists bodeul.ix_chat_read_receipts_session_message;

create index ix_chat_read_receipts_message
    on bodeul.companion_chat_read_receipts (last_read_message_id)
    where last_read_message_id is not null;
