revoke execute on function bodeul.search_appointment_by_public_code(uuid, text)
    from bodeul_admin_runtime;
drop function if exists bodeul.search_appointment_by_public_code(uuid, text);

drop table if exists bodeul.appointment_public_code_search_audit;

drop trigger if exists appointment_requests_public_code_immutable
    on bodeul.appointment_requests;
drop function if exists bodeul.prevent_appointment_public_code_change();

alter table bodeul.appointment_requests
    drop constraint if exists uq_appointment_requests_public_code,
    drop constraint if exists ck_appointment_requests_public_code,
    drop column if exists public_code;
