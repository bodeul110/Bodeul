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

update bodeul.companion_sessions as session
set care_ended_at = coalesce(
        session.completed_at,
        session.updated_at,
        session.started_at,
        now()),
    completed_at = coalesce(
        session.completed_at,
        session.updated_at,
        session.started_at,
        now()),
    report_generation_status = case
        when exists (
            select 1
            from bodeul.session_reports as report
            where report.companion_session_id = session.id
        ) then 'READY'
        else 'FAILED'
    end,
    report_generation_attempts = case
        when exists (
            select 1
            from bodeul.session_reports as report
            where report.companion_session_id = session.id
        ) then 1
        else 0
    end,
    report_generation_last_error = case
        when exists (
            select 1
            from bodeul.session_reports as report
            where report.companion_session_id = session.id
        ) then ''
        else 'LEGACY_REPORT_MISSING'
    end,
    report_generation_updated_at = coalesce(session.completed_at, session.updated_at, now())
where session.current_status = 'COMPLETED';

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
