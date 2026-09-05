-- 018 검증의 합성 데이터와 트랜잭션 안에서 실행한다.
set local role bodeul_migration;
do $$
begin
    if not has_function_privilege('bodeul_admin_runtime',
            'bodeul.get_admin_bank_transfer_payment(uuid,uuid)', 'EXECUTE')
            or exists (
                select 1 from (values ('anon'), ('authenticated'), ('service_role'),
                    ('bodeul_core_runtime')) blocked(role_name)
                where has_function_privilege(blocked.role_name,
                    'bodeul.get_admin_bank_transfer_payment(uuid,uuid)', 'EXECUTE')
            ) then
        raise exception '관리자 결제 조회 함수의 실행 권한이 분리되지 않았습니다.';
    end if;
end;
$$;

set local role bodeul_admin_runtime;
do $$
declare
    v_payment jsonb;
    v_actor uuid;
begin
    v_payment := bodeul.get_admin_bank_transfer_payment(
        '10000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000003');
    if v_payment ->> 'paymentStatusCode' <> 'REFUND_REQUESTED'
            or v_payment ->> 'appointmentStatus' <> 'CANCELED'
            or (v_payment ->> 'paymentVersion')::bigint <> 3
            or v_payment ->> 'publicCode' !~ '^BD-[A-Z0-9]{6}$'
            or jsonb_array_length(v_payment -> 'events') <> 4
            or (v_payment ->> 'hasMoreEvents')::boolean then
        raise exception '관리자 결제 조회가 최신 원장과 감사 이력을 반환하지 않았습니다.';
    end if;
    if v_payment::text ~ 'patient@example.com|010-1111-1111|depositor_name_fingerprint|operation_id' then
        raise exception '관리자 결제 조회가 불필요한 개인정보 또는 작업 지문을 노출했습니다.';
    end if;
    foreach v_actor in array array[
        '10000000-0000-0000-0000-000000000001'::uuid,
        '10000000-0000-0000-0000-000000000005'::uuid,
        '10000000-0000-0000-0000-000000000099'::uuid
    ] loop
        begin
            perform bodeul.get_admin_bank_transfer_payment(v_actor,
                '20000000-0000-0000-0000-000000000003');
            raise exception '운영 역할 없는 사용자의 결제 조회가 허용됐습니다.' using errcode = 'P0004';
        exception when insufficient_privilege then null;
        end;
    end loop;
    begin
        perform bodeul.get_admin_bank_transfer_payment(
            '10000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000099');
        raise exception '존재하지 않는 결제 조회가 성공했습니다.' using errcode = 'P0004';
    exception when sqlstate 'P0002' then null;
    end;
end;
$$;

set local role bodeul_migration;
do $$
begin
    if (select count(*) from bodeul.admin_access_audits
            where action = 'RAW_VIEW' and resource_type = 'APPOINTMENT_PAYMENT') <> 1 then
        raise exception '결제 조회의 관리자 접근 감사가 누락되거나 중복됐습니다.';
    end if;
end;
$$;

insert into bodeul.appointment_payment_events (
    appointment_request_id, actor_role, event_type, previous_status_code,
    next_status_code, expected_payment_version, created_at
)
select '20000000-0000-0000-0000-000000000003', 'SYSTEM', 'CREATED',
    'AWAITING_DEPOSIT', 'AWAITING_DEPOSIT', 0, now() - n * interval '1 minute'
from generate_series(1, 25) n;

set local role bodeul_admin_runtime;
do $$
declare v_payment jsonb;
begin
    v_payment := bodeul.get_admin_bank_transfer_payment(
        '10000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000003');
    if jsonb_array_length(v_payment -> 'events') <> 20
            or not (v_payment ->> 'hasMoreEvents')::boolean then
        raise exception '관리자 결제 이력의 최근 20건 제한이 지켜지지 않았습니다.';
    end if;
end;
$$;

set local role bodeul_migration;
update bodeul.admin_role_assignments
set revoked_at = now(), revoked_by_admin_user_id = '10000000-0000-0000-0000-000000000005'
where admin_user_id = '10000000-0000-0000-0000-000000000004';
set local role bodeul_admin_runtime;
do $$
begin
    begin
        perform bodeul.get_admin_bank_transfer_payment(
            '10000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000003');
        raise exception '철회된 운영 역할의 결제 조회가 허용됐습니다.' using errcode = 'P0004';
    exception when insufficient_privilege then null;
    end;
end;
$$;
