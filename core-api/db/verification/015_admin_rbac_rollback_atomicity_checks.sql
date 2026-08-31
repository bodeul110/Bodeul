begin;
set local role bodeul_migration;

do $$
begin
    if to_regclass('bodeul.verify_v20_rollback_dependency') is null then
        raise exception '실패를 유도한 롤백 의존 view가 유지되지 않았습니다.';
    end if;

    if to_regclass('bodeul.admin_role_assignments') is null
            or to_regclass('bodeul.admin_break_glass_grants') is null
            or to_regclass('bodeul.admin_access_audits') is null then
        raise exception '실패한 V20 롤백이 관리자 RBAC 테이블을 일부 제거했습니다.';
    end if;

    if (
        select count(*)
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'retention_job_runs'
          and column_name in ('admin_audit_candidates', 'admin_audits_deleted')
    ) <> 2 then
        raise exception '실패한 V20 롤백이 관리자 감사 파기 집계 컬럼을 일부 제거했습니다.';
    end if;

    if to_regprocedure('bodeul.resolve_admin_authorization(text)') is null
            or to_regprocedure(
                'bodeul.record_admin_access_audit(uuid,text,text,text,text,text,jsonb,uuid)'
            ) is null then
        raise exception '실패한 V20 롤백이 관리자 보안 함수를 일부 제거했습니다.';
    end if;

    if to_regprocedure('bodeul.retention_monthly_summary(date)') is null
            or position(
                'admin_audit_candidates'
                in pg_get_function_result(to_regprocedure('bodeul.retention_monthly_summary(date)'))
            ) = 0 then
        raise exception '실패한 V20 롤백이 retention 월간 집계 함수 계약을 변경했습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.resolve_admin_authorization(text)',
        'EXECUTE'
    ) then
        raise exception '실패한 V20 롤백이 관리자 런타임 함수 권한을 회수했습니다.';
    end if;

    if not exists (
        select 1
        from information_schema.triggers
        where event_object_schema = 'bodeul'
          and event_object_table = 'companion_session_assignment_audits'
          and trigger_name = 'companion_assignment_admin_role_guard'
    ) then
        raise exception '실패한 V20 롤백이 관리자 역할 검증 트리거를 제거했습니다.';
    end if;
end;
$$;

drop view bodeul.verify_v20_rollback_dependency;

commit;
