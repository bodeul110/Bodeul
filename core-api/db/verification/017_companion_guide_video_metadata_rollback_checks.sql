begin;

do $$
begin
    if to_regprocedure('bodeul.is_valid_guide_step_media_v1(jsonb)') is not null then
        raise exception '영상 메타데이터 검증 함수가 롤백 후 남아 있습니다.';
    end if;

    if exists (
        select 1
        from pg_constraint
        where conname in (
            'ck_hospital_guides_step_media_shape',
            'ck_companion_sessions_guide_media_shape'
        )
    ) then
        raise exception '영상 메타데이터 제약이 롤백 후 남아 있습니다.';
    end if;
end;
$$;

rollback;
