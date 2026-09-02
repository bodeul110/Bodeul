begin;
set local role bodeul_migration;

do $$
begin
    if to_regclass('bodeul.appointment_bank_transfer_payments') is null
            or to_regclass('bodeul.appointment_payment_events') is null
            or to_regprocedure('bodeul.get_bank_transfer_payment(uuid,uuid)') is null
            or to_regprocedure('bodeul.guard_appointment_create_request_fingerprint()') is null
            or to_regprocedure('bodeul.account_deletion_postgres_inventory(uuid)') is null
            or not exists (
                select 1 from information_schema.columns
                where table_schema = 'bodeul'
                  and table_name = 'appointment_requests'
                  and column_name = 'create_request_fingerprint'
            )
            or pg_get_function_result(
                'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
            ) not like '%bank_transfer_payment_count%'
            or pg_get_function_result(
                'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
            ) not like '%payment_event_count%'
            or not exists (
                select 1 from pg_constraint
                where conrelid = 'bodeul.appointment_requests'::regclass
                  and conname = 'ck_appointment_requests_payment_contract'
            )
            or not exists (
                select 1 from bodeul.appointment_requests
                where id = '50000000-0000-0000-0000-000000000002'
            )
            or not exists (
                select 1 from bodeul.appointment_bank_transfer_payments
                where appointment_request_id = '50000000-0000-0000-0000-000000000002'
            )
            or not exists (
                select 1 from bodeul.appointment_payment_events
                where appointment_request_id = '50000000-0000-0000-0000-000000000002'
                  and event_type = 'CREATED'
            ) then
        raise exception '실패한 V22 롤백이 일부 객체 또는 운영 자료를 제거했습니다.';
    end if;
end;
$$;

delete from bodeul.appointment_payment_events
where appointment_request_id = '50000000-0000-0000-0000-000000000002';
delete from bodeul.appointment_bank_transfer_payments
where appointment_request_id = '50000000-0000-0000-0000-000000000002';
delete from bodeul.appointment_requests
where id = '50000000-0000-0000-0000-000000000002';
delete from bodeul.app_users
where id = '50000000-0000-0000-0000-000000000001';

commit;
