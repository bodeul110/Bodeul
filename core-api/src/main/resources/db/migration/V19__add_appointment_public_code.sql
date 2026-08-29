alter table bodeul.appointment_requests
    add column public_code text;

do $$
declare
    appointment_row record;
    candidate text;
    attempt integer;
begin
    for appointment_row in
        select id
        from bodeul.appointment_requests
        where public_code is null
        order by id
    loop
        candidate := null;
        for attempt in 0..63 loop
            candidate := 'BD-' || upper(substr(md5(appointment_row.id::text || ':' || attempt::text), 1, 6));
            exit when not exists (
                select 1
                from bodeul.appointment_requests existing
                where existing.public_code = candidate
            );
            candidate := null;
        end loop;

        if candidate is null then
            raise exception '기존 예약의 공개 코드를 64회 안에 생성하지 못했습니다.';
        end if;

        update bodeul.appointment_requests
        set public_code = candidate
        where id = appointment_row.id;
    end loop;
end
$$;

alter table bodeul.appointment_requests
    alter column public_code set default ('BD-' || upper(substr(md5(gen_random_uuid()::text), 1, 6))),
    alter column public_code set not null,
    add constraint ck_appointment_requests_public_code
        check (public_code ~ '^BD-[A-Z0-9]{6}$'),
    add constraint uq_appointment_requests_public_code
        unique (public_code);

comment on column bodeul.appointment_requests.public_code is
    '예약 신청자, 배정 매니저와 관리자에게만 표시하는 예약 공개 코드. 인가 식별자로 사용하지 않는다.';

create function bodeul.prevent_appointment_public_code_change()
returns trigger
language plpgsql
set search_path = pg_catalog
as $$
begin
    if new.public_code is distinct from old.public_code then
        raise exception '예약 공개 코드는 발급 후 변경할 수 없습니다.' using errcode = '22023';
    end if;
    return new;
end
$$;

alter function bodeul.prevent_appointment_public_code_change() owner to bodeul_migration;
revoke all on function bodeul.prevent_appointment_public_code_change()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger appointment_requests_public_code_immutable
before update of public_code on bodeul.appointment_requests
for each row
execute function bodeul.prevent_appointment_public_code_change();

create table bodeul.appointment_public_code_search_audit (
    id uuid primary key default gen_random_uuid(),
    actor_admin_user_id uuid not null,
    public_code_hash text not null,
    outcome text not null,
    requested_at timestamptz not null default now(),
    constraint fk_appointment_public_code_search_audit_actor
        foreign key (actor_admin_user_id) references bodeul.app_users (id),
    constraint ck_appointment_public_code_search_audit_hash
        check (public_code_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_appointment_public_code_search_audit_outcome
        check (outcome in ('FOUND', 'NOT_FOUND', 'RATE_LIMITED'))
);

comment on table bodeul.appointment_public_code_search_audit is
    '관리자 예약 공개 코드 정확 검색의 요청 제한과 보안 감사를 위한 기록';
comment on column bodeul.appointment_public_code_search_audit.public_code_hash is
    '평문 공개 코드를 남기지 않는 SHA-256 해시';

create index ix_appointment_public_code_search_audit_actor_time
    on bodeul.appointment_public_code_search_audit (actor_admin_user_id, requested_at desc);

revoke all on table bodeul.appointment_public_code_search_audit
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
alter table bodeul.appointment_public_code_search_audit enable row level security;

create function bodeul.search_appointment_by_public_code(
    p_actor_admin_user_id uuid,
    p_public_code text
)
returns table (
    lookup_status text,
    appointment_request_id uuid,
    public_code text,
    appointment_status text,
    appointment_at timestamptz,
    hospital_name text,
    department_name text,
    patient_name text,
    guardian_name text,
    manager_user_id uuid,
    manager_name text
)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    normalized_code text := upper(btrim(coalesce(p_public_code, '')));
    recent_searches integer;
    matched bodeul.appointment_requests%rowtype;
    matched_manager_name text;
begin
    if normalized_code !~ '^BD-[A-Z0-9]{6}$' then
        raise exception '예약 공개 코드 형식이 올바르지 않습니다.' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from bodeul.app_users admin_user
        where admin_user.id = p_actor_admin_user_id
          and admin_user.role = 'ADMIN'
    ) then
        raise exception '관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(p_actor_admin_user_id::text, 0));
    select count(*)::integer
    into recent_searches
    from bodeul.appointment_public_code_search_audit audit
    where audit.actor_admin_user_id = p_actor_admin_user_id
      and audit.outcome <> 'RATE_LIMITED'
      and audit.requested_at >= now() - interval '1 minute';

    if recent_searches >= 10 then
        if not exists (
            select 1
            from bodeul.appointment_public_code_search_audit audit
            where audit.actor_admin_user_id = p_actor_admin_user_id
              and audit.outcome = 'RATE_LIMITED'
              and audit.requested_at >= now() - interval '1 minute'
        ) then
            insert into bodeul.appointment_public_code_search_audit (
                actor_admin_user_id,
                public_code_hash,
                outcome
            ) values (
                p_actor_admin_user_id,
                encode(sha256(convert_to(normalized_code, 'UTF8')), 'hex'),
                'RATE_LIMITED'
            );
        end if;

        return query select
            'RATE_LIMITED'::text,
            null::uuid,
            null::text,
            null::text,
            null::timestamptz,
            null::text,
            null::text,
            null::text,
            null::text,
            null::uuid,
            null::text;
        return;
    end if;

    select appointment.*
    into matched
    from bodeul.appointment_requests appointment
    where appointment.public_code = normalized_code
    limit 1;

    insert into bodeul.appointment_public_code_search_audit (
        actor_admin_user_id,
        public_code_hash,
        outcome
    ) values (
        p_actor_admin_user_id,
        encode(sha256(convert_to(normalized_code, 'UTF8')), 'hex'),
        case when matched.id is null then 'NOT_FOUND' else 'FOUND' end
    );

    if matched.id is null then
        return query select
            'NOT_FOUND'::text,
            null::uuid,
            null::text,
            null::text,
            null::timestamptz,
            null::text,
            null::text,
            null::text,
            null::text,
            null::uuid,
            null::text;
        return;
    end if;

    select manager.name
    into matched_manager_name
    from bodeul.app_users manager
    where manager.id = matched.manager_user_id;

    return query select
        'FOUND'::text,
        matched.id,
        matched.public_code,
        matched.status,
        matched.appointment_at,
        matched.hospital_name,
        matched.department_name,
        matched.patient_name,
        matched.guardian_name,
        matched.manager_user_id,
        coalesce(matched_manager_name, '');
end
$$;

alter function bodeul.search_appointment_by_public_code(uuid, text) owner to bodeul_migration;

revoke all on function bodeul.search_appointment_by_public_code(uuid, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.search_appointment_by_public_code(uuid, text)
    to bodeul_admin_runtime;
