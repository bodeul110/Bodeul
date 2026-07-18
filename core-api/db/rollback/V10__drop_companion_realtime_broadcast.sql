drop trigger if exists broadcast_companion_location_after_insert
    on bodeul.companion_session_locations;
drop trigger if exists broadcast_companion_read_receipt_after_change
    on bodeul.companion_chat_read_receipts;
drop trigger if exists broadcast_companion_chat_message_after_insert
    on bodeul.companion_chat_messages;
drop function if exists bodeul.broadcast_companion_realtime_change();
