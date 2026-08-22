do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'companion_sessions'
          and column_name like 'guide_%'
    ) then
        raise exception 'rollback 뒤 세션 snapshot 열이 남아 있습니다.';
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'hospital_guides'
          and column_name in ('revision', 'step_contract_version')
    ) then
        raise exception 'rollback 뒤 병원 가이드 version 열이 남아 있습니다.';
    end if;

    if to_regprocedure('bodeul.is_valid_guide_steps_v1(jsonb)') is not null
            or to_regprocedure('bodeul.bump_hospital_guide_revision()') is not null
            or to_regprocedure('bodeul.prevent_companion_guide_snapshot_change()') is not null then
        raise exception 'rollback 뒤 V14 함수가 남아 있습니다.';
    end if;

    if to_regprocedure(
        'bodeul.assign_companion_session(uuid,uuid,uuid,bigint,text)'
    ) is null then
        raise exception 'rollback이 기존 배정 함수를 복원하지 못했습니다.';
    end if;
end;
$$;

set role bodeul_admin_runtime;
select bodeul.assign_companion_session(
    '00000000-0000-0000-0000-000000000113',
    '00000000-0000-0000-0000-000000000102',
    '00000000-0000-0000-0000-000000000103',
    0,
    'rollback 함수 검증'
);
reset role;

do $$
begin
    if not exists (
        select 1
        from bodeul.companion_sessions
        where appointment_request_id = '00000000-0000-0000-0000-000000000113'
          and manager_user_id = '00000000-0000-0000-0000-000000000102'
          and current_status = 'READY'
    ) then
        raise exception 'rollback 뒤 기존 배정 함수 호출이 동작하지 않았습니다.';
    end if;
end;
$$;
