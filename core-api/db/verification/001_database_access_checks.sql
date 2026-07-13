select
    rolname,
    rolcanlogin,
    rolsuper,
    rolcreaterole,
    rolcreatedb,
    rolreplication,
    rolbypassrls,
    rolconnlimit
from pg_roles
where rolname in (
    'bodeul_migration',
    'bodeul_core_runtime',
    'bodeul_admin_runtime',
    'bodeul_migrator',
    'bodeul_core_service',
    'bodeul_admin_service'
)
order by rolname;

select
    role_name,
    has_schema_privilege(role_name, 'bodeul', 'USAGE') as can_use_schema,
    has_schema_privilege(role_name, 'bodeul', 'CREATE') as can_create_in_schema
from unnest(array[
    'bodeul_migration',
    'bodeul_core_runtime',
    'bodeul_admin_runtime',
    'bodeul_migrator',
    'bodeul_core_service',
    'bodeul_admin_service',
    'anon',
    'authenticated',
    'service_role'
]) as role_name
order by role_name;

select
    member.rolname as member_role,
    granted.rolname as granted_role,
    membership.inherit_option,
    membership.set_option,
    membership.admin_option
from pg_auth_members membership
join pg_roles member on member.oid = membership.member
join pg_roles granted on granted.oid = membership.roleid
where member.rolname in (
    'postgres',
    'bodeul_migrator',
    'bodeul_core_service',
    'bodeul_admin_service'
)
  and granted.rolname like 'bodeul_%'
order by member_role, granted_role;

select
    defaclrole::regrole::text as owner_role,
    coalesce(defaclnamespace::regnamespace::text, '(all schemas)') as schema_name,
    defaclobjtype as object_type,
    defaclacl::text as acl
from pg_default_acl
where defaclrole in (
    'postgres'::regrole,
    'bodeul_migration'::regrole
)
  and (
    (defaclrole = 'postgres'::regrole and defaclnamespace = 'public'::regnamespace)
    or
    (defaclrole = 'bodeul_migration'::regrole and defaclnamespace = 0)
  )
order by owner_role, schema_name, object_type;

select
    schemaname,
    tablename,
    tableowner
from pg_tables
where schemaname = 'bodeul'
  and tablename = 'flyway_schema_history';

select
    table_schema,
    table_name,
    table_type,
    pg_get_userbyid(class.relowner) as table_owner,
    class.relrowsecurity as rls_enabled
from information_schema.tables
join pg_class class on class.oid = format('%I.%I', table_schema, table_name)::regclass
where table_schema = 'bodeul'
  and table_name = 'app_users';

select
    grantee,
    privilege_type
from information_schema.role_table_grants
where table_schema = 'bodeul'
  and table_name = 'app_users'
order by grantee, privilege_type;

select
    policyname,
    roles,
    cmd,
    qual,
    with_check
from pg_policies
where schemaname = 'bodeul'
  and tablename = 'app_users'
order by policyname;
