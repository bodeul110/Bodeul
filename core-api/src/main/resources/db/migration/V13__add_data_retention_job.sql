create table bodeul.retention_job_runs (
    id uuid primary key default gen_random_uuid(),
    mode text not null,
    status text not null default 'RUNNING',
    as_of timestamptz not null,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    postgres_message_candidates integer not null default 0,
    postgres_attachment_candidates integer not null default 0,
    postgres_location_candidates integer not null default 0,
    postgres_legal_hold_skips integer not null default 0,
    firestore_message_candidates integer not null default 0,
    firestore_attachment_candidates integer not null default 0,
    firestore_location_candidates integer not null default 0,
    firestore_legal_hold_skips integer not null default 0,
    manager_document_candidates integer not null default 0,
    manager_document_legal_hold_skips integer not null default 0,
    messages_redacted integer not null default 0,
    attachments_deleted integer not null default 0,
    attachment_delete_failures integer not null default 0,
    locations_deleted integer not null default 0,
    firestore_messages_redacted integer not null default 0,
    firestore_attachments_deleted integer not null default 0,
    firestore_attachment_delete_failures integer not null default 0,
    firestore_locations_cleared integer not null default 0,
    manager_documents_deleted integer not null default 0,
    manager_document_delete_failures integer not null default 0,
    failure_stage text,
    constraint ck_retention_job_runs_mode
        check (mode in ('DRY_RUN', 'APPLY')),
    constraint ck_retention_job_runs_status
        check (status in ('RUNNING', 'COMPLETED', 'FAILED')),
    constraint ck_retention_job_runs_finished
        check (
            (status = 'RUNNING' and finished_at is null)
            or (status in ('COMPLETED', 'FAILED') and finished_at is not null)
        ),
    constraint ck_retention_job_runs_failure_stage
        check (failure_stage is null or char_length(failure_stage) <= 64),
    constraint ck_retention_job_runs_counts
        check (
            postgres_message_candidates >= 0
            and postgres_attachment_candidates >= 0
            and postgres_location_candidates >= 0
            and postgres_legal_hold_skips >= 0
            and firestore_message_candidates >= 0
            and firestore_attachment_candidates >= 0
            and firestore_location_candidates >= 0
            and firestore_legal_hold_skips >= 0
            and manager_document_candidates >= 0
            and manager_document_legal_hold_skips >= 0
            and messages_redacted >= 0
            and attachments_deleted >= 0
            and attachment_delete_failures >= 0
            and locations_deleted >= 0
            and firestore_messages_redacted >= 0
            and firestore_attachments_deleted >= 0
            and firestore_attachment_delete_failures >= 0
            and firestore_locations_cleared >= 0
            and manager_documents_deleted >= 0
            and manager_document_delete_failures >= 0
        )
);

comment on table bodeul.retention_job_runs is '개인정보 자동 파기 작업의 비식별 실행 집계';

create index ix_retention_job_runs_started
    on bodeul.retention_job_runs (started_at desc);

revoke all on table bodeul.retention_job_runs
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant select on table bodeul.retention_job_runs to bodeul_admin_runtime;

alter table bodeul.retention_job_runs enable row level security;

create policy retention_job_runs_admin_select
    on bodeul.retention_job_runs
    for select
    to bodeul_admin_runtime
    using (true);

create function bodeul.preview_expired_companion_data(
    p_as_of timestamptz
) returns table (
    message_candidates bigint,
    attachment_candidates bigint,
    location_candidates bigint,
    legal_hold_skips bigint
)
language sql
stable
security definer
set search_path = pg_catalog, pg_temp
as $$
    select
        (
            select count(*)
            from bodeul.companion_chat_messages message
            where message.deleted_at is null
              and message.expires_at <= p_as_of
              and (message.legal_hold_until is null or message.legal_hold_until <= p_as_of)
        ),
        (
            select count(*)
            from bodeul.companion_chat_attachments attachment
            where attachment.status in ('ACTIVE', 'DELETE_PENDING')
              and attachment.expires_at <= p_as_of
              and (attachment.legal_hold_until is null or attachment.legal_hold_until <= p_as_of)
        ),
        (
            select count(*)
            from bodeul.companion_session_locations location
            where location.expires_at <= p_as_of
              and (location.legal_hold_until is null or location.legal_hold_until <= p_as_of)
        ),
        (
            select count(*)
            from (
                select message.id
                from bodeul.companion_chat_messages message
                where message.deleted_at is null
                  and message.expires_at <= p_as_of
                  and message.legal_hold_until > p_as_of
                union all
                select attachment.id
                from bodeul.companion_chat_attachments attachment
                where attachment.status in ('ACTIVE', 'DELETE_PENDING')
                  and attachment.expires_at <= p_as_of
                  and attachment.legal_hold_until > p_as_of
                union all
                select location.id
                from bodeul.companion_session_locations location
                where location.expires_at <= p_as_of
                  and location.legal_hold_until > p_as_of
            ) held
        );
$$;

alter table bodeul.companion_chat_attachments
    drop constraint ck_chat_attachments_status;
alter table bodeul.companion_chat_attachments
    add constraint ck_chat_attachments_status
        check (status in ('ACTIVE', 'DELETE_PENDING', 'DELETED'));

drop index bodeul.ix_chat_attachments_expiry;
create index ix_chat_attachments_expiry
    on bodeul.companion_chat_attachments (expires_at)
    where expires_at is not null and status in ('ACTIVE', 'DELETE_PENDING');

create function bodeul.claim_expired_companion_attachments(
    p_as_of timestamptz,
    p_limit integer
) returns table (
    attachment_id uuid,
    storage_path text
)
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
begin
    if p_limit < 1 or p_limit > 500 then
        raise exception '파기 조회 건수는 1 이상 500 이하여야 합니다.' using errcode = '22023';
    end if;
    if p_as_of is null
            or p_as_of < now() - interval '1 day'
            or p_as_of > now() + interval '5 minutes' then
        raise exception '파기 기준 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;

    return query
    with candidates as (
        select attachment.id
        from bodeul.companion_chat_attachments attachment
        where attachment.status in ('ACTIVE', 'DELETE_PENDING')
          and attachment.expires_at <= p_as_of
          and (attachment.legal_hold_until is null or attachment.legal_hold_until <= p_as_of)
        order by
            case when attachment.status = 'DELETE_PENDING' then 0 else 1 end,
            attachment.expires_at,
            attachment.id
        limit p_limit
        for update skip locked
    )
    update bodeul.companion_chat_attachments attachment
    set status = 'DELETE_PENDING'
    from candidates candidate
    where attachment.id = candidate.id
    returning attachment.id, attachment.storage_path;
end;
$$;

create function bodeul.mark_companion_attachment_deleted(
    p_attachment_id uuid,
    p_expected_storage_path text,
    p_deleted_at timestamptz
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_updated_count integer;
begin
    if p_deleted_at is null
            or p_deleted_at < now() - interval '1 day'
            or p_deleted_at > now() + interval '5 minutes' then
        raise exception '파기 완료 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;

    update bodeul.companion_chat_attachments attachment
    set status = 'DELETED',
        deleted_at = p_deleted_at,
        storage_path = 'deleted/' || attachment.id::text,
        file_name = '',
        size_bytes = 0
    where attachment.id = p_attachment_id
      and attachment.storage_path = p_expected_storage_path
      and attachment.status = 'DELETE_PENDING'
      and attachment.expires_at <= p_deleted_at
      and (attachment.legal_hold_until is null or attachment.legal_hold_until <= p_deleted_at);

    get diagnostics v_updated_count = row_count;
    return v_updated_count = 1;
end;
$$;

create function bodeul.purge_expired_companion_records(
    p_as_of timestamptz,
    p_limit integer
) returns table (
    messages_redacted integer,
    locations_deleted integer
)
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
begin
    if p_limit < 1 or p_limit > 500 then
        raise exception '파기 처리 건수는 1 이상 500 이하여야 합니다.' using errcode = '22023';
    end if;
    if p_as_of is null
            or p_as_of < now() - interval '1 day'
            or p_as_of > now() + interval '5 minutes' then
        raise exception '파기 기준 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;

    return query
    with message_candidates as (
        select message.id
        from bodeul.companion_chat_messages message
        where message.deleted_at is null
          and message.expires_at <= p_as_of
          and (message.legal_hold_until is null or message.legal_hold_until <= p_as_of)
        order by message.expires_at, message.id
        limit p_limit
        for update skip locked
    ), redacted as (
        update bodeul.companion_chat_messages message
        set body = '', deleted_at = p_as_of
        from message_candidates candidate
        where message.id = candidate.id
        returning message.id
    ), location_candidates as (
        select location.id
        from bodeul.companion_session_locations location
        where location.expires_at <= p_as_of
          and (location.legal_hold_until is null or location.legal_hold_until <= p_as_of)
        order by location.expires_at, location.id
        limit p_limit
        for update skip locked
    ), removed as (
        delete from bodeul.companion_session_locations location
        using location_candidates candidate
        where location.id = candidate.id
        returning location.id
    )
    select
        (select count(*)::integer from redacted),
        (select count(*)::integer from removed);
end;
$$;

create function bodeul.begin_retention_job(
    p_mode text,
    p_as_of timestamptz,
    p_started_at timestamptz
) returns uuid
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_job_id uuid;
begin
    if p_mode not in ('DRY_RUN', 'APPLY') then
        raise exception '지원하지 않는 파기 실행 모드입니다.' using errcode = '22023';
    end if;
    if p_as_of is null
            or p_started_at is null
            or p_as_of < now() - interval '1 day'
            or p_as_of > now() + interval '5 minutes'
            or abs(extract(epoch from (p_started_at - p_as_of))) > 300 then
        raise exception '파기 실행 시각이 허용 범위를 벗어났습니다.' using errcode = '22023';
    end if;

    insert into bodeul.retention_job_runs (mode, as_of, started_at)
    values (p_mode, p_as_of, p_started_at)
    returning id into v_job_id;

    return v_job_id;
end;
$$;

create function bodeul.finish_retention_job(
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
        failure_stage = p_failure_stage
    where run.id = p_job_id and run.status = 'RUNNING';

    get diagnostics v_updated_count = row_count;
    return v_updated_count = 1;
end;
$$;

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
    legal_hold_skips bigint
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
        ), 0)
    from bodeul.retention_job_runs run
    where run.started_at >= date_trunc('month', p_month_start::timestamp)
      and run.started_at < date_trunc('month', p_month_start::timestamp) + interval '1 month';
$$;

alter function bodeul.preview_expired_companion_data(timestamptz) owner to bodeul_migration;
alter function bodeul.claim_expired_companion_attachments(timestamptz, integer) owner to bodeul_migration;
alter function bodeul.mark_companion_attachment_deleted(uuid, text, timestamptz) owner to bodeul_migration;
alter function bodeul.purge_expired_companion_records(timestamptz, integer) owner to bodeul_migration;
alter function bodeul.begin_retention_job(text, timestamptz, timestamptz) owner to bodeul_migration;
alter function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text) owner to bodeul_migration;
alter function bodeul.retention_monthly_summary(date) owner to bodeul_migration;

revoke all on function bodeul.preview_expired_companion_data(timestamptz)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.claim_expired_companion_attachments(timestamptz, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.mark_companion_attachment_deleted(uuid, text, timestamptz)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.purge_expired_companion_records(timestamptz, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.begin_retention_job(text, timestamptz, timestamptz)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.retention_monthly_summary(date)
    from public, anon, authenticated, service_role, bodeul_core_runtime;

grant execute on function bodeul.preview_expired_companion_data(timestamptz)
    to bodeul_retention_runtime;
grant execute on function bodeul.claim_expired_companion_attachments(timestamptz, integer)
    to bodeul_retention_runtime;
grant execute on function bodeul.mark_companion_attachment_deleted(uuid, text, timestamptz)
    to bodeul_retention_runtime;
grant execute on function bodeul.purge_expired_companion_records(timestamptz, integer)
    to bodeul_retention_runtime;
grant execute on function bodeul.begin_retention_job(text, timestamptz, timestamptz)
    to bodeul_retention_runtime;
grant execute on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    to bodeul_retention_runtime;
grant execute on function bodeul.retention_monthly_summary(date)
    to bodeul_retention_runtime, bodeul_admin_runtime;
