begin;
set local role bodeul_migration;

do $$
declare
    v_count integer;
    v_steps jsonb;
begin
    foreach v_count in array array[0, 1, 7, 13, 14]
    loop
        select coalesce(
            jsonb_agg(
                jsonb_build_object(
                    'code', 'STEP_' || item::text,
                    'order', item,
                    'title', '단계 ' || item::text,
                    'description', ''
                ) order by item
            ),
            '[]'::jsonb
        )
        into v_steps
        from generate_series(1, v_count) as series(item);

        if not bodeul.is_valid_guide_steps_v1(v_steps) then
            raise exception '%단계 정상 가이드가 거부되었습니다.', v_count;
        end if;
    end loop;

    if bodeul.is_valid_guide_steps_v1(
        '[
          {"code":"DUPLICATE","order":1,"title":"첫 단계","description":""},
          {"code":"DUPLICATE","order":2,"title":"둘째 단계","description":""}
        ]'::jsonb
    ) then
        raise exception '중복 stepCode가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_steps_v1(
        '[{"code":"lower_case","order":1,"title":"단계","description":""}]'::jsonb
    ) then
        raise exception '소문자 stepCode가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_steps_v1(
        '[{"code":"ORDER_GAP","order":2,"title":"단계","description":""}]'::jsonb
    ) then
        raise exception '1부터 연속되지 않은 order가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_steps_v1(
        '[{"code":"DECIMAL_ORDER","order":1.5,"title":"단계","description":""}]'::jsonb
    ) then
        raise exception '정수가 아닌 order가 허용되었습니다.';
    end if;

    if bodeul.is_valid_guide_steps_v1(
        '[{"code":"BLANK_TITLE","order":1,"title":" ","description":""}]'::jsonb
    ) then
        raise exception '빈 title이 허용되었습니다.';
    end if;
end;
$$;

do $$
begin
    if to_regprocedure(
        'bodeul.assign_companion_session(uuid,uuid,uuid,bigint,text)'
    ) is null then
        raise exception '기존 배정 함수 시그니처가 유지되지 않았습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.assign_companion_session(uuid,uuid,uuid,bigint,text)',
        'EXECUTE'
    ) then
        raise exception '관리자 runtime의 배정 함수 실행 권한이 없습니다.';
    end if;

    if has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.assign_companion_session(uuid,uuid,uuid,bigint,text)',
        'EXECUTE'
    ) then
        raise exception 'Core runtime에 관리자 배정 함수 권한이 노출되었습니다.';
    end if;

    if has_column_privilege(
        'bodeul_core_runtime',
        'bodeul.companion_sessions',
        'guide_steps_snapshot',
        'UPDATE'
    ) then
        raise exception 'Core runtime이 가이드 snapshot을 직접 수정할 수 있습니다.';
    end if;

    if exists (
        select 1
        from pg_constraint constraint_row
        where constraint_row.conrelid = 'bodeul.companion_sessions'::regclass
          and pg_get_constraintdef(constraint_row.oid) like '%current_step_order%guide_steps_snapshot%'
    ) then
        raise exception '롤링 배포 전에 current_step_order 상한이 DB에 고정되었습니다.';
    end if;
end;
$$;

insert into bodeul.app_users (id, firebase_uid, role, name)
values
    ('00000000-0000-0000-0000-000000000201', 'guide-check-patient', 'PATIENT', '검증 환자'),
    ('00000000-0000-0000-0000-000000000202', 'guide-check-manager', 'MANAGER', '검증 매니저'),
    ('00000000-0000-0000-0000-000000000203', 'guide-check-admin', 'ADMIN', '검증 관리자');

insert into bodeul.hospital_guides (
    id,
    hospital_name,
    department_name,
    steps,
    step_contract_version
) values (
    '00000000-0000-0000-0000-000000000220',
    '코드병원',
    '신경과',
    '[{"code":"FIRST_STEP","order":1,"title":"첫 단계","description":"첫 설명"}]'::jsonb,
    1
);

insert into bodeul.appointment_requests (
    id,
    patient_user_id,
    requester_user_id,
    requester_role,
    patient_name,
    hospital_name,
    department_name,
    appointment_at,
    appointment_at_epoch_millis,
    appointment_date_key,
    mobility_support_code,
    trip_type_code,
    manager_gender_preference_code,
    status,
    payment_method_code,
    coupon_code,
    payment_status_code,
    created_at,
    updated_at
) values
    (
        '00000000-0000-0000-0000-000000000211',
        '00000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000201',
        'PATIENT',
        '검증 환자',
        '코드병원',
        '신경과',
        '2026-10-01T01:00:00Z',
        1790816400000,
        '2026-10-01',
        'INDEPENDENT',
        'ONE_WAY',
        'ANY',
        'REQUESTED',
        'CARD',
        'NONE',
        'AUTHORIZED',
        '2026-08-22T00:00:00Z',
        '2026-08-22T00:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000212',
        '00000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000201',
        'PATIENT',
        '검증 환자',
        '가이드없는병원',
        '내과',
        '2026-10-02T01:00:00Z',
        1790902800000,
        '2026-10-02',
        'INDEPENDENT',
        'ONE_WAY',
        'ANY',
        'REQUESTED',
        'CARD',
        'NONE',
        'AUTHORIZED',
        '2026-08-22T00:00:00Z',
        '2026-08-22T00:00:00Z'
    );

do $$
declare
    v_session_id uuid;
    v_missing_session_id uuid;
    v_snapshot jsonb;
    v_snapshot_revision bigint;
begin
    v_session_id := bodeul.assign_companion_session(
        '00000000-0000-0000-0000-000000000211',
        '00000000-0000-0000-0000-000000000202',
        '00000000-0000-0000-0000-000000000203',
        0,
        'snapshot 검증'
    );

    select guide_steps_snapshot, guide_revision
    into v_snapshot, v_snapshot_revision
    from bodeul.companion_sessions
    where id = v_session_id;

    if v_snapshot_revision <> 1
            or jsonb_array_length(v_snapshot) <> 1
            or v_snapshot -> 0 ->> 'code' <> 'FIRST_STEP' then
        raise exception '신규 세션에 가이드 revision과 단계가 고정되지 않았습니다.';
    end if;

    update bodeul.hospital_guides
    set steps = '[
      {"code":"FIRST_STEP","order":1,"title":"수정된 첫 단계","description":"수정 설명"},
      {"code":"UNLISTED_EXTENSION","order":2,"title":"추가 단계","description":"추가 설명"}
    ]'::jsonb
    where id = '00000000-0000-0000-0000-000000000220';

    if (
        select revision
        from bodeul.hospital_guides
        where id = '00000000-0000-0000-0000-000000000220'
    ) <> 2 then
        raise exception '가이드 내용 변경 시 revision이 증가하지 않았습니다.';
    end if;

    select guide_steps_snapshot, guide_revision
    into v_snapshot, v_snapshot_revision
    from bodeul.companion_sessions
    where id = v_session_id;

    if v_snapshot_revision <> 1
            or jsonb_array_length(v_snapshot) <> 1
            or v_snapshot -> 0 ->> 'title' <> '첫 단계' then
        raise exception '가이드 수정이 기존 세션 snapshot을 변경했습니다.';
    end if;

    begin
        update bodeul.companion_sessions
        set guide_revision = 2
        where id = v_session_id;
        raise exception '세션 snapshot 직접 수정이 허용되었습니다.';
    exception
        when check_violation then
            null;
    end;

    begin
        insert into bodeul.companion_sessions (
            appointment_request_id,
            manager_user_id,
            current_status,
            guide_steps_snapshot,
            guide_snapshot_source
        ) values (
            '00000000-0000-0000-0000-000000000212',
            '00000000-0000-0000-0000-000000000202',
            'READY',
            null,
            'GUIDE_NOT_FOUND'
        );
        raise exception 'GUIDE_NOT_FOUND source에 NULL snapshot이 허용되었습니다.';
    exception
        when check_violation then
            null;
    end;

    v_missing_session_id := bodeul.assign_companion_session(
        '00000000-0000-0000-0000-000000000212',
        '00000000-0000-0000-0000-000000000202',
        '00000000-0000-0000-0000-000000000203',
        0,
        '가이드 누락 검증'
    );

    if not exists (
        select 1
        from bodeul.companion_sessions
        where id = v_missing_session_id
          and guide_snapshot_source = 'GUIDE_NOT_FOUND'
          and guide_id is null
          and guide_revision is null
          and guide_step_contract_version is null
          and guide_steps_snapshot = '[]'::jsonb
    ) then
        raise exception '가이드가 없는 신규 세션의 차단 상태가 명시되지 않았습니다.';
    end if;
end;
$$;

rollback;
