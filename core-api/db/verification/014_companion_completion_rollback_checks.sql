do $$
begin
    if to_regclass('bodeul.companion_session_artifacts') is not null
            or to_regclass('bodeul.companion_session_artifact_operations') is not null
            or to_regclass('bodeul.companion_completion_v18_chat_expiry_baseline') is not null
            or to_regclass('bodeul.companion_completion_v18_attachment_expiry_baseline') is not null
            or to_regclass('bodeul.companion_completion_v18_location_expiry_baseline') is not null
            or to_regclass('bodeul.companion_completion_v18_consent_expiry_baseline') is not null then
        raise exception 'V18 첨부 테이블 rollback이 완료되지 않았습니다.';
    end if;

    if (select expires_at
            from bodeul.companion_chat_messages
            where id = '60000000-0000-0000-0000-000000000001') is not null
            or (select expires_at
                from bodeul.companion_chat_attachments
                where id = '60000000-0000-0000-0000-000000000003') is not null
            or (select expires_at
                from bodeul.companion_session_locations
                where id = '60000000-0000-0000-0000-000000000004') is not null then
        raise exception 'V18 migration 전 실시간 데이터의 만료시각이 복원되지 않았습니다.';
    end if;

    if exists (
        select 1
        from bodeul.guardian_sharing_consents
        where id = '70000000-0000-0000-0000-000000000001'
          and (
              care_ended_at is not null
              or expires_at is distinct from '2026-09-10T01:00:00Z'::timestamptz
              or expiry_finalized
              or revoked_by_user_id is not null
              or revoked_at is not null
              or version <> 4
              or updated_at is distinct from '2026-08-28T01:00:00Z'::timestamptz
          )
    ) then
        raise exception 'V18 migration 전 보호자 동의 만료 상태가 복원되지 않았습니다.';
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

    if to_regprocedure('bodeul.guard_companion_chat_message_write()') is not null
            or to_regprocedure('bodeul.guard_companion_chat_attachment_write()') is not null
            or to_regprocedure('bodeul.guard_guardian_consent_care_boundary()') is not null
            or to_regprocedure('bodeul.finalize_guardian_consent_after_care_boundary()') is not null
            or exists (
                select 1
                from pg_trigger
                where tgname in (
                    'guard_companion_chat_message_write_before_insert',
                    'guard_companion_chat_attachment_write_before_insert'
                )
                  and not tgisinternal
            ) then
        raise exception 'V18 실시간 쓰기 guard rollback이 완료되지 않았습니다.';
    end if;

    if position('care_ended_at' in lower(pg_get_functiondef(
            'bodeul.record_companion_location(uuid,uuid,uuid,double precision,double precision,timestamp with time zone)'::regprocedure
        ))) > 0
            or position('care_ended_at' in lower(pg_get_functiondef(
                'bodeul.schedule_companion_realtime_expiry()'::regprocedure
            ))) > 0
            or position('care_ended_at' in lower(pg_get_functiondef(
                'bodeul.broadcast_companion_realtime_change()'::regprocedure
            ))) > 0 then
        raise exception 'V8 위치·보존 함수가 rollback 뒤 복원되지 않았습니다.';
    end if;

    if position('care_ended_at' in lower(pg_get_functiondef(
            'bodeul_realtime_auth.can_receive_companion_broadcast()'::regprocedure
        ))) > 0
            or position(
                'app_user.id in (session.manager_user_id, appointment.patient_user_id)'
                in lower(pg_get_functiondef(
                    'bodeul_realtime_auth.can_receive_companion_broadcast()'::regprocedure
                ))) = 0 then
        raise exception 'V17 Realtime 권한 helper가 rollback 뒤 복원되지 않았습니다.';
    end if;

    if obj_description(
            'bodeul_realtime_auth.can_receive_companion_broadcast()'::regprocedure,
            'pg_proc'
        ) is distinct from
            'Firebase JWT와 세션 참여 관계를 확인하고 보호자 Broadcast는 연결 권한 캐시 위험 때문에 거부하는 Realtime helper' then
        raise exception 'V17 Realtime 권한 helper 설명이 rollback 뒤 복원되지 않았습니다.';
    end if;

    if not exists (
        select 1
        from pg_trigger
        where tgname = 'schedule_companion_realtime_expiry_after_session_end'
          and not tgisinternal
          and position('update of current_status on' in lower(pg_get_triggerdef(oid))) > 0
          and position('care_ended_at' in lower(pg_get_triggerdef(oid))) = 0
    ) then
        raise exception 'V8 current_status 전용 보존 trigger가 복원되지 않았습니다.';
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
