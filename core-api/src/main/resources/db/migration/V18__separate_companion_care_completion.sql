alter table bodeul.companion_sessions
    drop constraint ck_companion_sessions_status;

alter table bodeul.companion_sessions
    add column care_ended_at timestamptz,
    add column manager_journal text not null default '',
    add column report_generation_status text not null default 'NOT_REQUESTED',
    add column report_generation_attempts integer not null default 0,
    add column report_generation_last_error text not null default '',
    add column report_generation_updated_at timestamptz,
    add constraint ck_companion_sessions_status
        check (current_status in (
            'READY', 'MEETING', 'WAITING', 'IN_TREATMENT',
            'PAYMENT', 'CARE_ENDED', 'CANCELED', 'COMPLETED'
        )),
    add constraint ck_companion_sessions_manager_journal
        check (char_length(manager_journal) <= 300),
    add constraint ck_companion_sessions_report_generation_status
        check (report_generation_status in ('NOT_REQUESTED', 'PENDING', 'READY', 'FAILED')),
    add constraint ck_companion_sessions_report_generation_attempts
        check (report_generation_attempts >= 0);

create table bodeul.companion_completion_v18_baseline (
    companion_session_id uuid primary key,
    original_completed_at timestamptz,
    expected_completed_at timestamptz not null,
    expected_care_ended_at timestamptz not null,
    expected_report_generation_status text not null,
    expected_report_generation_attempts integer not null,
    expected_report_generation_last_error text not null,
    expected_report_generation_updated_at timestamptz not null,
    recorded_at timestamptz not null default transaction_timestamp(),
    constraint fk_companion_completion_v18_baseline_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id)
            on delete cascade
);

revoke all on table bodeul.companion_completion_v18_baseline
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

insert into bodeul.companion_completion_v18_baseline (
    companion_session_id,
    original_completed_at,
    expected_completed_at,
    expected_care_ended_at,
    expected_report_generation_status,
    expected_report_generation_attempts,
    expected_report_generation_last_error,
    expected_report_generation_updated_at
)
select
    session.id,
    session.completed_at,
    coalesce(session.completed_at, session.updated_at, session.started_at, transaction_timestamp()),
    coalesce(session.completed_at, session.updated_at, session.started_at, transaction_timestamp()),
    case when report.id is not null then 'READY' else 'FAILED' end,
    case when report.id is not null then 1 else 0 end,
    case when report.id is not null then '' else 'LEGACY_REPORT_MISSING' end,
    coalesce(session.completed_at, session.updated_at, transaction_timestamp())
from bodeul.companion_sessions as session
left join bodeul.session_reports as report
    on report.companion_session_id = session.id
where session.current_status = 'COMPLETED';

create table bodeul.companion_completion_v18_chat_expiry_baseline (
    chat_message_id uuid primary key,
    original_expires_at timestamptz
);

create table bodeul.companion_completion_v18_attachment_expiry_baseline (
    chat_attachment_id uuid primary key,
    original_expires_at timestamptz
);

create table bodeul.companion_completion_v18_location_expiry_baseline (
    location_id uuid primary key,
    original_expires_at timestamptz
);

create table bodeul.companion_completion_v18_consent_expiry_baseline (
    consent_id uuid primary key,
    original_care_ended_at timestamptz,
    original_expires_at timestamptz not null,
    original_expiry_finalized boolean not null,
    original_revoked_by_user_id uuid,
    original_revoked_at timestamptz,
    original_version bigint not null,
    original_updated_at timestamptz not null,
    expected_care_ended_at timestamptz,
    expected_expires_at timestamptz,
    expected_expiry_finalized boolean,
    expected_version bigint,
    expected_updated_at timestamptz
);

revoke all on table bodeul.companion_completion_v18_chat_expiry_baseline
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.companion_completion_v18_attachment_expiry_baseline
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.companion_completion_v18_location_expiry_baseline
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.companion_completion_v18_consent_expiry_baseline
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

insert into bodeul.companion_completion_v18_chat_expiry_baseline (
    chat_message_id,
    original_expires_at
)
select message.id, message.expires_at
from bodeul.companion_chat_messages as message
join bodeul.companion_completion_v18_baseline as baseline
  on baseline.companion_session_id = message.companion_session_id;

insert into bodeul.companion_completion_v18_attachment_expiry_baseline (
    chat_attachment_id,
    original_expires_at
)
select attachment.id, attachment.expires_at
from bodeul.companion_chat_attachments as attachment
join bodeul.companion_chat_messages as message
  on message.id = attachment.chat_message_id
join bodeul.companion_completion_v18_baseline as baseline
  on baseline.companion_session_id = message.companion_session_id;

insert into bodeul.companion_completion_v18_location_expiry_baseline (
    location_id,
    original_expires_at
)
select location.id, location.expires_at
from bodeul.companion_session_locations as location
join bodeul.companion_completion_v18_baseline as baseline
  on baseline.companion_session_id = location.companion_session_id;

insert into bodeul.companion_completion_v18_consent_expiry_baseline (
    consent_id,
    original_care_ended_at,
    original_expires_at,
    original_expiry_finalized,
    original_revoked_by_user_id,
    original_revoked_at,
    original_version,
    original_updated_at
)
select
    consent.id,
    consent.care_ended_at,
    consent.expires_at,
    consent.expiry_finalized,
    consent.revoked_by_user_id,
    consent.revoked_at,
    consent.version,
    consent.updated_at
from bodeul.guardian_sharing_consents as consent
join bodeul.companion_sessions as session
  on session.appointment_request_id = consent.appointment_request_id
join bodeul.companion_completion_v18_baseline as baseline
  on baseline.companion_session_id = session.id;

create function bodeul.guard_guardian_consent_care_boundary()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_session_status text;
    v_care_ended_at timestamptz;
    v_canceled_at timestamptz;
    v_completed_at timestamptz;
    v_care_boundary timestamptz;
begin
    if tg_op = 'INSERT' then
        select session.current_status, session.care_ended_at,
               session.canceled_at, session.completed_at
        into v_session_status, v_care_ended_at, v_canceled_at, v_completed_at
        from bodeul.companion_sessions as session
        where session.appointment_request_id = new.appointment_request_id
        for update;

        if found and (
            v_care_ended_at is not null
            or v_session_status in ('CARE_ENDED', 'COMPLETED', 'CANCELED')
        ) then
            raise exception '동행 종료 뒤에는 보호자 정보공유 동의를 다시 부여할 수 없습니다.'
                using errcode = '55000';
        end if;
        if new.care_ended_at is not null
                or new.expiry_finalized
                or new.revoked_by_user_id is not null
                or new.revoked_at is not null
                or new.version <> 0 then
            raise exception '새 보호자 정보공유 동의는 종료 전 활성 상태로만 만들 수 있습니다.'
                using errcode = '55000';
        end if;
        return new;
    end if;

    if new.id is distinct from old.id
            or new.appointment_request_id is distinct from old.appointment_request_id
            or new.created_at is distinct from old.created_at then
        raise exception '보호자 정보공유 동의의 식별자와 예약 연결은 변경할 수 없습니다.'
            using errcode = '55000';
    end if;

    if not old.expiry_finalized and new.expiry_finalized then
        select session.current_status, session.care_ended_at,
               session.canceled_at, session.completed_at
        into v_session_status, v_care_ended_at, v_canceled_at, v_completed_at
        from bodeul.companion_sessions as session
        where session.appointment_request_id = new.appointment_request_id
        for update;

        if not found then
            raise exception '동행 종료 경계가 없는 보호자 동의는 만료를 확정할 수 없습니다.'
                using errcode = '55000';
        end if;
        if v_care_ended_at is not null then
            v_care_boundary := v_care_ended_at;
        elsif v_session_status = 'CANCELED' then
            v_care_boundary := coalesce(v_canceled_at, now());
        elsif v_session_status = 'COMPLETED' then
            v_care_boundary := coalesce(v_completed_at, now());
        else
            raise exception '동행 종료 전에는 보호자 동의 만료를 확정할 수 없습니다.'
                using errcode = '55000';
        end if;

        if new.patient_user_id is distinct from old.patient_user_id
                or new.guardian_user_id is distinct from old.guardian_user_id
                or new.scopes is distinct from old.scopes
                or new.policy_version is distinct from old.policy_version
                or new.granted_by_user_id is distinct from old.granted_by_user_id
                or new.adult_self_declared_at is distinct from old.adult_self_declared_at
                or new.granted_at is distinct from old.granted_at
                or new.revoked_by_user_id is distinct from old.revoked_by_user_id
                or new.revoked_at is distinct from old.revoked_at
                or new.care_ended_at is distinct from v_care_boundary
                or new.expires_at is distinct from v_care_boundary + interval '7 days'
                or new.version <> old.version + 1
                or new.updated_at < old.updated_at then
            raise exception '보호자 정보공유 동의 만료는 실제 동행 종료 경계로만 확정할 수 있습니다.'
                using errcode = '55000';
        end if;
        return new;
    end if;

    if not old.expiry_finalized and (
        new.care_ended_at is not null or new.expiry_finalized
    ) then
        raise exception '동행 종료 전 보호자 정보공유 동의에는 종료 경계를 기록할 수 없습니다.'
            using errcode = '55000';
    end if;

    if old.expiry_finalized and (
        new.patient_user_id is distinct from old.patient_user_id
        or new.guardian_user_id is distinct from old.guardian_user_id
        or new.scopes is distinct from old.scopes
        or new.policy_version is distinct from old.policy_version
        or new.granted_by_user_id is distinct from old.granted_by_user_id
        or new.adult_self_declared_at is distinct from old.adult_self_declared_at
        or new.granted_at is distinct from old.granted_at
        or new.expires_at is distinct from old.expires_at
        or new.care_ended_at is distinct from old.care_ended_at
        or new.expiry_finalized is distinct from old.expiry_finalized
    ) then
        raise exception '확정된 보호자 정보공유 동의 만료 경계는 변경할 수 없습니다.'
            using errcode = '55000';
    end if;

    if old.expiry_finalized then
        if old.revoked_at is null
                and new.revoked_at is not null
                and new.revoked_by_user_id = old.patient_user_id then
            if new.version <> old.version + 1
                    or new.updated_at < old.updated_at
                    or new.updated_at < new.revoked_at then
                raise exception '보호자 정보공유 동의 철회 버전이 올바르지 않습니다.'
                    using errcode = '55000';
            end if;
        elsif new.revoked_at is distinct from old.revoked_at
                or new.revoked_by_user_id is distinct from old.revoked_by_user_id
                or new.version is distinct from old.version
                or new.updated_at is distinct from old.updated_at then
            raise exception '확정된 보호자 정보공유 동의 철회는 환자가 최초 한 번만 기록할 수 있습니다.'
                using errcode = '55000';
        end if;
    end if;
    return new;
end;
$$;

alter function bodeul.guard_guardian_consent_care_boundary() owner to bodeul_migration;
revoke all on function bodeul.guard_guardian_consent_care_boundary()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger guard_guardian_consent_care_boundary_before_write
before insert or update on bodeul.guardian_sharing_consents
for each row execute function bodeul.guard_guardian_consent_care_boundary();

create function bodeul.finalize_guardian_consent_after_care_boundary()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_care_boundary timestamptz;
begin
    if new.care_ended_at is not null then
        v_care_boundary := new.care_ended_at;
    elsif new.current_status = 'CANCELED' then
        v_care_boundary := coalesce(new.canceled_at, now());
    elsif new.current_status = 'COMPLETED' then
        v_care_boundary := coalesce(new.completed_at, now());
    else
        return new;
    end if;

    update bodeul.guardian_sharing_consents
    set care_ended_at = v_care_boundary,
        expires_at = v_care_boundary + interval '7 days',
        expiry_finalized = true,
        version = version + 1,
        updated_at = now()
    where appointment_request_id = new.appointment_request_id
      and not expiry_finalized;
    return new;
end;
$$;

alter function bodeul.finalize_guardian_consent_after_care_boundary()
    owner to bodeul_migration;
revoke all on function bodeul.finalize_guardian_consent_after_care_boundary()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger finalize_guardian_consent_after_care_boundary_update
after update of current_status, care_ended_at on bodeul.companion_sessions
for each row execute function bodeul.finalize_guardian_consent_after_care_boundary();

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

    if exists (
        select 1
        from bodeul.companion_sessions as session
        where session.id = v_session_id
          and (
              session.care_ended_at is not null
              or session.current_status in ('CARE_ENDED', 'COMPLETED', 'CANCELED')
          )
    ) then
        return new;
    end if;

    if to_regprocedure('bodeul.send_companion_realtime_signal(jsonb,text,text)') is null then
        return new;
    end if;

    v_payload := jsonb_build_object(
        'sessionId', v_session_id::text,
        'resource', v_resource,
        'recordId', v_record_id::text
    );

    perform bodeul.send_companion_realtime_signal(
        v_payload,
        v_event,
        'companion-session:' || v_session_id::text
    );
    return new;
exception
    when others then
        raise warning '동행 Realtime 알림 전송을 건너뛰었습니다. session_id=%', v_session_id;
        return new;
end;
$$;

alter function bodeul.broadcast_companion_realtime_change() owner to bodeul_migration;
revoke all on function bodeul.broadcast_companion_realtime_change()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create or replace function bodeul.record_companion_location(
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
    v_session_manager_id uuid;
    v_session_status text;
    v_care_ended_at timestamptz;
begin
    if p_client_location_id is null or p_captured_at is null then
        raise exception '위치 식별자와 수집 시각이 필요합니다.' using errcode = '22023';
    end if;
    if p_captured_at < now() - interval '15 minutes'
            or p_captured_at > now() + interval '5 minutes' then
        raise exception '위치 수집 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;
    select session.manager_user_id, session.current_status, session.care_ended_at
    into v_session_manager_id, v_session_status, v_care_ended_at
    from bodeul.companion_sessions session
    where session.id = p_companion_session_id
    for update;

    if not found
            or v_session_manager_id is distinct from p_manager_user_id
            or v_care_ended_at is not null
            or v_session_status in ('CARE_ENDED', 'COMPLETED', 'CANCELED') then
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

create function bodeul.guard_companion_chat_message_write()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_session_status text;
    v_care_ended_at timestamptz;
begin
    select session.current_status, session.care_ended_at
    into v_session_status, v_care_ended_at
    from bodeul.companion_sessions session
    where session.id = new.companion_session_id
    for update;

    if not found
            or v_care_ended_at is not null
            or v_session_status in ('CARE_ENDED', 'COMPLETED', 'CANCELED') then
        raise exception '동행 종료 후에는 새 채팅을 저장할 수 없습니다.' using errcode = '42501';
    end if;
    return new;
end;
$$;

alter function bodeul.guard_companion_chat_message_write() owner to bodeul_migration;
revoke all on function bodeul.guard_companion_chat_message_write()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger guard_companion_chat_message_write_before_insert
before insert on bodeul.companion_chat_messages
for each row execute function bodeul.guard_companion_chat_message_write();

create function bodeul.guard_companion_chat_attachment_write()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_session_status text;
    v_care_ended_at timestamptz;
begin
    select session.current_status, session.care_ended_at
    into v_session_status, v_care_ended_at
    from bodeul.companion_chat_messages message
    join bodeul.companion_sessions session
      on session.id = message.companion_session_id
    where message.id = new.chat_message_id
    for update of session;

    if not found
            or v_care_ended_at is not null
            or v_session_status in ('CARE_ENDED', 'COMPLETED', 'CANCELED') then
        raise exception '동행 종료 후에는 새 첨부를 저장할 수 없습니다.' using errcode = '42501';
    end if;
    return new;
end;
$$;

alter function bodeul.guard_companion_chat_attachment_write() owner to bodeul_migration;
revoke all on function bodeul.guard_companion_chat_attachment_write()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger guard_companion_chat_attachment_write_before_insert
before insert on bodeul.companion_chat_attachments
for each row execute function bodeul.guard_companion_chat_attachment_write();

drop trigger if exists schedule_companion_realtime_expiry_after_session_end
    on bodeul.companion_sessions;

create or replace function bodeul.schedule_companion_realtime_expiry()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_retention_started_at timestamptz;
begin
    if new.care_ended_at is not null then
        v_retention_started_at := new.care_ended_at;
    elsif new.current_status = 'CANCELED' then
        v_retention_started_at := coalesce(new.canceled_at, now());
    else
        return new;
    end if;

    update bodeul.companion_chat_messages
    set expires_at = v_retention_started_at + interval '180 days'
    where companion_session_id = new.id and expires_at is null;

    update bodeul.companion_chat_attachments attachment
    set expires_at = v_retention_started_at + interval '30 days'
    from bodeul.companion_chat_messages message
    where attachment.chat_message_id = message.id
      and message.companion_session_id = new.id
      and attachment.expires_at is null;

    update bodeul.companion_session_locations
    set expires_at = v_retention_started_at + interval '24 hours'
    where companion_session_id = new.id and expires_at is null;

    return new;
end;
$$;

create trigger schedule_companion_realtime_expiry_after_session_end
after update of current_status, care_ended_at on bodeul.companion_sessions
for each row execute function bodeul.schedule_companion_realtime_expiry();

update bodeul.companion_sessions as session
set care_ended_at = baseline.expected_care_ended_at,
    completed_at = baseline.expected_completed_at,
    report_generation_status = baseline.expected_report_generation_status,
    report_generation_attempts = baseline.expected_report_generation_attempts,
    report_generation_last_error = baseline.expected_report_generation_last_error,
    report_generation_updated_at = baseline.expected_report_generation_updated_at
from bodeul.companion_completion_v18_baseline as baseline
where session.id = baseline.companion_session_id;

update bodeul.companion_completion_v18_consent_expiry_baseline as baseline
set expected_care_ended_at = consent.care_ended_at,
    expected_expires_at = consent.expires_at,
    expected_expiry_finalized = consent.expiry_finalized,
    expected_version = consent.version,
    expected_updated_at = consent.updated_at
from bodeul.guardian_sharing_consents as consent
where consent.id = baseline.consent_id;

alter table bodeul.companion_completion_v18_consent_expiry_baseline
    alter column expected_expires_at set not null,
    alter column expected_expiry_finalized set not null,
    alter column expected_version set not null,
    alter column expected_updated_at set not null;

alter table bodeul.companion_sessions
    add constraint ck_companion_sessions_completion_timestamps
        check (
            (current_status not in ('CARE_ENDED', 'COMPLETED') or care_ended_at is not null)
            and (current_status <> 'COMPLETED' or completed_at is not null)
        );

comment on column bodeul.companion_sessions.care_ended_at is
    '환자 인계를 확인해 실제 동행을 종료한 최초 서버 시각';
comment on column bodeul.companion_sessions.completed_at is
    '선택 매니저 일지를 확정해 최종 기록을 완료한 최초 서버 시각';
comment on column bodeul.companion_sessions.manager_journal is
    '최종 완료 전에 매니저가 선택 입력하는 최대 300자 일지';
comment on column bodeul.companion_sessions.report_generation_status is
    '세션 완료와 별도로 재시도하는 최종 리포트 저장 상태';

grant update (
    current_step_order,
    current_status,
    care_ended_at,
    manager_journal,
    report_generation_status,
    report_generation_attempts,
    report_generation_last_error,
    report_generation_updated_at,
    completed_at,
    updated_at,
    version
) on table bodeul.companion_sessions to bodeul_core_runtime;

create table bodeul.companion_session_artifacts (
    id uuid primary key default gen_random_uuid(),
    companion_session_id uuid not null,
    purpose text not null,
    client_request_id uuid not null,
    item_order integer not null,
    storage_path text not null,
    file_name text not null,
    content_type text not null,
    size_bytes bigint not null,
    sha256 text not null,
    uploaded_by_user_id uuid,
    created_at timestamptz not null default now(),
    constraint fk_companion_session_artifacts_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id)
            on delete cascade,
    constraint fk_companion_session_artifacts_uploader
        foreign key (uploaded_by_user_id) references bodeul.app_users (id)
            on delete set null,
    constraint uq_companion_session_artifacts_request_item
        unique (companion_session_id, purpose, client_request_id, item_order),
    constraint uq_companion_session_artifacts_session_purpose_item
        unique (companion_session_id, purpose, item_order),
    constraint uq_companion_session_artifacts_storage_path unique (storage_path),
    constraint ck_companion_session_artifacts_purpose
        check (purpose in ('PAYMENT_EVIDENCE', 'PRESCRIPTION_IMAGE')),
    constraint ck_companion_session_artifacts_item_order
        check (
            (purpose = 'PAYMENT_EVIDENCE' and item_order = 0)
            or (purpose = 'PRESCRIPTION_IMAGE' and item_order between 0 and 2)
        ),
    constraint ck_companion_session_artifacts_file_name
        check (btrim(file_name) <> '' and char_length(file_name) <= 255),
    constraint ck_companion_session_artifacts_content_type
        check (
            (purpose = 'PAYMENT_EVIDENCE'
                and content_type in ('image/jpeg', 'image/png', 'application/pdf'))
            or (purpose = 'PRESCRIPTION_IMAGE'
                and content_type in ('image/jpeg', 'image/png'))
        ),
    constraint ck_companion_session_artifacts_size
        check (size_bytes > 0 and size_bytes <= 10485760),
    constraint ck_companion_session_artifacts_sha256
        check (sha256 ~ '^[0-9a-f]{64}$')
);

comment on table bodeul.companion_session_artifacts is
    '가이드 8 선택 결제 증빙과 가이드 10 선택 처방 이미지를 세션별로 관리하는 메타데이터';
comment on column bodeul.companion_session_artifacts.client_request_id is
    '중복 탭과 네트워크 재시도에서 같은 교체 요청을 식별하는 클라이언트 UUID';
comment on column bodeul.companion_session_artifacts.sha256 is
    '다운로드 때 원본 바이트 무결성을 확인하는 소문자 SHA-256';

create table bodeul.companion_session_artifact_operations (
    companion_session_id uuid not null,
    purpose text not null,
    client_request_id uuid not null,
    payload_fingerprint text not null,
    result_revision bigint not null,
    created_at timestamptz not null default now(),
    primary key (companion_session_id, purpose, client_request_id),
    constraint fk_companion_session_artifact_operations_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id)
            on delete cascade,
    constraint ck_companion_session_artifact_operations_purpose
        check (purpose in ('PAYMENT_EVIDENCE', 'PRESCRIPTION_IMAGE')),
    constraint ck_companion_session_artifact_operations_fingerprint
        check (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_companion_session_artifact_operations_revision
        check (result_revision > 0),
    constraint uq_companion_session_artifact_operations_revision
        unique (companion_session_id, purpose, result_revision)
);

comment on table bodeul.companion_session_artifact_operations is
    '첨부가 교체 또는 삭제된 뒤에도 재시도를 판별하는 영구 요청 원장';

create index ix_companion_session_artifacts_session_purpose
    on bodeul.companion_session_artifacts (companion_session_id, purpose, item_order);
create index ix_companion_session_artifacts_uploader
    on bodeul.companion_session_artifacts (uploaded_by_user_id);

revoke all on table bodeul.companion_session_artifacts
    from public, anon, authenticated, service_role;
revoke all on table bodeul.companion_session_artifact_operations
    from public, anon, authenticated, service_role;
grant select, insert, delete on table bodeul.companion_session_artifacts
    to bodeul_core_runtime;
grant select, insert on table bodeul.companion_session_artifact_operations
    to bodeul_core_runtime;
grant select on table bodeul.companion_session_artifacts
    to bodeul_admin_runtime;
grant select on table bodeul.companion_session_artifact_operations
    to bodeul_admin_runtime;

alter table bodeul.companion_session_artifacts enable row level security;
alter table bodeul.companion_session_artifact_operations enable row level security;

create policy companion_session_artifacts_core_select
    on bodeul.companion_session_artifacts
    for select to bodeul_core_runtime using (true);
create policy companion_session_artifacts_core_insert
    on bodeul.companion_session_artifacts
    for insert to bodeul_core_runtime with check (true);
create policy companion_session_artifacts_core_delete
    on bodeul.companion_session_artifacts
    for delete to bodeul_core_runtime using (true);
create policy companion_session_artifacts_admin_select
    on bodeul.companion_session_artifacts
    for select to bodeul_admin_runtime using (true);
create policy companion_session_artifact_operations_core_select
    on bodeul.companion_session_artifact_operations
    for select to bodeul_core_runtime using (true);
create policy companion_session_artifact_operations_core_insert
    on bodeul.companion_session_artifact_operations
    for insert to bodeul_core_runtime with check (true);
create policy companion_session_artifact_operations_admin_select
    on bodeul.companion_session_artifact_operations
    for select to bodeul_admin_runtime using (true);
