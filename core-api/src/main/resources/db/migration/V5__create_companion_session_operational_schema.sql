create table bodeul.companion_sessions (
    id uuid primary key default gen_random_uuid(),
    firestore_id text,
    appointment_request_id uuid not null,
    manager_user_id uuid not null,
    current_step_order integer not null default 0,
    current_status text not null default 'READY',
    guardian_update text not null default '',
    location_summary text not null default '',
    field_photo_note text not null default '',
    medication_note text not null default '',
    pharmacy_summary text not null default '',
    prescription_collected boolean not null default false,
    pharmacy_completed boolean not null default false,
    medication_guidance_completed boolean not null default false,
    version bigint not null default 0,
    started_at timestamptz,
    completed_at timestamptz,
    canceled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    imported_at timestamptz,
    constraint uq_companion_sessions_firestore_id unique (firestore_id),
    constraint uq_companion_sessions_appointment_request unique (appointment_request_id),
    constraint fk_companion_sessions_appointment_request
        foreign key (appointment_request_id) references bodeul.appointment_requests (id),
    constraint fk_companion_sessions_manager_user
        foreign key (manager_user_id) references bodeul.app_users (id),
    constraint ck_companion_sessions_firestore_id
        check (firestore_id is null or btrim(firestore_id) <> ''),
    constraint ck_companion_sessions_step
        check (current_step_order >= 0),
    constraint ck_companion_sessions_status
        check (current_status in (
            'READY', 'MEETING', 'WAITING', 'IN_TREATMENT',
            'PAYMENT', 'CANCELED', 'COMPLETED'
        )),
    constraint ck_companion_sessions_version
        check (version >= 0)
);

comment on table bodeul.companion_sessions is '매칭된 예약의 담당 매니저와 동행 진행 상태를 관리하는 PostgreSQL 운영 원본';
comment on column bodeul.companion_sessions.firestore_id is '전환 전에 생성된 Firestore companionSessions 문서 ID';
comment on column bodeul.companion_sessions.imported_at is 'Firestore 백필 행이 PostgreSQL에 마지막으로 적재된 시각';
comment on column bodeul.companion_sessions.version is '동행 상태 수정 충돌을 검출하는 낙관적 잠금 버전';

create index ix_companion_sessions_manager_status
    on bodeul.companion_sessions (manager_user_id, current_status, updated_at desc);

create table bodeul.session_reports (
    id uuid primary key default gen_random_uuid(),
    firestore_id text,
    companion_session_id uuid not null,
    summary text not null default '',
    treatment_notes text not null default '',
    medication_notes text not null default '',
    medication_name text not null default '',
    medication_change_summary text not null default '',
    medication_schedule_note text not null default '',
    medication_comparison_decision_code text not null default '',
    medication_comparison_note text not null default '',
    next_visit_at timestamptz,
    next_visit_note text not null default '',
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    imported_at timestamptz,
    constraint uq_session_reports_firestore_id unique (firestore_id),
    constraint uq_session_reports_companion_session unique (companion_session_id),
    constraint fk_session_reports_companion_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id),
    constraint ck_session_reports_firestore_id
        check (firestore_id is null or btrim(firestore_id) <> ''),
    constraint ck_session_reports_medication_comparison
        check (medication_comparison_decision_code in ('', 'MATCHED', 'CHANGED', 'RECHECK_REQUIRED')),
    constraint ck_session_reports_version
        check (version >= 0)
);

comment on table bodeul.session_reports is '동행 종료 시 매니저가 작성하는 진료와 복약 결과의 PostgreSQL 운영 원본';
comment on column bodeul.session_reports.firestore_id is '전환 전에 생성된 Firestore sessionReports 문서 ID';
comment on column bodeul.session_reports.next_visit_note is '날짜로 정규화할 수 없는 경우를 포함한 다음 방문 일정 원문';

create table bodeul.appointment_follow_ups (
    appointment_request_id uuid primary key,
    review_rating_code text not null default '',
    review_comment text not null default '',
    review_saved_by_user_id uuid,
    review_saved_at timestamptz,
    settlement_follow_up_status text not null default '',
    settlement_follow_up_note text not null default '',
    settlement_follow_up_saved_by_user_id uuid,
    settlement_follow_up_saved_at timestamptz,
    support_escalation_status text not null default '',
    support_escalated_by_user_id uuid,
    support_escalated_at timestamptz,
    version bigint not null default 0,
    updated_at timestamptz not null default now(),
    imported_at timestamptz,
    constraint fk_appointment_follow_ups_request
        foreign key (appointment_request_id) references bodeul.appointment_requests (id),
    constraint fk_appointment_follow_ups_review_actor
        foreign key (review_saved_by_user_id) references bodeul.app_users (id),
    constraint fk_appointment_follow_ups_settlement_actor
        foreign key (settlement_follow_up_saved_by_user_id) references bodeul.app_users (id),
    constraint fk_appointment_follow_ups_support_actor
        foreign key (support_escalated_by_user_id) references bodeul.app_users (id),
    constraint ck_appointment_follow_ups_review
        check (review_rating_code in ('', 'excellent', 'good', 'ok', 'disappointing', 'need_help')),
    constraint ck_appointment_follow_ups_settlement
        check (settlement_follow_up_status in ('', 'CONFIRMED', 'NEEDS_HELP', 'OVERTIME_REVIEW', 'REFUND_REVIEW')),
    constraint ck_appointment_follow_ups_support
        check (support_escalation_status in ('', 'GUIDE_VIEWED', 'MANAGER_CALLED', 'DIALED_119')),
    constraint ck_appointment_follow_ups_version
        check (version >= 0)
);

comment on table bodeul.appointment_follow_ups is '예약 종료 후 후기, 정산 확인, 긴급 지원 후속 기록';

create table bodeul.companion_session_assignment_audits (
    id uuid primary key default gen_random_uuid(),
    appointment_request_id uuid not null,
    companion_session_id uuid not null,
    previous_manager_user_id uuid,
    assigned_manager_user_id uuid not null,
    actor_admin_user_id uuid not null,
    reason text not null default '',
    created_at timestamptz not null default now(),
    constraint fk_assignment_audits_request
        foreign key (appointment_request_id) references bodeul.appointment_requests (id),
    constraint fk_assignment_audits_session
        foreign key (companion_session_id) references bodeul.companion_sessions (id),
    constraint fk_assignment_audits_previous_manager
        foreign key (previous_manager_user_id) references bodeul.app_users (id),
    constraint fk_assignment_audits_assigned_manager
        foreign key (assigned_manager_user_id) references bodeul.app_users (id),
    constraint fk_assignment_audits_actor_admin
        foreign key (actor_admin_user_id) references bodeul.app_users (id)
);

comment on table bodeul.companion_session_assignment_audits is '관리자 서버의 매니저 배정 변경 이력';

create index ix_assignment_audits_request_created
    on bodeul.companion_session_assignment_audits (appointment_request_id, created_at desc);

revoke all on table bodeul.companion_sessions from public, anon, authenticated, service_role;
revoke all on table bodeul.session_reports from public, anon, authenticated, service_role;
revoke all on table bodeul.appointment_follow_ups from public, anon, authenticated, service_role;
revoke all on table bodeul.companion_session_assignment_audits from public, anon, authenticated, service_role;

grant select on table bodeul.companion_sessions to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.session_reports to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.appointment_follow_ups to bodeul_core_runtime, bodeul_admin_runtime;
grant select on table bodeul.companion_session_assignment_audits to bodeul_admin_runtime;

alter table bodeul.companion_sessions enable row level security;
alter table bodeul.session_reports enable row level security;
alter table bodeul.appointment_follow_ups enable row level security;
alter table bodeul.companion_session_assignment_audits enable row level security;

create policy companion_sessions_core_select
    on bodeul.companion_sessions for select to bodeul_core_runtime using (true);
create policy companion_sessions_admin_select
    on bodeul.companion_sessions for select to bodeul_admin_runtime using (true);
create policy session_reports_core_select
    on bodeul.session_reports for select to bodeul_core_runtime using (true);
create policy session_reports_admin_select
    on bodeul.session_reports for select to bodeul_admin_runtime using (true);
create policy appointment_follow_ups_core_select
    on bodeul.appointment_follow_ups for select to bodeul_core_runtime using (true);
create policy appointment_follow_ups_admin_select
    on bodeul.appointment_follow_ups for select to bodeul_admin_runtime using (true);
create policy assignment_audits_admin_select
    on bodeul.companion_session_assignment_audits for select to bodeul_admin_runtime using (true);

create function bodeul.assign_companion_session(
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
