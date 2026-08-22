begin;
set local role bodeul_migration;

do $$
begin
    if exists (
        select 1
        from bodeul.hospital_guides
        where step_contract_version = 1 or revision > 1
    ) then
        raise exception 'STEP_CODE_V1 가이드가 있어 V14 rollback을 중단합니다.';
    end if;

    if exists (
        select 1
        from bodeul.companion_sessions
        where guide_snapshot_source in (
            'HOSPITAL_GUIDE_STEP_CODE_V1',
            'LEGACY_HOSPITAL_GUIDE_V0',
            'GUIDE_NOT_FOUND'
        )
    ) then
        raise exception 'V14 이후 생성된 동행 세션이 있어 rollback을 중단합니다.';
    end if;
end;
$$;

create or replace function bodeul.assign_companion_session(
    p_appointment_request_id uuid,
    p_manager_user_id uuid,
    p_actor_admin_user_id uuid,
    p_expected_appointment_version bigint,
    p_reason text default ''
) returns uuid
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_appointment_status text;
    v_appointment_version bigint;
    v_previous_manager_user_id uuid;
    v_session_id uuid;
begin
    if p_expected_appointment_version is null or p_expected_appointment_version < 0 then
        raise exception '예약 버전이 필요합니다.' using errcode = '22023';
    end if;

    if not exists (
        select 1 from bodeul.app_users
        where id = p_actor_admin_user_id and role = 'ADMIN'
    ) then
        raise exception '관리자 권한을 확인할 수 없습니다.' using errcode = '42501';
    end if;

    if not exists (
        select 1 from bodeul.app_users
        where id = p_manager_user_id and role = 'MANAGER'
    ) then
        raise exception '배정 대상 매니저를 확인할 수 없습니다.' using errcode = '23503';
    end if;

    select status, version, manager_user_id
    into v_appointment_status, v_appointment_version, v_previous_manager_user_id
    from bodeul.appointment_requests
    where id = p_appointment_request_id
    for update;

    if not found then
        raise exception '예약을 찾을 수 없습니다.' using errcode = 'P0002';
    end if;
    if v_appointment_status <> 'REQUESTED' then
        raise exception '요청 상태의 예약만 매칭할 수 있습니다.' using errcode = 'P0001';
    end if;
    if v_appointment_version <> p_expected_appointment_version then
        raise exception '예약이 다른 요청에서 변경되었습니다.' using errcode = '40001';
    end if;

    insert into bodeul.companion_sessions (
        appointment_request_id,
        manager_user_id,
        current_status,
        created_at,
        updated_at
    ) values (
        p_appointment_request_id,
        p_manager_user_id,
        'READY',
        now(),
        now()
    )
    returning id into v_session_id;

    update bodeul.appointment_requests
    set manager_user_id = p_manager_user_id,
        status = 'MATCHED',
        updated_at = now(),
        version = version + 1
    where id = p_appointment_request_id;

    insert into bodeul.companion_session_assignment_audits (
        appointment_request_id,
        companion_session_id,
        previous_manager_user_id,
        assigned_manager_user_id,
        actor_admin_user_id,
        reason
    ) values (
        p_appointment_request_id,
        v_session_id,
        v_previous_manager_user_id,
        p_manager_user_id,
        p_actor_admin_user_id,
        coalesce(p_reason, '')
    );

    return v_session_id;
end;
$$;

alter function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    owner to bodeul_migration;
revoke all on function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    to bodeul_admin_runtime;

drop trigger if exists prevent_companion_guide_snapshot_change_before_update
    on bodeul.companion_sessions;
drop function if exists bodeul.prevent_companion_guide_snapshot_change();
drop index if exists bodeul.ix_companion_sessions_guide_revision;

alter table bodeul.companion_sessions
    drop constraint if exists ck_companion_sessions_guide_snapshot,
    drop constraint if exists fk_companion_sessions_guide,
    drop column if exists guide_snapshot_source,
    drop column if exists guide_steps_snapshot,
    drop column if exists guide_step_contract_version,
    drop column if exists guide_revision,
    drop column if exists guide_id;

drop trigger if exists bump_hospital_guide_revision_before_update
    on bodeul.hospital_guides;
drop function if exists bodeul.bump_hospital_guide_revision();

alter table bodeul.hospital_guides
    drop constraint if exists ck_hospital_guides_step_contract,
    drop constraint if exists ck_hospital_guides_step_contract_version,
    drop constraint if exists ck_hospital_guides_revision,
    drop column if exists step_contract_version,
    drop column if exists revision;

drop function if exists bodeul.is_valid_guide_steps_v1(jsonb);

commit;
