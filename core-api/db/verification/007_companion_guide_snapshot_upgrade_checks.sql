do $$
begin
    if not exists (
        select 1
        from bodeul.hospital_guides
        where id = '00000000-0000-0000-0000-000000000120'
          and revision = 1
          and step_contract_version = 0
          and jsonb_array_length(steps) = 7
          and not (steps -> 0 ? 'code')
    ) then
        raise exception '기존 코드 없는 병원 가이드가 보존되지 않았습니다.';
    end if;

    if not exists (
        select 1
        from bodeul.companion_sessions
        where id = '00000000-0000-0000-0000-000000000131'
          and guide_snapshot_source = 'LEGACY_CORE_7_V1'
          and guide_id is null
          and guide_revision is null
          and guide_step_contract_version is null
          and jsonb_array_length(guide_steps_snapshot) = 7
          and guide_steps_snapshot -> 0 ->> 'code' = 'LEGACY_CORE_PATIENT_CONTACT'
          and guide_steps_snapshot -> 6 ->> 'code' = 'LEGACY_CORE_RETURN_AND_CLOSE'
          and not (guide_steps_snapshot @> '[{"code":"MEETING_CONFIRMATION"}]'::jsonb)
    ) then
        raise exception '기존 Core API 7단계 세션이 legacy snapshot으로 보존되지 않았습니다.';
    end if;

    if not exists (
        select 1
        from bodeul.companion_sessions
        where id = '00000000-0000-0000-0000-000000000132'
          and guide_snapshot_source = 'UNRESOLVED_LEGACY'
          and guide_id is null
          and guide_revision is null
          and guide_step_contract_version is null
          and guide_steps_snapshot is null
    ) then
        raise exception 'Firestore import 세션에 근거 없는 단계 코드가 추론되었습니다.';
    end if;
end;
$$;
