-- schema별 default privilege로는 전역 PUBLIC 함수 실행 권한을 취소할 수 없다.
-- migration role이 만드는 함수는 명시적으로 grant하기 전까지 실행할 수 없게 한다.
set local role bodeul_migration;

alter default privileges for role bodeul_migration
    revoke execute on functions from public, anon, authenticated, service_role;

reset role;
