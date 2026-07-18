select
    project_id
from bodeul_realtime_auth.allowed_firebase_projects
order by project_id;

select
    policyname,
    roles,
    cmd,
    qual,
    with_check
from pg_policies
where schemaname = 'realtime'
  and tablename = 'messages'
  and policyname = 'bodeul participants can receive companion broadcasts';

select
    has_schema_privilege('authenticated', 'bodeul_realtime_auth', 'USAGE')
        as authenticated_can_use_auth_schema,
    has_function_privilege(
        'authenticated',
        'bodeul_realtime_auth.can_receive_companion_broadcast()',
        'EXECUTE'
    ) as authenticated_can_execute_helper,
    has_table_privilege(
        'authenticated',
        'bodeul_realtime_auth.allowed_firebase_projects',
        'SELECT'
    ) as authenticated_can_read_allowed_projects,
    has_schema_privilege('authenticated', 'bodeul', 'USAGE')
        as authenticated_can_use_business_schema;

select
    pg_get_userbyid(procedure.proowner) as function_owner,
    procedure.prosecdef as security_definer,
    procedure.provolatile as volatility
from pg_proc procedure
where procedure.oid =
    'bodeul_realtime_auth.can_receive_companion_broadcast()'::regprocedure;
