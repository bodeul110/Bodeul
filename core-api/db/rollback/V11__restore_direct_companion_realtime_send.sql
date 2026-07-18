create or replace function bodeul.broadcast_companion_realtime_change()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_session_id uuid;
    v_record_id uuid;
    v_resource text;
    v_event text;
    v_payload jsonb;
begin
    case tg_table_name
        when 'companion_chat_messages' then
            v_session_id := new.companion_session_id;
            v_record_id := new.id;
            v_resource := 'chat';
            v_event := 'chat.changed';
        when 'companion_chat_read_receipts' then
            v_session_id := new.companion_session_id;
            v_record_id := new.user_id;
            v_resource := 'read-receipt';
            v_event := 'read-receipt.changed';
        when 'companion_session_locations' then
            v_session_id := new.companion_session_id;
            v_record_id := new.id;
            v_resource := 'location';
            v_event := 'location.changed';
        else
            return new;
    end case;

    if to_regprocedure('realtime.send(jsonb,text,text,boolean)') is null then
        return new;
    end if;

    v_payload := jsonb_build_object(
        'sessionId', v_session_id::text,
        'resource', v_resource,
        'recordId', v_record_id::text
    );

    execute 'select realtime.send($1, $2, $3, $4)'
        using v_payload, v_event, 'companion-session:' || v_session_id::text, true;
    return new;
exception
    when others then
        raise warning '동행 Realtime 알림 전송을 건너뛰었습니다. session_id=%', v_session_id;
        return new;
end;
$$;

alter function bodeul.broadcast_companion_realtime_change() owner to bodeul_migration;
revoke all on function bodeul.broadcast_companion_realtime_change()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
