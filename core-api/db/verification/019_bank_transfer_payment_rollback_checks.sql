begin transaction read only;
set local role bodeul_migration;

do $$
declare
    v_inventory_columns text[];
begin
    if to_regclass('bodeul.appointment_bank_transfer_payments') is not null
            or to_regclass('bodeul.appointment_payment_events') is not null then
        raise exception '무통장입금 상세 또는 감사 테이블이 롤백 후 남아 있습니다.';
    end if;
    if to_regprocedure('bodeul.get_bank_transfer_payment(uuid,uuid)') is not null
            or to_regprocedure('bodeul.set_bank_transfer_depositor(uuid,uuid,uuid,bigint,text)') is not null
            or to_regprocedure(
                'bodeul.transition_appointment_bank_transfer_payment(uuid,uuid,uuid,bigint,text,integer,text)'
            ) is not null then
        raise exception '무통장입금 함수가 롤백 후 남아 있습니다.';
    end if;
    if exists (
        select 1 from pg_constraint
        where conrelid = 'bodeul.appointment_requests'::regclass
          and conname = 'ck_appointment_requests_payment_contract'
    ) then
        raise exception '무통장입금 결제수단-상태 결합 제약이 롤백 후 남아 있습니다.';
    end if;
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'appointment_requests'
          and column_name = 'create_request_fingerprint'
    ) or to_regprocedure('bodeul.guard_appointment_create_request_fingerprint()') is not null then
        raise exception '예약 생성 요청 지문 열 또는 guard가 롤백 후 남아 있습니다.';
    end if;
    if to_regprocedure('bodeul.account_deletion_postgres_inventory(uuid)') is null
            or pg_get_function_result(
                'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
            ) like '%bank_transfer_payment_count%'
            or pg_get_function_result(
                'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
            ) like '%payment_event_count%'
            or (
                select count(*)
                from bodeul.account_deletion_postgres_inventory(
                    '00000000-0000-0000-0000-000000000000'
                ) inventory
                cross join lateral jsonb_object_keys(to_jsonb(inventory)) inventory_column
            ) <> 14 then
        raise exception '계정 삭제 영향도 함수가 V15 반환 계약으로 복원되지 않았습니다.';
    end if;

    select array_agg(argument.argument_name order by argument.ordinal_position)
    into v_inventory_columns
    from pg_proc proc
    cross join lateral unnest(proc.proargnames, proc.proargmodes)
        with ordinality as argument(argument_name, argument_mode, ordinal_position)
    where proc.oid = 'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
      and argument.argument_mode in ('o', 'b', 't');

    if v_inventory_columns is distinct from array[
        'profile_count',
        'appointment_count',
        'active_appointment_count',
        'companion_session_count',
        'active_companion_session_count',
        'session_report_count',
        'appointment_follow_up_count',
        'assignment_audit_count',
        'related_chat_message_count',
        'sent_chat_message_count',
        'related_chat_attachment_count',
        'related_chat_read_receipt_count',
        'related_location_count',
        'active_legal_hold_count'
    ]::text[] then
        raise exception '계정 삭제 영향도 함수의 V15 반환 열 이름 또는 순서가 다릅니다.';
    end if;

    if not exists (
        select 1
        from pg_proc proc
        where proc.oid = 'bodeul.account_deletion_postgres_inventory(uuid)'::regprocedure
          and pg_get_userbyid(proc.proowner) = 'bodeul_migration'
          and proc.prosecdef
          and proc.provolatile = 's'
          and coalesce(proc.proconfig, '{}'::text[])
                @> array['search_path=bodeul, pg_temp']
          and not exists (
              select 1
              from aclexplode(coalesce(proc.proacl, acldefault('f', proc.proowner))) access
              where access.grantee = 0
                and access.privilege_type = 'EXECUTE'
          )
    ) or not has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.account_deletion_postgres_inventory(uuid)',
        'EXECUTE'
    ) or not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.account_deletion_postgres_inventory(uuid)',
        'EXECUTE'
    ) or has_function_privilege(
        'anon',
        'bodeul.account_deletion_postgres_inventory(uuid)',
        'EXECUTE'
    ) or has_function_privilege(
        'authenticated',
        'bodeul.account_deletion_postgres_inventory(uuid)',
        'EXECUTE'
    ) or has_function_privilege(
        'service_role',
        'bodeul.account_deletion_postgres_inventory(uuid)',
        'EXECUTE'
    ) then
        raise exception '계정 삭제 영향도 함수의 V15 소유권·보안 또는 실행 권한이 다릅니다.';
    end if;

    if pg_get_functiondef(
        'bodeul.assign_companion_session(uuid,uuid,uuid,bigint,text)'::regprocedure
    ) like '%DEPOSIT_CONFIRMED%' then
        raise exception '매칭 함수의 무통장입금 제약이 롤백 후 남아 있습니다.';
    end if;
end;
$$;

rollback;
