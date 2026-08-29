do $$
begin
    if to_regclass('bodeul.companion_session_artifacts') is not null
            or to_regclass('bodeul.companion_session_artifact_operations') is not null then
        raise exception 'V18 첨부 테이블 rollback이 완료되지 않았습니다.';
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'companion_sessions'
          and column_name in (
              'care_ended_at',
              'manager_journal',
              'report_generation_status',
              'report_generation_attempts',
              'report_generation_last_error',
              'report_generation_updated_at'
          )
    ) then
        raise exception 'V18 완료 컬럼 rollback이 완료되지 않았습니다.';
    end if;

    begin
        update bodeul.companion_sessions
        set current_status = 'CARE_ENDED'
        where id = '30000000-0000-0000-0000-000000000001';
        raise exception 'rollback 후 CARE_ENDED 상태가 허용되고 있습니다.';
    exception when check_violation then
        null;
    end;
end;
$$;
