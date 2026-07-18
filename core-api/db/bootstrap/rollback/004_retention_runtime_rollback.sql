begin;

revoke usage on schema bodeul from bodeul_retention_runtime;
revoke bodeul_retention_runtime from bodeul_retention_service;

drop role if exists bodeul_retention_service;
drop role if exists bodeul_retention_runtime;

commit;
