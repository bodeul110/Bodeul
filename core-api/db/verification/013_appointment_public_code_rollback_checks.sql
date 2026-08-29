begin transaction read only;
set local role bodeul_migration;

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'appointment_requests'
          and column_name = 'public_code'
    ) then
        raise exception '예약 공개 코드 열이 롤백 후 남아 있습니다.';
    end if;

    if to_regclass('bodeul.appointment_public_code_search_audit') is not null then
        raise exception '예약 공개 코드 검색 감사 테이블이 롤백 후 남아 있습니다.';
    end if;

    if to_regprocedure('bodeul.search_appointment_by_public_code(uuid,text)') is not null
        or to_regprocedure('bodeul.prevent_appointment_public_code_change()') is not null then
        raise exception '예약 공개 코드 함수가 롤백 후 남아 있습니다.';
    end if;

    if exists (
        select 1
        from pg_trigger trigger_record
        where trigger_record.tgrelid = 'bodeul.appointment_requests'::regclass
          and trigger_record.tgname = 'appointment_requests_public_code_immutable'
          and not trigger_record.tgisinternal
    ) then
        raise exception '예약 공개 코드 변경 금지 트리거가 롤백 후 남아 있습니다.';
    end if;
end;
$$;

rollback;
