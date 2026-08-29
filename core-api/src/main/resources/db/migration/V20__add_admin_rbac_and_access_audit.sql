create table bodeul.admin_role_assignments (
    admin_user_id uuid primary key,
    admin_role text not null,
    granted_by_admin_user_id uuid,
    grant_reason text not null,
    granted_at timestamptz not null default now(),
    revoked_at timestamptz,
    revoked_by_admin_user_id uuid,
    updated_at timestamptz not null default now(),
    constraint fk_admin_role_assignments_user
        foreign key (admin_user_id) references bodeul.app_users (id),
    constraint fk_admin_role_assignments_granter
        foreign key (granted_by_admin_user_id) references bodeul.app_users (id),
    constraint fk_admin_role_assignments_revoker
        foreign key (revoked_by_admin_user_id) references bodeul.app_users (id),
    constraint ck_admin_role_assignments_role
        check (admin_role in ('SUPER_ADMIN', 'OPERATIONS', 'DEVELOPER')),
    constraint ck_admin_role_assignments_reason
        check (char_length(btrim(grant_reason)) between 1 and 500),
    constraint ck_admin_role_assignments_revocation
        check (
            (revoked_at is null and revoked_by_admin_user_id is null)
            or (revoked_at is not null and revoked_by_admin_user_id is not null)
        )
);

comment on table bodeul.admin_role_assignments is 'ADMIN 진입 자격 위에 적용하는 관리자 업무별 최소권한';
comment on column bodeul.admin_role_assignments.admin_role is 'SUPER_ADMIN, OPERATIONS, DEVELOPER 중 하나';

create table bodeul.admin_break_glass_grants (
    id uuid primary key default gen_random_uuid(),
    admin_user_id uuid not null,
    approved_by_admin_user_id uuid not null,
    reason text not null,
    granted_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revoked_by_admin_user_id uuid,
    constraint fk_admin_break_glass_subject
        foreign key (admin_user_id) references bodeul.app_users (id),
    constraint fk_admin_break_glass_approver
        foreign key (approved_by_admin_user_id) references bodeul.app_users (id),
    constraint fk_admin_break_glass_revoker
        foreign key (revoked_by_admin_user_id) references bodeul.app_users (id),
    constraint ck_admin_break_glass_two_person
        check (admin_user_id <> approved_by_admin_user_id),
    constraint ck_admin_break_glass_reason
        check (char_length(btrim(reason)) between 10 and 500),
    constraint ck_admin_break_glass_expiry
        check (expires_at > granted_at and expires_at <= granted_at + interval '60 minutes'),
    constraint ck_admin_break_glass_revocation
        check (
            (revoked_at is null and revoked_by_admin_user_id is null)
            or (revoked_at is not null and revoked_by_admin_user_id is not null)
        )
);

comment on table bodeul.admin_break_glass_grants is '민감정보 긴급 접근을 위한 2인 승인·자동 만료 권한';

create index ix_admin_break_glass_active
    on bodeul.admin_break_glass_grants (admin_user_id, expires_at desc)
    where revoked_at is null;

create unique index ux_admin_break_glass_unrevoked
    on bodeul.admin_break_glass_grants (admin_user_id)
    where revoked_at is null;

create table bodeul.admin_access_audits (
    id uuid primary key default gen_random_uuid(),
    actor_admin_user_id uuid not null,
    actor_admin_role text not null,
    action text not null,
    resource_type text not null,
    resource_id text not null,
    reason text not null default '',
    outcome text not null,
    break_glass_grant_id uuid,
    operation_id uuid,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint fk_admin_access_audits_actor
        foreign key (actor_admin_user_id) references bodeul.app_users (id),
    constraint fk_admin_access_audits_break_glass
        foreign key (break_glass_grant_id) references bodeul.admin_break_glass_grants (id),
    constraint ck_admin_access_audits_role
        check (actor_admin_role in ('SUPER_ADMIN', 'OPERATIONS', 'DEVELOPER')),
    constraint ck_admin_access_audits_action
        check (action in (
            'VIEW', 'RAW_VIEW', 'DOWNLOAD', 'UPDATE', 'DELETE',
            'ROLE_CHANGE', 'BREAK_GLASS_GRANT', 'BREAK_GLASS_REVOKE'
        )),
    constraint ck_admin_access_audits_resource_type
        check (char_length(btrim(resource_type)) between 1 and 100),
    constraint ck_admin_access_audits_resource_id
        check (char_length(btrim(resource_id)) between 1 and 200),
    constraint ck_admin_access_audits_outcome
        check (outcome in ('ALLOWED', 'DENIED', 'FAILED')),
    constraint ck_admin_access_audits_metadata
        check (jsonb_typeof(metadata) = 'object'),
    constraint ck_admin_access_audits_sensitive_reason
        check (
            action not in ('RAW_VIEW', 'DOWNLOAD', 'ROLE_CHANGE', 'BREAK_GLASS_GRANT', 'BREAK_GLASS_REVOKE')
            or char_length(btrim(reason)) between 10 and 500
        )
);

comment on table bodeul.admin_access_audits is '관리자 조회·원문 접근·변경·삭제·권한 변경의 추가 전용 감사 기록';

create index ix_admin_access_audits_actor_created
    on bodeul.admin_access_audits (actor_admin_user_id, created_at desc);
create index ix_admin_access_audits_resource_created
    on bodeul.admin_access_audits (resource_type, resource_id, created_at desc);
create index ix_admin_access_audits_created
    on bodeul.admin_access_audits (created_at, id);
create unique index ux_admin_access_audits_operation
    on bodeul.admin_access_audits (operation_id)
    where operation_id is not null;

revoke all on table bodeul.admin_role_assignments from public, anon, authenticated, service_role,
    bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.admin_break_glass_grants from public, anon, authenticated, service_role,
    bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.admin_access_audits from public, anon, authenticated, service_role,
    bodeul_core_runtime, bodeul_admin_runtime;

alter table bodeul.admin_role_assignments enable row level security;
alter table bodeul.admin_break_glass_grants enable row level security;
alter table bodeul.admin_access_audits enable row level security;

create function bodeul.resolve_admin_authorization(p_firebase_uid text)
returns table (
    app_user_id uuid,
    app_role text,
    admin_role text,
    break_glass_expires_at timestamptz
)
language sql
stable
security definer
set search_path = bodeul, pg_temp
as $$
    select
        app_user.id,
        app_user.role,
        assignment.admin_role,
        active_grant.expires_at
    from bodeul.app_users app_user
    left join bodeul.admin_role_assignments assignment
        on assignment.admin_user_id = app_user.id
        and assignment.revoked_at is null
        and app_user.role = 'ADMIN'
    left join lateral (
        select grant_record.expires_at
        from bodeul.admin_break_glass_grants grant_record
        where grant_record.admin_user_id = app_user.id
          and grant_record.revoked_at is null
          and grant_record.expires_at > now()
        order by grant_record.expires_at desc
        limit 1
    ) active_grant on true
    where app_user.firebase_uid = p_firebase_uid
    limit 1;
$$;

alter function bodeul.resolve_admin_authorization(text) owner to bodeul_migration;
revoke all on function bodeul.resolve_admin_authorization(text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.resolve_admin_authorization(text)
    to bodeul_admin_runtime;

create function bodeul.record_admin_access_audit(
    p_actor_admin_user_id uuid,
    p_action text,
    p_resource_type text,
    p_resource_id text,
    p_reason text,
    p_outcome text,
    p_metadata jsonb default '{}'::jsonb,
    p_operation_id uuid default null
) returns uuid
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_admin_role text;
    v_outbox_admin_role text;
    v_break_glass_grant_id uuid;
    v_audit_id uuid;
    v_existing bodeul.admin_access_audits%rowtype;
begin
    if p_operation_id is not null then
        select audit.* into v_existing
        from bodeul.admin_access_audits audit
        where audit.operation_id = p_operation_id;

        if found then
            if v_existing.actor_admin_user_id is distinct from p_actor_admin_user_id
                    or v_existing.action is distinct from p_action
                    or v_existing.resource_type is distinct from p_resource_type
                    or v_existing.resource_id is distinct from p_resource_id
                    or v_existing.reason is distinct from coalesce(p_reason, '')
                    or v_existing.outcome is distinct from p_outcome
                    or v_existing.metadata is distinct from coalesce(p_metadata, '{}'::jsonb) then
                raise exception '같은 감사 작업 ID를 다른 내용으로 재사용할 수 없습니다.'
                    using errcode = '22023';
            end if;
            return v_existing.id;
        end if;
    end if;

    v_outbox_admin_role := nullif(p_metadata ->> 'actorAdminRole', '');
    if p_operation_id is not null
            and p_action = 'UPDATE'
            and p_resource_type = 'MANAGER_REVIEW'
            and p_outcome = 'ALLOWED'
            and v_outbox_admin_role is not null then
        if v_outbox_admin_role not in ('SUPER_ADMIN', 'OPERATIONS') then
            raise exception '심사 감사의 관리자 역할이 올바르지 않습니다.' using errcode = '22023';
        end if;
        if coalesce(p_metadata ->> 'payloadHash', '') !~ '^[0-9a-f]{64}$' then
            raise exception '심사 감사의 payload hash가 올바르지 않습니다.' using errcode = '22023';
        end if;

        -- Firestore transaction에서 만든 immutable operation outbox의 당시 역할 snapshot이다.
        -- 이 함수는 bodeul_admin_runtime만 호출하며, 예외 범위를 심사 성공 감사로 제한한다.
        v_admin_role := v_outbox_admin_role;
    else
        select assignment.admin_role
        into v_admin_role
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN';
    end if;

    if v_admin_role is null then
        raise exception '활성 관리자 세부 역할이 필요합니다.' using errcode = '42501';
    end if;

    if p_outcome = 'ALLOWED'
            and p_action = 'RAW_VIEW'
            and v_admin_role not in ('SUPER_ADMIN', 'OPERATIONS') then
        raise exception '민감정보 원문 조회 권한이 없습니다.' using errcode = '42501';
    end if;

    if p_outcome = 'ALLOWED' and p_action = 'DOWNLOAD' then
        if v_admin_role <> 'SUPER_ADMIN' then
            raise exception '민감정보 다운로드는 최고 관리자만 요청할 수 있습니다.' using errcode = '42501';
        end if;

        select grant_record.id
        into v_break_glass_grant_id
        from bodeul.admin_break_glass_grants grant_record
        where grant_record.admin_user_id = p_actor_admin_user_id
          and grant_record.revoked_at is null
          and grant_record.expires_at > now()
        order by grant_record.expires_at desc
        limit 1;

        if v_break_glass_grant_id is null then
            raise exception '유효한 긴급 접근 승인이 필요합니다.' using errcode = '42501';
        end if;
    end if;

    insert into bodeul.admin_access_audits (
        actor_admin_user_id,
        actor_admin_role,
        action,
        resource_type,
        resource_id,
        reason,
        outcome,
        break_glass_grant_id,
        operation_id,
        metadata
    ) values (
        p_actor_admin_user_id,
        v_admin_role,
        p_action,
        p_resource_type,
        p_resource_id,
        coalesce(p_reason, ''),
        p_outcome,
        v_break_glass_grant_id,
        p_operation_id,
        coalesce(p_metadata, '{}'::jsonb)
    ) on conflict (operation_id) where operation_id is not null do nothing
    returning id into v_audit_id;

    if v_audit_id is null then
        select audit.* into strict v_existing
        from bodeul.admin_access_audits audit
        where audit.operation_id = p_operation_id;

        if v_existing.actor_admin_user_id is distinct from p_actor_admin_user_id
                or v_existing.action is distinct from p_action
                or v_existing.resource_type is distinct from p_resource_type
                or v_existing.resource_id is distinct from p_resource_id
                or v_existing.reason is distinct from coalesce(p_reason, '')
                or v_existing.outcome is distinct from p_outcome
                or v_existing.metadata is distinct from coalesce(p_metadata, '{}'::jsonb) then
            raise exception '같은 감사 작업 ID를 다른 내용으로 재사용할 수 없습니다.'
                using errcode = '22023';
        end if;
        v_audit_id := v_existing.id;
    end if;

    return v_audit_id;
end;
$$;

alter function bodeul.record_admin_access_audit(uuid, text, text, text, text, text, jsonb, uuid)
    owner to bodeul_migration;
revoke all on function bodeul.record_admin_access_audit(uuid, text, text, text, text, text, jsonb, uuid)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.record_admin_access_audit(uuid, text, text, text, text, text, jsonb, uuid)
    to bodeul_admin_runtime;

create function bodeul.preview_expired_admin_access_audits(p_as_of timestamptz)
returns bigint
language sql
stable
security definer
set search_path = bodeul, pg_temp
as $$
    select count(*)
    from bodeul.admin_access_audits audit
    where audit.created_at < p_as_of - interval '1 year';
$$;

alter function bodeul.preview_expired_admin_access_audits(timestamptz) owner to bodeul_migration;
revoke all on function bodeul.preview_expired_admin_access_audits(timestamptz)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.preview_expired_admin_access_audits(timestamptz)
    to bodeul_retention_runtime;

create function bodeul.purge_expired_admin_access_audits(
    p_as_of timestamptz,
    p_limit integer
) returns integer
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_deleted_count integer;
begin
    if p_limit not between 1 and 500 then
        raise exception '관리자 감사 파기 단위는 1부터 500까지 허용합니다.' using errcode = '22023';
    end if;
    if p_as_of is null
            or p_as_of < now() - interval '1 day'
            or p_as_of > now() + interval '5 minutes' then
        raise exception '관리자 감사 파기 기준 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;

    with candidates as (
        select audit.id
        from bodeul.admin_access_audits audit
        where audit.created_at < p_as_of - interval '1 year'
        order by audit.created_at, audit.id
        limit p_limit
        for update skip locked
    ), deleted as (
        delete from bodeul.admin_access_audits audit
        using candidates
        where audit.id = candidates.id
        returning audit.id
    )
    select count(*) into v_deleted_count from deleted;

    return v_deleted_count;
end;
$$;

alter function bodeul.purge_expired_admin_access_audits(timestamptz, integer)
    owner to bodeul_migration;
revoke all on function bodeul.purge_expired_admin_access_audits(timestamptz, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.purge_expired_admin_access_audits(timestamptz, integer)
    to bodeul_retention_runtime;

alter table bodeul.retention_job_runs
    add column admin_audit_candidates integer not null default 0,
    add column admin_audits_deleted integer not null default 0,
    add constraint ck_retention_job_runs_admin_audit_counts
        check (admin_audit_candidates >= 0 and admin_audits_deleted >= 0);

create or replace function bodeul.finish_retention_job(
    p_job_id uuid,
    p_status text,
    p_finished_at timestamptz,
    p_counts jsonb,
    p_failure_stage text
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_updated_count integer;
begin
    if p_status not in ('COMPLETED', 'FAILED') then
        raise exception '지원하지 않는 파기 완료 상태입니다.' using errcode = '22023';
    end if;
    if p_failure_stage is not null and char_length(p_failure_stage) > 64 then
        raise exception '실패 단계 식별자는 64자 이하여야 합니다.' using errcode = '22023';
    end if;
    if jsonb_typeof(p_counts) <> 'object'
            or not p_counts ?& array[
                'postgresMessageCandidates',
                'postgresAttachmentCandidates',
                'postgresLocationCandidates',
                'postgresLegalHoldSkips',
                'firestoreMessageCandidates',
                'firestoreAttachmentCandidates',
                'firestoreLocationCandidates',
                'firestoreLegalHoldSkips',
                'managerDocumentCandidates',
                'managerDocumentLegalHoldSkips',
                'messagesRedacted',
                'attachmentsDeleted',
                'attachmentDeleteFailures',
                'locationsDeleted',
                'firestoreMessagesRedacted',
                'firestoreAttachmentsDeleted',
                'firestoreAttachmentDeleteFailures',
                'firestoreLocationsCleared',
                'managerDocumentsDeleted',
                'managerDocumentDeleteFailures'
            ]
            or p_counts - array[
                'postgresMessageCandidates',
                'postgresAttachmentCandidates',
                'postgresLocationCandidates',
                'postgresLegalHoldSkips',
                'firestoreMessageCandidates',
                'firestoreAttachmentCandidates',
                'firestoreLocationCandidates',
                'firestoreLegalHoldSkips',
                'managerDocumentCandidates',
                'managerDocumentLegalHoldSkips',
                'adminAuditCandidates',
                'messagesRedacted',
                'attachmentsDeleted',
                'attachmentDeleteFailures',
                'locationsDeleted',
                'firestoreMessagesRedacted',
                'firestoreAttachmentsDeleted',
                'firestoreAttachmentDeleteFailures',
                'firestoreLocationsCleared',
                'managerDocumentsDeleted',
                'managerDocumentDeleteFailures',
                'adminAuditsDeleted'
            ] <> '{}'::jsonb
            or exists (
                select 1
                from jsonb_each_text(p_counts) item
                where item.value !~ '^[0-9]{1,9}$'
            ) then
        raise exception '파기 집계 형식이 올바르지 않습니다.' using errcode = '22023';
    end if;

    update bodeul.retention_job_runs run
    set status = p_status,
        finished_at = p_finished_at,
        postgres_message_candidates = (p_counts ->> 'postgresMessageCandidates')::integer,
        postgres_attachment_candidates = (p_counts ->> 'postgresAttachmentCandidates')::integer,
        postgres_location_candidates = (p_counts ->> 'postgresLocationCandidates')::integer,
        postgres_legal_hold_skips = (p_counts ->> 'postgresLegalHoldSkips')::integer,
        firestore_message_candidates = (p_counts ->> 'firestoreMessageCandidates')::integer,
        firestore_attachment_candidates = (p_counts ->> 'firestoreAttachmentCandidates')::integer,
        firestore_location_candidates = (p_counts ->> 'firestoreLocationCandidates')::integer,
        firestore_legal_hold_skips = (p_counts ->> 'firestoreLegalHoldSkips')::integer,
        manager_document_candidates = (p_counts ->> 'managerDocumentCandidates')::integer,
        manager_document_legal_hold_skips = (p_counts ->> 'managerDocumentLegalHoldSkips')::integer,
        admin_audit_candidates = coalesce((p_counts ->> 'adminAuditCandidates')::integer, 0),
        messages_redacted = (p_counts ->> 'messagesRedacted')::integer,
        attachments_deleted = (p_counts ->> 'attachmentsDeleted')::integer,
        attachment_delete_failures = (p_counts ->> 'attachmentDeleteFailures')::integer,
        locations_deleted = (p_counts ->> 'locationsDeleted')::integer,
        firestore_messages_redacted = (p_counts ->> 'firestoreMessagesRedacted')::integer,
        firestore_attachments_deleted = (p_counts ->> 'firestoreAttachmentsDeleted')::integer,
        firestore_attachment_delete_failures = (p_counts ->> 'firestoreAttachmentDeleteFailures')::integer,
        firestore_locations_cleared = (p_counts ->> 'firestoreLocationsCleared')::integer,
        manager_documents_deleted = (p_counts ->> 'managerDocumentsDeleted')::integer,
        manager_document_delete_failures = (p_counts ->> 'managerDocumentDeleteFailures')::integer,
        admin_audits_deleted = coalesce((p_counts ->> 'adminAuditsDeleted')::integer, 0),
        failure_stage = p_failure_stage
    where run.id = p_job_id and run.status = 'RUNNING';

    get diagnostics v_updated_count = row_count;
    return v_updated_count = 1;
end;
$$;

drop function bodeul.retention_monthly_summary(date);

create function bodeul.retention_monthly_summary(
    p_month_start date
) returns table (
    run_count bigint,
    failed_run_count bigint,
    message_candidates bigint,
    attachment_candidates bigint,
    location_candidates bigint,
    manager_document_candidates bigint,
    messages_redacted bigint,
    attachments_deleted bigint,
    attachment_delete_failures bigint,
    locations_deleted bigint,
    manager_documents_deleted bigint,
    manager_document_delete_failures bigint,
    legal_hold_skips bigint,
    admin_audit_candidates bigint,
    admin_audits_deleted bigint
)
language sql
stable
security definer
set search_path = pg_catalog, pg_temp
as $$
    select
        count(*),
        count(*) filter (where run.status = 'FAILED'),
        coalesce(sum(run.postgres_message_candidates + run.firestore_message_candidates), 0),
        coalesce(sum(run.postgres_attachment_candidates + run.firestore_attachment_candidates), 0),
        coalesce(sum(run.postgres_location_candidates + run.firestore_location_candidates), 0),
        coalesce(sum(run.manager_document_candidates), 0),
        coalesce(sum(run.messages_redacted + run.firestore_messages_redacted), 0),
        coalesce(sum(run.attachments_deleted + run.firestore_attachments_deleted), 0),
        coalesce(sum(run.attachment_delete_failures + run.firestore_attachment_delete_failures), 0),
        coalesce(sum(run.locations_deleted + run.firestore_locations_cleared), 0),
        coalesce(sum(run.manager_documents_deleted), 0),
        coalesce(sum(run.manager_document_delete_failures), 0),
        coalesce(sum(
            run.postgres_legal_hold_skips
            + run.firestore_legal_hold_skips
            + run.manager_document_legal_hold_skips
        ), 0),
        coalesce(sum(run.admin_audit_candidates), 0),
        coalesce(sum(run.admin_audits_deleted), 0)
    from bodeul.retention_job_runs run
    where run.started_at >= date_trunc('month', p_month_start::timestamp)
      and run.started_at < date_trunc('month', p_month_start::timestamp) + interval '1 month';
$$;

alter function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    owner to bodeul_migration;
alter function bodeul.retention_monthly_summary(date) owner to bodeul_migration;
revoke all on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.retention_monthly_summary(date)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    to bodeul_retention_runtime;
grant execute on function bodeul.retention_monthly_summary(date)
    to bodeul_retention_runtime, bodeul_admin_runtime;

create function bodeul.set_admin_role_assignment(
    p_target_admin_user_id uuid,
    p_admin_role text,
    p_actor_admin_user_id uuid,
    p_reason text
) returns void
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_previous_role text;
begin
    perform pg_advisory_xact_lock(110349);

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    if not exists (
        select 1 from bodeul.app_users
        where id = p_target_admin_user_id and role = 'ADMIN'
    ) then
        raise exception '대상 계정은 ADMIN 진입 자격이 필요합니다.' using errcode = '23503';
    end if;

    if p_admin_role not in ('SUPER_ADMIN', 'OPERATIONS', 'DEVELOPER') then
        raise exception '지원하지 않는 관리자 역할입니다.' using errcode = '22023';
    end if;

    if char_length(btrim(coalesce(p_reason, ''))) not between 10 and 500 then
        raise exception '권한 변경 사유는 10자부터 500자까지 입력해야 합니다.' using errcode = '22023';
    end if;

    select assignment.admin_role into v_previous_role
    from bodeul.admin_role_assignments assignment
    where assignment.admin_user_id = p_target_admin_user_id
      and assignment.revoked_at is null;

    if v_previous_role = 'SUPER_ADMIN' and p_admin_role <> 'SUPER_ADMIN' and (
        select count(*)
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) <= 1 then
        raise exception '마지막 최고 관리자 권한은 변경할 수 없습니다.' using errcode = 'P0001';
    end if;

    insert into bodeul.admin_role_assignments (
        admin_user_id,
        admin_role,
        granted_by_admin_user_id,
        grant_reason,
        granted_at,
        revoked_at,
        revoked_by_admin_user_id,
        updated_at
    ) values (
        p_target_admin_user_id,
        p_admin_role,
        p_actor_admin_user_id,
        btrim(p_reason),
        now(),
        null,
        null,
        now()
    )
    on conflict (admin_user_id) do update
    set admin_role = excluded.admin_role,
        granted_by_admin_user_id = excluded.granted_by_admin_user_id,
        grant_reason = excluded.grant_reason,
        granted_at = excluded.granted_at,
        revoked_at = null,
        revoked_by_admin_user_id = null,
        updated_at = now();

    insert into bodeul.admin_access_audits (
        actor_admin_user_id, actor_admin_role, action, resource_type,
        resource_id, reason, outcome, metadata
    ) values (
        p_actor_admin_user_id, 'SUPER_ADMIN', 'ROLE_CHANGE', 'ADMIN_ROLE_ASSIGNMENT',
        p_target_admin_user_id::text, btrim(p_reason), 'ALLOWED',
        jsonb_build_object('previousRole', v_previous_role, 'nextRole', p_admin_role)
    );
end;
$$;

alter function bodeul.set_admin_role_assignment(uuid, text, uuid, text) owner to bodeul_migration;
revoke all on function bodeul.set_admin_role_assignment(uuid, text, uuid, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.set_admin_role_assignment(uuid, text, uuid, text)
    to bodeul_admin_runtime;

create function bodeul.revoke_admin_role_assignment(
    p_target_admin_user_id uuid,
    p_actor_admin_user_id uuid,
    p_reason text
) returns void
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_target_role text;
begin
    perform pg_advisory_xact_lock(110349);

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    if char_length(btrim(coalesce(p_reason, ''))) not between 10 and 500 then
        raise exception '권한 회수 사유는 10자부터 500자까지 입력해야 합니다.' using errcode = '22023';
    end if;

    select assignment.admin_role into v_target_role
    from bodeul.admin_role_assignments assignment
    where assignment.admin_user_id = p_target_admin_user_id
      and assignment.revoked_at is null
    for update;

    if v_target_role is null then
        raise exception '활성 관리자 역할을 찾을 수 없습니다.' using errcode = 'P0002';
    end if;

    if v_target_role = 'SUPER_ADMIN' and (
        select count(*)
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) <= 1 then
        raise exception '마지막 최고 관리자 권한은 회수할 수 없습니다.' using errcode = 'P0001';
    end if;

    update bodeul.admin_role_assignments
    set revoked_at = now(),
        revoked_by_admin_user_id = p_actor_admin_user_id,
        updated_at = now()
    where admin_user_id = p_target_admin_user_id;

    update bodeul.admin_break_glass_grants
    set revoked_at = now(),
        revoked_by_admin_user_id = p_actor_admin_user_id
    where admin_user_id = p_target_admin_user_id
      and revoked_at is null
      and expires_at > now();

    insert into bodeul.admin_access_audits (
        actor_admin_user_id, actor_admin_role, action, resource_type,
        resource_id, reason, outcome, metadata
    ) values (
        p_actor_admin_user_id, 'SUPER_ADMIN', 'ROLE_CHANGE', 'ADMIN_ROLE_ASSIGNMENT',
        p_target_admin_user_id::text, btrim(p_reason), 'ALLOWED',
        jsonb_build_object('previousRole', v_target_role, 'nextRole', null)
    );
end;
$$;

alter function bodeul.revoke_admin_role_assignment(uuid, uuid, text) owner to bodeul_migration;
revoke all on function bodeul.revoke_admin_role_assignment(uuid, uuid, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.revoke_admin_role_assignment(uuid, uuid, text)
    to bodeul_admin_runtime;

create function bodeul.grant_admin_break_glass(
    p_target_admin_user_id uuid,
    p_actor_admin_user_id uuid,
    p_reason text,
    p_duration_minutes integer
) returns uuid
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_grant_id uuid;
    v_replaced_count integer;
begin
    perform pg_advisory_xact_lock(110349);

    if p_target_admin_user_id = p_actor_admin_user_id then
        raise exception '긴급 접근은 본인이 승인할 수 없습니다.' using errcode = '42501';
    end if;

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_target_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '대상의 활성 관리자 역할을 찾을 수 없습니다.' using errcode = 'P0002';
    end if;

    if char_length(btrim(coalesce(p_reason, ''))) not between 10 and 500
        or p_duration_minutes not between 1 and 60 then
        raise exception '사유와 1~60분 만료 시간을 확인해 주세요.' using errcode = '22023';
    end if;

    update bodeul.admin_break_glass_grants
    set revoked_at = now(),
        revoked_by_admin_user_id = p_actor_admin_user_id
    where admin_user_id = p_target_admin_user_id
      and revoked_at is null;
    get diagnostics v_replaced_count = row_count;

    insert into bodeul.admin_break_glass_grants (
        admin_user_id, approved_by_admin_user_id, reason, expires_at
    ) values (
        p_target_admin_user_id,
        p_actor_admin_user_id,
        btrim(p_reason),
        now() + make_interval(mins => p_duration_minutes)
    ) returning id into v_grant_id;

    insert into bodeul.admin_access_audits (
        actor_admin_user_id, actor_admin_role, action, resource_type,
        resource_id, reason, outcome, metadata
    ) values (
        p_actor_admin_user_id, 'SUPER_ADMIN', 'BREAK_GLASS_GRANT', 'ADMIN_BREAK_GLASS',
        v_grant_id::text, btrim(p_reason), 'ALLOWED',
        jsonb_build_object(
            'targetAdminUserId', p_target_admin_user_id,
            'durationMinutes', p_duration_minutes,
            'replacedGrantCount', v_replaced_count
        )
    );

    return v_grant_id;
end;
$$;

alter function bodeul.grant_admin_break_glass(uuid, uuid, text, integer) owner to bodeul_migration;
revoke all on function bodeul.grant_admin_break_glass(uuid, uuid, text, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.grant_admin_break_glass(uuid, uuid, text, integer)
    to bodeul_admin_runtime;

create function bodeul.revoke_admin_break_glass(
    p_grant_id uuid,
    p_actor_admin_user_id uuid,
    p_reason text
) returns boolean
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_updated_count integer;
    v_target_admin_user_id uuid;
begin
    perform pg_advisory_xact_lock(110349);

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    if char_length(btrim(coalesce(p_reason, ''))) not between 10 and 500 then
        raise exception '긴급 접근 회수 사유는 10자부터 500자까지 입력해야 합니다.' using errcode = '22023';
    end if;

    select grant_record.admin_user_id
    into v_target_admin_user_id
    from bodeul.admin_break_glass_grants grant_record
    where grant_record.id = p_grant_id
      and grant_record.revoked_at is null
      and grant_record.expires_at > now()
    for update;

    if v_target_admin_user_id is null then
        return false;
    end if;

    update bodeul.admin_break_glass_grants
    set revoked_at = now(),
        revoked_by_admin_user_id = p_actor_admin_user_id
    where id = p_grant_id
      and revoked_at is null;

    get diagnostics v_updated_count = row_count;
    if v_updated_count > 0 then
        insert into bodeul.admin_access_audits (
            actor_admin_user_id, actor_admin_role, action, resource_type,
            resource_id, reason, outcome, metadata
        ) values (
            p_actor_admin_user_id, 'SUPER_ADMIN', 'BREAK_GLASS_REVOKE', 'ADMIN_BREAK_GLASS',
            p_grant_id::text, btrim(p_reason), 'ALLOWED',
            jsonb_build_object(
                'targetAdminUserId', v_target_admin_user_id,
                'revokedGrantCount', v_updated_count
            )
        );
    end if;
    return v_updated_count > 0;
end;
$$;

alter function bodeul.revoke_admin_break_glass(uuid, uuid, text) owner to bodeul_migration;
revoke all on function bodeul.revoke_admin_break_glass(uuid, uuid, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.revoke_admin_break_glass(uuid, uuid, text)
    to bodeul_admin_runtime;

create function bodeul.list_admin_role_assignments(p_actor_admin_user_id uuid)
returns table (
    admin_user_id uuid,
    admin_role text,
    granted_at timestamptz,
    revoked_at timestamptz,
    break_glass_grant_id uuid,
    break_glass_expires_at timestamptz
)
language plpgsql
stable
security definer
set search_path = bodeul, pg_temp
as $$
begin
    if not exists (
        select 1
        from bodeul.admin_role_assignments actor_assignment
        join bodeul.app_users app_user on app_user.id = actor_assignment.admin_user_id
        where actor_assignment.admin_user_id = p_actor_admin_user_id
          and actor_assignment.admin_role = 'SUPER_ADMIN'
          and actor_assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    return query
    select
        assignment.admin_user_id,
        assignment.admin_role,
        assignment.granted_at,
        assignment.revoked_at,
        active_grant.id,
        active_grant.expires_at
    from bodeul.admin_role_assignments assignment
    left join lateral (
        select grant_record.id, grant_record.expires_at
        from bodeul.admin_break_glass_grants grant_record
        where grant_record.admin_user_id = assignment.admin_user_id
          and grant_record.revoked_at is null
          and grant_record.expires_at > now()
        order by grant_record.expires_at desc
        limit 1
    ) active_grant on true
    order by assignment.revoked_at nulls first, assignment.admin_role, assignment.admin_user_id;
end;
$$;

alter function bodeul.list_admin_role_assignments(uuid) owner to bodeul_migration;
revoke all on function bodeul.list_admin_role_assignments(uuid)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.list_admin_role_assignments(uuid)
    to bodeul_admin_runtime;

create function bodeul.list_admin_access_audits(
    p_actor_admin_user_id uuid,
    p_limit integer
) returns table (
    audit_id uuid,
    audited_actor_admin_user_id uuid,
    audited_actor_admin_role text,
    audited_action text,
    audited_resource_type text,
    audited_resource_id text,
    audited_reason text,
    audited_outcome text,
    audited_at timestamptz
)
language plpgsql
stable
security definer
set search_path = bodeul, pg_temp
as $$
begin
    if p_limit not between 1 and 200 then
        raise exception '감사 조회 건수는 1부터 200까지 허용합니다.' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = p_actor_admin_user_id
          and assignment.admin_role = 'SUPER_ADMIN'
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '최고 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    return query
    select
        audit.id,
        audit.actor_admin_user_id,
        audit.actor_admin_role,
        audit.action,
        audit.resource_type,
        audit.resource_id,
        audit.reason,
        audit.outcome,
        audit.created_at
    from bodeul.admin_access_audits audit
    order by audit.created_at desc, audit.id desc
    limit p_limit;
end;
$$;

alter function bodeul.list_admin_access_audits(uuid, integer) owner to bodeul_migration;
revoke all on function bodeul.list_admin_access_audits(uuid, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.list_admin_access_audits(uuid, integer)
    to bodeul_admin_runtime;

create function bodeul.enforce_assignment_actor_admin_role()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
begin
    if not exists (
        select 1
        from bodeul.admin_role_assignments assignment
        join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
        where assignment.admin_user_id = new.actor_admin_user_id
          and assignment.admin_role in ('SUPER_ADMIN', 'OPERATIONS')
          and assignment.revoked_at is null
          and app_user.role = 'ADMIN'
    ) then
        raise exception '배정 작업에는 운영 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;
    return new;
end;
$$;

alter function bodeul.enforce_assignment_actor_admin_role() owner to bodeul_migration;
revoke all on function bodeul.enforce_assignment_actor_admin_role()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger companion_assignment_admin_role_guard
before insert on bodeul.companion_session_assignment_audits
for each row execute function bodeul.enforce_assignment_actor_admin_role();
