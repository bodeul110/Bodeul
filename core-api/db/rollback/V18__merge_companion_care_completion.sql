begin;

do $$
begin
    if exists (select 1 from bodeul.companion_session_artifacts limit 1)
            or exists (
                select 1
                from bodeul.companion_session_artifact_operations
                limit 1
            ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. 첨부 경로와 operation ledger를 export하고 Storage 원본을 정리한 뒤 두 테이블의 행을 삭제해 주세요.';
    end if;

    if exists (
        select 1
        from bodeul.companion_completion_v18_chat_expiry_baseline as baseline
        left join bodeul.companion_chat_messages as message
          on message.id = baseline.chat_message_id
        where message.id is null
    ) or exists (
        select 1
        from bodeul.companion_completion_v18_attachment_expiry_baseline as baseline
        left join bodeul.companion_chat_attachments as attachment
          on attachment.id = baseline.chat_attachment_id
        where attachment.id is null
    ) or exists (
        select 1
        from bodeul.companion_completion_v18_location_expiry_baseline as baseline
        left join bodeul.companion_session_locations as location
          on location.id = baseline.location_id
        where location.id is null
    ) or exists (
        select 1
        from bodeul.companion_completion_v18_consent_expiry_baseline as baseline
        left join bodeul.guardian_sharing_consents as consent
          on consent.id = baseline.consent_id
        where consent.id is null
    ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. migration 이전 실시간 데이터가 이미 삭제되어 백업 복원 없이는 원래 보존 상태를 복구할 수 없습니다.';
    end if;

    if exists (
        select 1
        from bodeul.companion_completion_v18_consent_expiry_baseline as baseline
        join bodeul.guardian_sharing_consents as consent
          on consent.id = baseline.consent_id
        where consent.care_ended_at is distinct from baseline.expected_care_ended_at
           or consent.expires_at is distinct from baseline.expected_expires_at
           or consent.expiry_finalized is distinct from baseline.expected_expiry_finalized
           or consent.revoked_by_user_id is distinct from baseline.original_revoked_by_user_id
           or consent.revoked_at is distinct from baseline.original_revoked_at
           or consent.version is distinct from baseline.expected_version
           or consent.updated_at is distinct from baseline.expected_updated_at
    ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. migration 이후 보호자 동의가 변경되어 원래 상태를 안전하게 복원할 수 없습니다.';
    end if;

    if exists (
        select 1
        from bodeul.companion_completion_v18_baseline as baseline
        join bodeul.companion_sessions as session
          on session.id = baseline.companion_session_id
        where session.current_status <> 'COMPLETED'
           or session.completed_at is distinct from baseline.expected_completed_at
           or session.care_ended_at is distinct from baseline.expected_care_ended_at
           or session.manager_journal <> ''
           or session.report_generation_status <> baseline.expected_report_generation_status
           or session.report_generation_attempts <> baseline.expected_report_generation_attempts
           or session.report_generation_last_error <> baseline.expected_report_generation_last_error
           or session.report_generation_updated_at
                is distinct from baseline.expected_report_generation_updated_at
    ) or exists (
        select 1
        from bodeul.companion_sessions as session
        where not exists (
                select 1
                from bodeul.companion_completion_v18_baseline as baseline
                where baseline.companion_session_id = session.id
            )
          and (
              session.current_status = 'CARE_ENDED'
              or session.care_ended_at is not null
              or btrim(session.manager_journal) <> ''
              or session.report_generation_status <> 'NOT_REQUESTED'
              or session.report_generation_attempts <> 0
              or btrim(session.report_generation_last_error) <> ''
              or session.report_generation_updated_at is not null
          )
    ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. 동행 종료 시각, 매니저 일지와 리포트 생성 상태를 export한 뒤 V18 완료 필드를 기본값으로 정리하고 다시 실행해 주세요.';
    end if;
end;
$$;

drop table if exists bodeul.companion_session_artifact_operations;
drop table if exists bodeul.companion_session_artifacts;

drop trigger if exists guard_companion_chat_attachment_write_before_insert
    on bodeul.companion_chat_attachments;
drop function if exists bodeul.guard_companion_chat_attachment_write();
drop trigger if exists guard_companion_chat_message_write_before_insert
    on bodeul.companion_chat_messages;
drop function if exists bodeul.guard_companion_chat_message_write();
drop trigger if exists finalize_guardian_consent_after_care_boundary_update
    on bodeul.companion_sessions;
drop function if exists bodeul.finalize_guardian_consent_after_care_boundary();
drop trigger if exists guard_guardian_consent_care_boundary_before_write
    on bodeul.guardian_sharing_consents;
drop function if exists bodeul.guard_guardian_consent_care_boundary();

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

drop trigger if exists schedule_companion_realtime_expiry_after_session_end
    on bodeul.companion_sessions;

create or replace function bodeul.schedule_companion_realtime_expiry()
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

create trigger schedule_companion_realtime_expiry_after_session_end
after update of current_status on bodeul.companion_sessions
for each row execute function bodeul.schedule_companion_realtime_expiry();

update bodeul.companion_chat_messages as message
set expires_at = baseline.original_expires_at
from bodeul.companion_completion_v18_chat_expiry_baseline as baseline
where message.id = baseline.chat_message_id;

update bodeul.companion_chat_attachments as attachment
set expires_at = baseline.original_expires_at
from bodeul.companion_completion_v18_attachment_expiry_baseline as baseline
where attachment.id = baseline.chat_attachment_id;

update bodeul.companion_session_locations as location
set expires_at = baseline.original_expires_at
from bodeul.companion_completion_v18_location_expiry_baseline as baseline
where location.id = baseline.location_id;

update bodeul.guardian_sharing_consents as consent
set care_ended_at = baseline.original_care_ended_at,
    expires_at = baseline.original_expires_at,
    expiry_finalized = baseline.original_expiry_finalized,
    revoked_by_user_id = baseline.original_revoked_by_user_id,
    revoked_at = baseline.original_revoked_at,
    version = baseline.original_version,
    updated_at = baseline.original_updated_at
from bodeul.companion_completion_v18_consent_expiry_baseline as baseline
where consent.id = baseline.consent_id;

alter table bodeul.companion_sessions
    drop constraint ck_companion_sessions_completion_timestamps;

update bodeul.companion_sessions as session
set completed_at = baseline.original_completed_at
from bodeul.companion_completion_v18_baseline as baseline
where session.id = baseline.companion_session_id;

alter table bodeul.companion_sessions
    drop constraint ck_companion_sessions_report_generation_attempts,
    drop constraint ck_companion_sessions_report_generation_status,
    drop constraint ck_companion_sessions_manager_journal,
    drop constraint ck_companion_sessions_status;

alter table bodeul.companion_sessions
    drop column report_generation_updated_at,
    drop column report_generation_last_error,
    drop column report_generation_attempts,
    drop column report_generation_status,
    drop column manager_journal,
    drop column care_ended_at,
    add constraint ck_companion_sessions_status
        check (current_status in (
            'READY', 'MEETING', 'WAITING', 'IN_TREATMENT',
            'PAYMENT', 'CANCELED', 'COMPLETED'
        ));

drop table bodeul.companion_completion_v18_location_expiry_baseline;
drop table bodeul.companion_completion_v18_attachment_expiry_baseline;
drop table bodeul.companion_completion_v18_chat_expiry_baseline;
drop table bodeul.companion_completion_v18_consent_expiry_baseline;
drop table bodeul.companion_completion_v18_baseline;

commit;
