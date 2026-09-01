create function bodeul.is_valid_guide_step_media_v1(
    p_steps jsonb
) returns boolean
language sql
immutable
strict
parallel safe
security invoker
set search_path = pg_catalog, pg_temp
as $$
    select case
        when jsonb_typeof(p_steps) <> 'array' then false
        else not exists (
            select 1
            from jsonb_array_elements(p_steps) as entry(step)
            where jsonb_typeof(entry.step) <> 'object'
               or (
                    entry.step ? 'videoAssetId'
                    and jsonb_typeof(entry.step -> 'videoAssetId')
                        not in ('string', 'null')
               )
               or (
                    jsonb_typeof(entry.step -> 'videoAssetId') = 'string'
                    and btrim(entry.step ->> 'videoAssetId') = ''
               )
               or (
                    entry.step ? 'videoAssetVersion'
                    and jsonb_typeof(entry.step -> 'videoAssetVersion')
                        not in ('string', 'null')
               )
               or (
                    jsonb_typeof(entry.step -> 'videoAssetVersion') = 'string'
                    and btrim(entry.step ->> 'videoAssetVersion') = ''
               )
               or (
                    entry.step ? 'videoFallbackText'
                    and jsonb_typeof(entry.step -> 'videoFallbackText')
                        not in ('string', 'null')
               )
               or (
                    jsonb_typeof(entry.step -> 'videoFallbackText') = 'string'
                    and btrim(entry.step ->> 'videoFallbackText') = ''
               )
               or (
                    (
                        coalesce(
                            jsonb_typeof(entry.step -> 'videoAssetId') = 'string',
                            false
                        )
                        or coalesce(
                            jsonb_typeof(entry.step -> 'videoAssetVersion') = 'string',
                            false
                        )
                        or coalesce(
                            jsonb_typeof(entry.step -> 'videoFallbackText') = 'string',
                            false
                        )
                    )
                    and not (
                        coalesce(
                            jsonb_typeof(entry.step -> 'videoAssetId') = 'string',
                            false
                        )
                        and coalesce(
                            jsonb_typeof(entry.step -> 'videoAssetVersion') = 'string',
                            false
                        )
                        and coalesce(
                            jsonb_typeof(entry.step -> 'videoFallbackText') = 'string',
                            false
                        )
                    )
               )
        )
    end;
$$;

alter function bodeul.is_valid_guide_step_media_v1(jsonb) owner to bodeul_migration;
revoke all on function bodeul.is_valid_guide_step_media_v1(jsonb)
    from public, anon, authenticated, service_role;
grant execute on function bodeul.is_valid_guide_step_media_v1(jsonb)
    to bodeul_core_runtime, bodeul_admin_runtime;

comment on function bodeul.is_valid_guide_step_media_v1(jsonb) is
    '기존 가이드 단계에 선택적으로 붙는 영상 자산 식별자, 버전과 대체 안내의 JSON 형상 검증';

alter table bodeul.hospital_guides
    add constraint ck_hospital_guides_step_media_shape
        check (
            step_contract_version <> 1
            or bodeul.is_valid_guide_step_media_v1(steps)
        ) not valid;

alter table bodeul.hospital_guides
    validate constraint ck_hospital_guides_step_media_shape;

alter table bodeul.companion_sessions
    add constraint ck_companion_sessions_guide_media_shape
        check (
            guide_steps_snapshot is null
            or guide_step_contract_version is distinct from 1
            or bodeul.is_valid_guide_step_media_v1(guide_steps_snapshot)
        ) not valid;

alter table bodeul.companion_sessions
    validate constraint ck_companion_sessions_guide_media_shape;

comment on constraint ck_hospital_guides_step_media_shape
    on bodeul.hospital_guides is
    '영상 자산 ID, 버전과 대체 안내를 모두 비우거나 non-blank 문자열 세트로 저장';

comment on constraint ck_companion_sessions_guide_media_shape
    on bodeul.companion_sessions is
    '기존 4필드 snapshot을 유지하면서 선택 영상 메타데이터 형상을 검증';
