do $$
begin
    if exists (select 1 from bodeul.companion_session_artifacts limit 1)
            or exists (
                select 1
                from bodeul.companion_session_artifact_operations
                limit 1
            ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. 첨부 경로와 operation ledger를 export하고 Storage 원본을 정리한 뒤 두 테이블의 행을 삭제해 주세요.';
    end if;

    if exists (
        select 1
        from bodeul.companion_sessions
        where current_status = 'CARE_ENDED'
           or care_ended_at is not null
           or btrim(manager_journal) <> ''
           or report_generation_status <> 'NOT_REQUESTED'
           or report_generation_attempts <> 0
           or btrim(report_generation_last_error) <> ''
           or report_generation_updated_at is not null
    ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. 동행 종료 시각, 매니저 일지와 리포트 생성 상태를 export한 뒤 V18 완료 필드를 기본값으로 정리하고 다시 실행해 주세요.';
    end if;
end;
$$;

drop table if exists bodeul.companion_session_artifact_operations;
drop table if exists bodeul.companion_session_artifacts;

update bodeul.companion_sessions
set current_status = 'PAYMENT',
    updated_at = now(),
    version = version + 1
where current_status = 'CARE_ENDED';

alter table bodeul.companion_sessions
    drop constraint ck_companion_sessions_completion_timestamps,
    drop constraint ck_companion_sessions_report_generation_attempts,
    drop constraint ck_companion_sessions_report_generation_status,
    drop constraint ck_companion_sessions_manager_journal,
    drop constraint ck_companion_sessions_status;

alter table bodeul.companion_sessions
    drop column report_generation_updated_at,
    drop column report_generation_last_error,
    drop column report_generation_attempts,
    drop column report_generation_status,
    drop column manager_journal,
    drop column care_ended_at,
    add constraint ck_companion_sessions_status
        check (current_status in (
            'READY', 'MEETING', 'WAITING', 'IN_TREATMENT',
            'PAYMENT', 'CANCELED', 'COMPLETED'
        ));
