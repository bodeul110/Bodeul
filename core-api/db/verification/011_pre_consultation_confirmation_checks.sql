do $$
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'companion_sessions'
          and column_name = 'pre_consultation_confirmed'
          and data_type = 'boolean'
          and is_nullable = 'NO'
          and column_default = 'false'
    ) then
        raise exception '진료 전 필수 확인 열이 올바르게 생성되지 않았습니다.';
    end if;

    if not has_column_privilege(
        'bodeul_core_runtime',
        'bodeul.companion_sessions',
        'pre_consultation_confirmed',
        'UPDATE'
    ) then
        raise exception 'Core API runtime에 진료 전 확인 상태 수정 권한이 없습니다.';
    end if;
end
$$;
