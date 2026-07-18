drop index if exists bodeul.ix_chat_read_receipts_message;

create index ix_chat_read_receipts_session_message
    on bodeul.companion_chat_read_receipts (
        companion_session_id,
        last_read_message_id
    )
    where last_read_message_id is not null;

comment on index bodeul.ix_chat_read_receipts_session_message is
    '같은 세션 메시지만 참조하는 읽음 위치 외래키의 covering index';
