create function bodeul.is_valid_guide_steps_v1(
    p_steps jsonb
) returns boolean
language sql
immutable
strict
parallel safe
security invoker
set search_path = pg_catalog, pg_temp
as $$
    select case
        when jsonb_typeof(p_steps) <> 'array' then false
        else
            not exists (
                select 1
                from jsonb_array_elements(p_steps) with ordinality as entry(step, position)
                where jsonb_typeof(entry.step) <> 'object'
                   or jsonb_typeof(entry.step -> 'code') is distinct from 'string'
                   or coalesce(entry.step ->> 'code', '') !~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'
                   or case
                        when jsonb_typeof(entry.step -> 'order') = 'number'
                             and coalesce(entry.step ->> 'order', '') ~ '^[1-9][0-9]*$'
                            then (entry.step ->> 'order')::numeric <> entry.position
                        else true
                      end
                   or jsonb_typeof(entry.step -> 'title') is distinct from 'string'
                   or btrim(coalesce(entry.step ->> 'title', '')) = ''
                   or jsonb_typeof(entry.step -> 'description') is distinct from 'string'
            )
            and (
                select count(*) = count(distinct entry.step ->> 'code')
                from jsonb_array_elements(p_steps) as entry(step)
            )
    end;
$$;

alter function bodeul.is_valid_guide_steps_v1(jsonb) owner to bodeul_migration;
revoke all on function bodeul.is_valid_guide_steps_v1(jsonb)
    from public, anon, authenticated, service_role;
grant execute on function bodeul.is_valid_guide_steps_v1(jsonb)
    to bodeul_core_runtime, bodeul_admin_runtime;

comment on function bodeul.is_valid_guide_steps_v1(jsonb) is
    '단계 code 중복과 배열 위치에 맞는 1부터 시작하는 order를 포함한 가이드 JSON 계약 검증';

alter table bodeul.hospital_guides
    add column revision bigint not null default 1,
    add column step_contract_version smallint not null default 0;

alter table bodeul.hospital_guides
    add constraint ck_hospital_guides_revision
        check (revision > 0),
    add constraint ck_hospital_guides_step_contract_version
        check (step_contract_version in (0, 1)),
    add constraint ck_hospital_guides_step_contract
        check (
            (step_contract_version = 0
                and jsonb_typeof(steps) = 'array')
            or
            (step_contract_version = 1
                and bodeul.is_valid_guide_steps_v1(steps))
        );

comment on column bodeul.hospital_guides.revision is
    '단계 계약이 변경될 때 DB가 단조 증가시키는 가이드 개정 번호';
comment on column bodeul.hospital_guides.step_contract_version is
    '0은 기존 무코드 배열, 1은 STEP_CODE_V1 가이드 계약';

create function bodeul.bump_hospital_guide_revision()
returns trigger
language plpgsql
security invoker
set search_path = pg_catalog, pg_temp
as $$
begin
    if old.step_contract_version = 1
            and new.step_contract_version <> 1 then
        raise exception '코드 기반 가이드를 legacy 계약으로 되돌릴 수 없습니다.'
            using errcode = '23514';
    end if;

    if new.hospital_name is distinct from old.hospital_name
            or new.department_name is distinct from old.department_name
            or new.steps is distinct from old.steps
            or new.step_contract_version is distinct from old.step_contract_version then
        new.revision := old.revision + 1;
        new.updated_at := now();
    else
        new.revision := old.revision;
    end if;

    return new;
end;
$$;

alter function bodeul.bump_hospital_guide_revision() owner to bodeul_migration;
revoke all on function bodeul.bump_hospital_guide_revision()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger bump_hospital_guide_revision_before_update
before update on bodeul.hospital_guides
for each row execute function bodeul.bump_hospital_guide_revision();

alter table bodeul.companion_sessions
    add column guide_id uuid,
    add column guide_revision bigint,
    add column guide_step_contract_version smallint,
    add column guide_steps_snapshot jsonb,
    add column guide_snapshot_source text not null default 'UNRESOLVED_LEGACY';

alter table bodeul.companion_sessions
    add constraint fk_companion_sessions_guide
        foreign key (guide_id) references bodeul.hospital_guides (id),
    add constraint ck_companion_sessions_guide_snapshot
        check ((
            (
                guide_snapshot_source = 'HOSPITAL_GUIDE_STEP_CODE_V1'
                and guide_id is not null
                and guide_revision is not null
                and guide_revision > 0
                and guide_step_contract_version is not null
                and guide_step_contract_version = 1
                and guide_steps_snapshot is not null
                and bodeul.is_valid_guide_steps_v1(guide_steps_snapshot)
            )
            or (
                guide_snapshot_source = 'LEGACY_HOSPITAL_GUIDE_V0'
                and guide_id is not null
                and guide_revision is not null
                and guide_revision > 0
                and guide_step_contract_version is not null
                and guide_step_contract_version = 0
                and guide_steps_snapshot is not null
                and jsonb_typeof(guide_steps_snapshot) = 'array'
            )
            or (
                guide_snapshot_source = 'LEGACY_CORE_7_V1'
                and guide_id is null
                and guide_revision is null
                and guide_step_contract_version is null
                and guide_steps_snapshot is not null
                and case
                    when bodeul.is_valid_guide_steps_v1(guide_steps_snapshot)
                        then jsonb_array_length(guide_steps_snapshot) = 7
                    else false
                end
            )
            or (
                guide_snapshot_source = 'GUIDE_NOT_FOUND'
                and guide_id is null
                and guide_revision is null
                and guide_step_contract_version is null
                and guide_steps_snapshot is not null
                and guide_steps_snapshot = '[]'::jsonb
            )
            or (
                guide_snapshot_source = 'UNRESOLVED_LEGACY'
                and guide_id is null
                and guide_revision is null
                and guide_step_contract_version is null
                and guide_steps_snapshot is null
            )
        ) is true);

create index ix_companion_sessions_guide_revision
    on bodeul.companion_sessions (guide_id, guide_revision)
    where guide_id is not null;

comment on column bodeul.companion_sessions.guide_id is
    '세션 생성 시 선택한 병원 가이드 ID. legacy 또는 누락 상태에서는 비어 있다';
comment on column bodeul.companion_sessions.guide_revision is
    '세션 생성 시 고정한 가이드 revision';
comment on column bodeul.companion_sessions.guide_step_contract_version is
    '세션 snapshot이 따르는 병원 가이드 단계 계약 버전';
comment on column bodeul.companion_sessions.guide_steps_snapshot is
    '가이드 수정과 무관하게 세션 종료까지 유지하는 단계 배열 snapshot';
comment on column bodeul.companion_sessions.guide_snapshot_source is
    '코드 가이드, legacy 보존 또는 원본 미확정 상태를 구분하는 snapshot 출처';

update bodeul.companion_sessions
set guide_snapshot_source = 'LEGACY_CORE_7_V1',
    guide_steps_snapshot = jsonb_build_array(
        jsonb_build_object(
            'code', 'LEGACY_CORE_PATIENT_CONTACT',
            'order', 1,
            'title', '환자 접촉',
            'description', '환자 도착 여부와 기본 컨디션을 확인하고 보호자에게 시작 상황을 공유합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_RECEPTION_PREPARATION',
            'order', 2,
            'title', '접수 준비',
            'description', '예약 정보, 신분증, 필요 서류를 확인하고 접수 창구 위치를 확인합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_RECEPTION',
            'order', 3,
            'title', '진료 접수',
            'description', '진료과 접수와 대기 순서를 확인하고 예상 대기 시간을 보호자에게 전달합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_CONSULTATION',
            'order', 4,
            'title', '진료 동행',
            'description', '진료 중 필요한 설명과 요청 사항을 메모하고 의료진 안내를 정리합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_PAYMENT',
            'order', 5,
            'title', '수납 처리',
            'description', '수납, 검사 예약, 다음 방문 일정 여부를 확인합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_PHARMACY',
            'order', 6,
            'title', '약국 방문',
            'description', '처방전 수령과 약국 방문 여부를 확인하고 복약 안내를 정리합니다.'
        ),
        jsonb_build_object(
            'code', 'LEGACY_CORE_RETURN_AND_CLOSE',
            'order', 7,
            'title', '귀가 및 종료',
            'description', '귀가 동선과 최종 특이사항을 확인하고 보호자에게 종료 상황을 전달합니다.'
        )
    )
where firestore_id is null
  and current_step_order between 0 and 7;

create function bodeul.prevent_companion_guide_snapshot_change()
returns trigger
language plpgsql
security invoker
set search_path = pg_catalog, pg_temp
as $$
begin
    if new.guide_id is distinct from old.guide_id
            or new.guide_revision is distinct from old.guide_revision
            or new.guide_step_contract_version is distinct from old.guide_step_contract_version
            or new.guide_steps_snapshot is distinct from old.guide_steps_snapshot
            or new.guide_snapshot_source is distinct from old.guide_snapshot_source then
        raise exception '동행 세션의 가이드 snapshot은 생성 후 변경할 수 없습니다.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

alter function bodeul.prevent_companion_guide_snapshot_change() owner to bodeul_migration;
revoke all on function bodeul.prevent_companion_guide_snapshot_change()
    from public, anon, authenticated, service_role,
         bodeul_core_runtime, bodeul_admin_runtime;

create trigger prevent_companion_guide_snapshot_change_before_update
before update on bodeul.companion_sessions
for each row execute function bodeul.prevent_companion_guide_snapshot_change();

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
    v_hospital_name text;
    v_department_name text;
    v_guide_id uuid;
    v_guide_revision bigint;
    v_guide_step_contract_version smallint;
    v_guide_steps jsonb;
    v_snapshot_source text;
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

    select
        status,
        version,
        manager_user_id,
        hospital_name,
        department_name
    into
        v_appointment_status,
        v_appointment_version,
        v_previous_manager_user_id,
        v_hospital_name,
        v_department_name
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

    select
        guide.id,
        guide.revision,
        guide.steps,
        guide.step_contract_version
    into
        v_guide_id,
        v_guide_revision,
        v_guide_steps,
        v_guide_step_contract_version
    from bodeul.hospital_guides guide
    where guide.hospital_name = v_hospital_name
      and guide.department_name = v_department_name;

    if found then
        v_snapshot_source := case v_guide_step_contract_version
            when 1 then 'HOSPITAL_GUIDE_STEP_CODE_V1'
            else 'LEGACY_HOSPITAL_GUIDE_V0'
        end;
    else
        v_guide_id := null;
        v_guide_revision := null;
        v_guide_step_contract_version := null;
        v_guide_steps := '[]'::jsonb;
        v_snapshot_source := 'GUIDE_NOT_FOUND';
    end if;

    insert into bodeul.companion_sessions (
        appointment_request_id,
        manager_user_id,
        current_status,
        guide_id,
        guide_revision,
        guide_step_contract_version,
        guide_steps_snapshot,
        guide_snapshot_source,
        created_at,
        updated_at
    ) values (
        p_appointment_request_id,
        p_manager_user_id,
        'READY',
        v_guide_id,
        v_guide_revision,
        v_guide_step_contract_version,
        v_guide_steps,
        v_snapshot_source,
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
