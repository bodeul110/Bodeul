begin;
set local role bodeul_migration;

do $$
declare
    sample_appointment_id uuid;
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'appointment_requests'
          and column_name = 'public_code'
          and is_nullable = 'NO'
          and column_default is not null
    ) then
        raise exception '예약 공개 코드 열의 NOT NULL 또는 롤링 배포 default가 없습니다.';
    end if;

    if exists (
        select 1
        from bodeul.appointment_requests
        where public_code is null
           or public_code !~ '^BD-[A-Z0-9]{6}$'
    ) then
        raise exception '기존 예약 공개 코드 backfill 결과가 형식 계약을 위반합니다.';
    end if;

    if exists (
        select public_code
        from bodeul.appointment_requests
        group by public_code
        having count(*) > 1
    ) then
        raise exception '예약 공개 코드가 중복되어 있습니다.';
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'bodeul.appointment_requests'::regclass
          and conname = 'uq_appointment_requests_public_code'
          and contype = 'u'
    ) then
        raise exception '예약 공개 코드 unique 제약이 없습니다.';
    end if;

    if not exists (
        select 1
        from pg_trigger
        where tgrelid = 'bodeul.appointment_requests'::regclass
          and tgname = 'appointment_requests_public_code_immutable'
          and not tgisinternal
    ) then
        raise exception '예약 공개 코드 변경 금지 trigger가 없습니다.';
    end if;

    if not exists (
        select 1
        from pg_class
        where oid = 'bodeul.appointment_public_code_search_audit'::regclass
          and relrowsecurity
    ) then
        raise exception '관리자 검색 감사 테이블의 RLS가 활성화되지 않았습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.search_appointment_by_public_code(uuid,text)',
        'EXECUTE'
    ) then
        raise exception '관리자 runtime에 공개 코드 정확 검색 권한이 없습니다.';
    end if;

    if has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.search_appointment_by_public_code(uuid,text)',
        'EXECUTE'
    ) then
        raise exception 'Core runtime에 관리자 공개 코드 검색 권한이 노출됐습니다.';
    end if;

    select id
    into sample_appointment_id
    from bodeul.appointment_requests
    order by created_at
    limit 1;

    if sample_appointment_id is not null then
        begin
            update bodeul.appointment_requests
            set public_code = case
                when public_code = 'BD-AAAAAA' then 'BD-BBBBBB'
                else 'BD-AAAAAA'
            end
            where id = sample_appointment_id;
            raise exception '예약 공개 코드 변경 금지 trigger가 수정을 허용했습니다.';
        exception
            when sqlstate '22023' then
                null;
        end;
    end if;
end
$$;

rollback;
