-- 개발 DB에서 bootstrap 직후 되돌릴 때만 사용한다.
-- public schema의 자동 권한 차단은 보안 기준이므로 복원하지 않는다.

begin;

do $$
begin
    if exists (
        select 1
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'bodeul'
          and c.relkind in ('r', 'p', 'v', 'm', 'S', 'f')
    ) then
        raise exception 'bodeul schema에 객체가 있어 자동 rollback을 중단합니다.';
    end if;
end
$$;

set local role bodeul_migration;

alter default privileges for role bodeul_migration
    grant execute on functions to public;

reset role;

drop schema if exists bodeul;

do $$
begin
    if exists (select 1 from pg_namespace where nspname = 'realtime') then
        execute 'revoke usage on schema realtime from bodeul_migration';
    end if;
end
$$;

revoke bodeul_migration from bodeul_migrator;
revoke bodeul_core_runtime from bodeul_core_service;
revoke bodeul_admin_runtime from bodeul_admin_service;
revoke bodeul_migration from postgres;

drop role if exists bodeul_migrator;
drop role if exists bodeul_core_service;
drop role if exists bodeul_admin_service;
drop role if exists bodeul_migration;
drop role if exists bodeul_core_runtime;
drop role if exists bodeul_admin_runtime;

commit;
