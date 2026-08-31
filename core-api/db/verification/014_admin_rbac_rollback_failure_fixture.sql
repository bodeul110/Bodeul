begin;
set local role bodeul_migration;

create view bodeul.verify_v20_rollback_dependency as
select *
from bodeul.resolve_admin_authorization('verify-v20-rollback-atomicity');

commit;
