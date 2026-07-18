-- BoDeul 서버가 공유하는 PostgreSQL 권한 기반을 준비한다.
-- 비밀번호는 이 파일에서 설정하지 않는다. *_service, *_migrator role은
-- 별도 보안 절차로 비밀번호를 설정하기 전까지 로그인할 수 없다.

begin;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'bodeul_migration') then
        create role bodeul_migration nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_core_runtime') then
        create role bodeul_core_runtime nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_admin_runtime') then
        create role bodeul_admin_runtime nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_migrator') then
        create role bodeul_migrator nologin inherit connection limit 2;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_core_service') then
        create role bodeul_core_service nologin inherit connection limit 5;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_admin_service') then
        create role bodeul_admin_service nologin inherit connection limit 5;
    end if;
end
$$;

alter role bodeul_migration
    nologin noinherit nocreatedb nocreaterole;
alter role bodeul_core_runtime
    nologin noinherit nocreatedb nocreaterole;
alter role bodeul_admin_runtime
    nologin noinherit nocreatedb nocreaterole;

alter role bodeul_migrator
    inherit nocreatedb nocreaterole connection limit 2;
alter role bodeul_core_service
    inherit nocreatedb nocreaterole connection limit 5;
alter role bodeul_admin_service
    inherit nocreatedb nocreaterole connection limit 5;

grant bodeul_migration to postgres with inherit false, set true;
grant bodeul_migration to bodeul_migrator with inherit true, set true;
grant bodeul_core_runtime to bodeul_core_service with inherit true, set true;
grant bodeul_admin_runtime to bodeul_admin_service with inherit true, set true;

create schema if not exists bodeul authorization bodeul_migration;
alter schema bodeul owner to bodeul_migration;

-- managed realtime table 권한을 migration role에 넘기지 않고, 검증된 private 신호만
-- Supabase Realtime로 전달하는 privileged wrapper를 둔다.
create or replace function bodeul.send_companion_realtime_signal(
    p_payload jsonb,
    p_event text,
    p_topic text
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_resource text;
    v_session_id text;
begin
    if to_regprocedure('realtime.send(jsonb,text,text,boolean)') is null then
        return false;
    end if;
    if jsonb_typeof(p_payload) <> 'object'
            or p_payload - array['sessionId', 'resource', 'recordId'] <> '{}'::jsonb then
        raise exception 'Realtime payload 형식을 확인해 주세요.' using errcode = '22023';
    end if;

    v_resource := p_payload ->> 'resource';
    v_session_id := p_payload ->> 'sessionId';
    if not (
        (p_event = 'chat.changed' and v_resource = 'chat')
        or (p_event = 'read-receipt.changed' and v_resource = 'read-receipt')
        or (p_event = 'location.changed' and v_resource = 'location')
    ) then
        raise exception 'Realtime 이벤트와 리소스가 일치하지 않습니다.' using errcode = '22023';
    end if;
    if p_topic !~ '^companion-session:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or v_session_id <> split_part(p_topic, ':', 2)
            or coalesce(p_payload ->> 'recordId', '')
                !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        raise exception 'Realtime 식별자 형식을 확인해 주세요.' using errcode = '22023';
    end if;

    perform realtime.send(p_payload, p_event, p_topic, true);
    return true;
end;
$$;

alter function bodeul.send_companion_realtime_signal(jsonb, text, text) owner to postgres;
revoke all on function bodeul.send_companion_realtime_signal(jsonb, text, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.send_companion_realtime_signal(jsonb, text, text)
    to bodeul_migration;

set local role bodeul_migration;

revoke all on schema bodeul from public, anon, authenticated, service_role;
grant usage, create on schema bodeul to bodeul_migration;
grant usage on schema bodeul to bodeul_core_runtime, bodeul_admin_runtime;

revoke all on all tables in schema bodeul from public, anon, authenticated, service_role;
revoke all on all sequences in schema bodeul from public, anon, authenticated, service_role;
revoke all on all functions in schema bodeul from public, anon, authenticated, service_role;

alter default privileges for role bodeul_migration in schema bodeul
    revoke all on tables from public, anon, authenticated, service_role;
alter default privileges for role bodeul_migration in schema bodeul
    revoke all on sequences from public, anon, authenticated, service_role;
alter default privileges for role bodeul_migration
    revoke execute on functions from public, anon, authenticated, service_role;

comment on schema bodeul is 'BoDeul 서버 전용 데이터 schema. Supabase Data API에 노출하지 않는다.';

reset role;

-- 현재 구조에서는 Data API를 사용하지 않는다. public에 새 객체가 생겨도
-- anon/authenticated/service_role에 자동 노출되지 않도록 기존 기본값을 보강한다.
alter default privileges for role postgres in schema public
    revoke all on tables from anon, authenticated, service_role;
alter default privileges for role postgres in schema public
    revoke all on sequences from anon, authenticated, service_role;
alter default privileges for role postgres in schema public
    revoke execute on functions from public, anon, authenticated, service_role;

alter role bodeul_migrator set search_path = bodeul, public;
alter role bodeul_core_service set search_path = bodeul, public;
alter role bodeul_admin_service set search_path = bodeul, public;

comment on role bodeul_migration is 'BoDeul schema와 Flyway migration 소유 권한';
comment on role bodeul_core_runtime is 'Spring Core API 최소 runtime 권한';
comment on role bodeul_admin_runtime is 'Next.js 관리자 서버 최소 runtime 권한';

commit;
