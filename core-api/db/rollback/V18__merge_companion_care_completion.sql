begin;

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
        from bodeul.companion_completion_v18_baseline as baseline
        join bodeul.companion_sessions as session
          on session.id = baseline.companion_session_id
        where session.current_status <> 'COMPLETED'
           or session.completed_at is distinct from baseline.expected_completed_at
           or session.care_ended_at is distinct from baseline.expected_care_ended_at
           or session.manager_journal <> ''
           or session.report_generation_status <> baseline.expected_report_generation_status
           or session.report_generation_attempts <> baseline.expected_report_generation_attempts
           or session.report_generation_last_error <> baseline.expected_report_generation_last_error
           or session.report_generation_updated_at
                is distinct from baseline.expected_report_generation_updated_at
    ) or exists (
        select 1
        from bodeul.companion_sessions as session
        where not exists (
                select 1
                from bodeul.companion_completion_v18_baseline as baseline
                where baseline.companion_session_id = session.id
            )
          and (
              session.current_status = 'CARE_ENDED'
              or session.care_ended_at is not null
              or btrim(session.manager_journal) <> ''
              or session.report_generation_status <> 'NOT_REQUESTED'
              or session.report_generation_attempts <> 0
              or btrim(session.report_generation_last_error) <> ''
              or session.report_generation_updated_at is not null
          )
    ) then
        raise exception using
            errcode = 'P0001',
            message = 'V18 rollback을 중단합니다. 동행 종료 시각, 매니저 일지와 리포트 생성 상태를 export한 뒤 V18 완료 필드를 기본값으로 정리하고 다시 실행해 주세요.';
    end if;
end;
$$;

drop table if exists bodeul.companion_session_artifact_operations;
drop table if exists bodeul.companion_session_artifacts;

alter table bodeul.companion_sessions
    drop constraint ck_companion_sessions_completion_timestamps;

update bodeul.companion_sessions as session
set completed_at = baseline.original_completed_at
from bodeul.companion_completion_v18_baseline as baseline
where session.id = baseline.companion_session_id;

alter table bodeul.companion_sessions
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

drop table bodeul.companion_completion_v18_baseline;

commit;
