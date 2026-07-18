create table bodeul.companion_chat_messages (
    id uuid primary key default gen_random_uuid(),
    companion_session_id uuid not null,
    client_message_id uuid not null,
    sender_user_id uuid not null,
    sender_role text not null,
    body text not null default '',
    sent_at timestamptz not null default now(),
    expires_at timestamptz,
    deleted_at timestamptz,
    legal_hold_until timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_chat_messages_session_client unique (companion_session_id, client_message_id),
    constraint uq_chat_messages_session_id unique (companion_session_id, id),
    constraint fk_chat_messages_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id),
    constraint fk_chat_messages_sender
        foreign key (sender_user_id) references bodeul.app_users (id),
    constraint ck_chat_messages_sender_role
        check (sender_role in ('PATIENT', 'GUARDIAN', 'MANAGER')),
    constraint ck_chat_messages_body_length
        check (char_length(body) <= 2000),
    constraint ck_chat_messages_expiry
        check (expires_at is null or expires_at >= sent_at),
    constraint ck_chat_messages_legal_hold
        check (legal_hold_until is null or legal_hold_until >= created_at)
);

comment on table bodeul.companion_chat_messages is 'Core API를 통해 저장되는 동행 세션 채팅 본문';
comment on column bodeul.companion_chat_messages.client_message_id is '재시도 시 중복 저장을 막는 클라이언트 생성 UUID';
comment on column bodeul.companion_chat_messages.expires_at is '세션 종료 후 180일 보관 만료 시각';

create index ix_chat_messages_session_sent
    on bodeul.companion_chat_messages (companion_session_id, sent_at desc, id desc)
    where deleted_at is null;
create index ix_chat_messages_expiry
    on bodeul.companion_chat_messages (expires_at)
    where expires_at is not null and deleted_at is null;
create index ix_chat_messages_sender
    on bodeul.companion_chat_messages (sender_user_id);

create table bodeul.companion_chat_attachments (
    id uuid primary key default gen_random_uuid(),
    chat_message_id uuid not null,
    storage_path text not null,
    file_name text not null default '',
    content_type text not null,
    size_bytes bigint not null,
    status text not null default 'ACTIVE',
    expires_at timestamptz,
    deleted_at timestamptz,
    legal_hold_until timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_chat_attachments_storage_path unique (storage_path),
    constraint fk_chat_attachments_message
        foreign key (chat_message_id) references bodeul.companion_chat_messages (id),
    constraint ck_chat_attachments_storage_path
        check (btrim(storage_path) <> ''),
    constraint ck_chat_attachments_content_type
        check (content_type in ('image/jpeg', 'image/png', 'application/pdf')),
    constraint ck_chat_attachments_size
        check (size_bytes >= 0 and size_bytes <= 10485760),
    constraint ck_chat_attachments_status
        check (status in ('ACTIVE', 'DELETED')),
    constraint ck_chat_attachments_legal_hold
        check (legal_hold_until is null or legal_hold_until >= created_at)
);

comment on table bodeul.companion_chat_attachments is 'Firebase Storage 채팅 첨부의 서버 인가용 메타데이터';
comment on column bodeul.companion_chat_attachments.expires_at is '세션 종료 후 30일 보관 만료 시각';

create index ix_chat_attachments_message
    on bodeul.companion_chat_attachments (chat_message_id);
create index ix_chat_attachments_expiry
    on bodeul.companion_chat_attachments (expires_at)
    where expires_at is not null and status = 'ACTIVE';

create table bodeul.companion_chat_read_receipts (
    companion_session_id uuid not null,
    user_id uuid not null,
    last_read_message_id uuid,
    last_read_at timestamptz not null,
    updated_at timestamptz not null default now(),
    primary key (companion_session_id, user_id),
    constraint fk_chat_read_receipts_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id),
    constraint fk_chat_read_receipts_user
        foreign key (user_id) references bodeul.app_users (id),
    constraint fk_chat_read_receipts_message
        foreign key (companion_session_id, last_read_message_id)
        references bodeul.companion_chat_messages (companion_session_id, id)
);

comment on table bodeul.companion_chat_read_receipts is '세션 참여자별 마지막 채팅 읽음 위치';

create index ix_chat_read_receipts_user
    on bodeul.companion_chat_read_receipts (user_id, updated_at desc);
create index ix_chat_read_receipts_message
    on bodeul.companion_chat_read_receipts (last_read_message_id)
    where last_read_message_id is not null;

create table bodeul.companion_session_locations (
    id uuid primary key default gen_random_uuid(),
    companion_session_id uuid not null,
    client_location_id uuid not null,
    manager_user_id uuid not null,
    latitude double precision not null,
    longitude double precision not null,
    captured_at timestamptz not null,
    expires_at timestamptz,
    deleted_at timestamptz,
    legal_hold_until timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_session_locations_session_client unique (companion_session_id, client_location_id),
    constraint fk_session_locations_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id),
    constraint fk_session_locations_manager
        foreign key (manager_user_id) references bodeul.app_users (id),
    constraint ck_session_locations_latitude
        check (latitude between -90.0 and 90.0),
    constraint ck_session_locations_longitude
        check (longitude between -180.0 and 180.0),
    constraint ck_session_locations_capture_window
        check (captured_at <= created_at + interval '5 minutes'),
    constraint ck_session_locations_legal_hold
        check (legal_hold_until is null or legal_hold_until >= created_at)
);

comment on table bodeul.companion_session_locations is '진행 중 세션의 최신 위치와 최근 10건 위치 이력';
comment on column bodeul.companion_session_locations.expires_at is '세션 종료 후 24시간 보관 만료 시각';

create index ix_session_locations_session_captured
    on bodeul.companion_session_locations (companion_session_id, captured_at desc, id desc)
    where deleted_at is null;
create index ix_session_locations_expiry
    on bodeul.companion_session_locations (expires_at)
    where expires_at is not null and deleted_at is null;
create index ix_session_locations_manager
    on bodeul.companion_session_locations (manager_user_id);

revoke all on table bodeul.companion_chat_messages from public, anon, authenticated, service_role;
revoke all on table bodeul.companion_chat_attachments from public, anon, authenticated, service_role;
revoke all on table bodeul.companion_chat_read_receipts from public, anon, authenticated, service_role;
revoke all on table bodeul.companion_session_locations from public, anon, authenticated, service_role;

grant select on table bodeul.companion_chat_messages to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.companion_chat_attachments to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.companion_chat_read_receipts to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.companion_session_locations to bodeul_core_runtime, bodeul_admin_runtime;

grant insert (
    companion_session_id,
    client_message_id,
    sender_user_id,
    sender_role,
    body,
    sent_at
) on table bodeul.companion_chat_messages to bodeul_core_runtime;

grant insert (
    chat_message_id,
    storage_path,
    file_name,
    content_type,
    size_bytes
) on table bodeul.companion_chat_attachments to bodeul_core_runtime;

grant insert (
    companion_session_id,
    user_id,
    last_read_message_id,
    last_read_at
) on table bodeul.companion_chat_read_receipts to bodeul_core_runtime;
grant update (
    last_read_message_id,
    last_read_at,
    updated_at
) on table bodeul.companion_chat_read_receipts to bodeul_core_runtime;

alter table bodeul.companion_chat_messages enable row level security;
alter table bodeul.companion_chat_attachments enable row level security;
alter table bodeul.companion_chat_read_receipts enable row level security;
alter table bodeul.companion_session_locations enable row level security;

create policy chat_messages_core_select
    on bodeul.companion_chat_messages for select to bodeul_core_runtime using (true);
create policy chat_messages_admin_select
    on bodeul.companion_chat_messages for select to bodeul_admin_runtime using (true);
create policy chat_messages_core_insert
    on bodeul.companion_chat_messages for insert to bodeul_core_runtime with check (true);

create policy chat_attachments_core_select
    on bodeul.companion_chat_attachments for select to bodeul_core_runtime using (true);
create policy chat_attachments_admin_select
    on bodeul.companion_chat_attachments for select to bodeul_admin_runtime using (true);
create policy chat_attachments_core_insert
    on bodeul.companion_chat_attachments for insert to bodeul_core_runtime with check (true);

create policy chat_read_receipts_core_select
    on bodeul.companion_chat_read_receipts for select to bodeul_core_runtime using (true);
create policy chat_read_receipts_admin_select
    on bodeul.companion_chat_read_receipts for select to bodeul_admin_runtime using (true);
create policy chat_read_receipts_core_insert
    on bodeul.companion_chat_read_receipts for insert to bodeul_core_runtime with check (true);
create policy chat_read_receipts_core_update
    on bodeul.companion_chat_read_receipts for update to bodeul_core_runtime using (true) with check (true);

create policy session_locations_core_select
    on bodeul.companion_session_locations for select to bodeul_core_runtime using (true);
create policy session_locations_admin_select
    on bodeul.companion_session_locations for select to bodeul_admin_runtime using (true);

create function bodeul.record_companion_location(
    p_companion_session_id uuid,
    p_client_location_id uuid,
    p_manager_user_id uuid,
    p_latitude double precision,
    p_longitude double precision,
    p_captured_at timestamptz
) returns uuid
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_location_id uuid;
begin
    if p_client_location_id is null or p_captured_at is null then
        raise exception '위치 식별자와 수집 시각이 필요합니다.' using errcode = '22023';
    end if;
    if p_captured_at < now() - interval '15 minutes'
            or p_captured_at > now() + interval '5 minutes' then
        raise exception '위치 수집 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;
    if not exists (
        select 1
        from bodeul.companion_sessions session
        where session.id = p_companion_session_id
          and session.manager_user_id = p_manager_user_id
          and session.current_status not in ('COMPLETED', 'CANCELED')
    ) then
        raise exception '진행 가능한 배정 세션을 찾을 수 없습니다.' using errcode = '42501';
    end if;

    insert into bodeul.companion_session_locations (
        companion_session_id,
        client_location_id,
        manager_user_id,
        latitude,
        longitude,
        captured_at
    ) values (
        p_companion_session_id,
        p_client_location_id,
        p_manager_user_id,
        p_latitude,
        p_longitude,
        p_captured_at
    )
    on conflict (companion_session_id, client_location_id) do nothing
    returning id into v_location_id;

    if v_location_id is null then
        select id into v_location_id
        from bodeul.companion_session_locations
        where companion_session_id = p_companion_session_id
          and client_location_id = p_client_location_id;
    end if;

    delete from bodeul.companion_session_locations location
    where location.companion_session_id = p_companion_session_id
      and (location.legal_hold_until is null or location.legal_hold_until <= now())
      and location.id not in (
          select recent.id
          from bodeul.companion_session_locations recent
          where recent.companion_session_id = p_companion_session_id
          order by recent.captured_at desc, recent.id desc
          limit 10
      );

    return v_location_id;
end;
$$;

alter function bodeul.record_companion_location(uuid, uuid, uuid, double precision, double precision, timestamptz)
    owner to bodeul_migration;
revoke all on function bodeul.record_companion_location(uuid, uuid, uuid, double precision, double precision, timestamptz)
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
grant execute on function bodeul.record_companion_location(uuid, uuid, uuid, double precision, double precision, timestamptz)
    to bodeul_core_runtime;

create function bodeul.schedule_companion_realtime_expiry()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_finished_at timestamptz;
begin
    if new.current_status not in ('COMPLETED', 'CANCELED')
            or old.current_status in ('COMPLETED', 'CANCELED') then
        return new;
    end if;

    v_finished_at := coalesce(new.completed_at, new.canceled_at, now());

    update bodeul.companion_chat_messages
    set expires_at = v_finished_at + interval '180 days'
    where companion_session_id = new.id and expires_at is null;

    update bodeul.companion_chat_attachments attachment
    set expires_at = v_finished_at + interval '30 days'
    from bodeul.companion_chat_messages message
    where attachment.chat_message_id = message.id
      and message.companion_session_id = new.id
      and attachment.expires_at is null;

    update bodeul.companion_session_locations
    set expires_at = v_finished_at + interval '24 hours'
    where companion_session_id = new.id and expires_at is null;

    return new;
end;
$$;

alter function bodeul.schedule_companion_realtime_expiry() owner to bodeul_migration;
revoke all on function bodeul.schedule_companion_realtime_expiry()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger schedule_companion_realtime_expiry_after_session_end
after update of current_status on bodeul.companion_sessions
for each row execute function bodeul.schedule_companion_realtime_expiry();
