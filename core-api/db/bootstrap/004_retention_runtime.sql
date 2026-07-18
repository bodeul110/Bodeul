-- 자동 파기 작업은 Core API나 관리자 서버의 런타임 권한을 재사용하지 않는다.
-- 비밀번호는 이 파일에 두지 않고 환경별 보안 절차에서 별도로 설정한다.

begin;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'bodeul_retention_runtime') then
        create role bodeul_retention_runtime nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'bodeul_retention_service') then
        create role bodeul_retention_service nologin inherit connection limit 2;
    end if;
end
$$;

alter role bodeul_retention_runtime
    nologin noinherit nocreatedb nocreaterole;
alter role bodeul_retention_service
    inherit nocreatedb nocreaterole connection limit 2;

grant bodeul_retention_runtime to bodeul_retention_service with inherit true, set true;

grant usage on schema bodeul to bodeul_retention_runtime;
alter role bodeul_retention_service set search_path = bodeul, public;

comment on role bodeul_retention_runtime is '자동 파기 작업의 최소 runtime 권한';
comment on role bodeul_retention_service is '예약 파기 작업 전용 로그인 role';

commit;
