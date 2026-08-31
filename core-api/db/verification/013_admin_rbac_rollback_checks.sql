begin transaction;
set local role bodeul_migration;

do $$
declare
    v_retention_job_id uuid;
    v_nonzero_admin_counts_rejected boolean := false;
begin
    if to_regclass('bodeul.admin_role_assignments') is not null
        or to_regclass('bodeul.admin_break_glass_grants') is not null
        or to_regclass('bodeul.admin_access_audits') is not null then
        raise exception '관리자 RBAC 테이블이 롤백 후 남아 있습니다.';
    end if;

    if to_regprocedure('bodeul.resolve_admin_authorization(text)') is not null
        or to_regprocedure('bodeul.purge_expired_admin_access_audits(timestamptz,integer)') is not null then
        raise exception '관리자 RBAC 함수가 롤백 후 남아 있습니다.';
    end if;

    if exists (
        select 1
        from information_schema.triggers
        where event_object_schema = 'bodeul'
          and event_object_table = 'companion_session_assignment_audits'
          and trigger_name = 'companion_assignment_admin_role_guard'
    ) then
        raise exception '동행 배정 관리자 역할 트리거가 롤백 후 남아 있습니다.';
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'bodeul'
          and table_name = 'retention_job_runs'
          and column_name in ('admin_audit_candidates', 'admin_audits_deleted')
    ) then
        raise exception '관리자 감사 파기 집계 컬럼이 롤백 후 남아 있습니다.';
    end if;

    if pg_get_function_result('bodeul.retention_monthly_summary(date)'::regprocedure)
            ilike '%admin_audit%' then
        raise exception '월간 파기 집계의 V20 반환 계약이 롤백 후 남아 있습니다.';
    end if;

    v_retention_job_id := bodeul.begin_retention_job('DRY_RUN', now(), now());
    if not bodeul.finish_retention_job(
        v_retention_job_id,
        'COMPLETED',
        now(),
        jsonb_build_object(
            'postgresMessageCandidates', 0,
            'postgresAttachmentCandidates', 0,
            'postgresLocationCandidates', 0,
            'postgresLegalHoldSkips', 0,
            'firestoreMessageCandidates', 0,
            'firestoreAttachmentCandidates', 0,
            'firestoreLocationCandidates', 0,
            'firestoreLegalHoldSkips', 0,
            'managerDocumentCandidates', 0,
            'managerDocumentLegalHoldSkips', 0,
            'adminAuditCandidates', 0,
            'messagesRedacted', 0,
            'attachmentsDeleted', 0,
            'attachmentDeleteFailures', 0,
            'locationsDeleted', 0,
            'firestoreMessagesRedacted', 0,
            'firestoreAttachmentsDeleted', 0,
            'firestoreAttachmentDeleteFailures', 0,
            'firestoreLocationsCleared', 0,
            'managerDocumentsDeleted', 0,
            'managerDocumentDeleteFailures', 0,
            'adminAuditsDeleted', 0
        ),
        null
    ) then
        raise exception 'V20 Functions 집계 키를 포함한 롤백 호환 완료 기록에 실패했습니다.';
    end if;

    begin
        perform bodeul.finish_retention_job(
            v_retention_job_id,
            'COMPLETED',
            now(),
            jsonb_build_object(
                'postgresMessageCandidates', 0,
                'postgresAttachmentCandidates', 0,
                'postgresLocationCandidates', 0,
                'postgresLegalHoldSkips', 0,
                'firestoreMessageCandidates', 0,
                'firestoreAttachmentCandidates', 0,
                'firestoreLocationCandidates', 0,
                'firestoreLegalHoldSkips', 0,
                'managerDocumentCandidates', 0,
                'managerDocumentLegalHoldSkips', 0,
                'adminAuditCandidates', 1,
                'messagesRedacted', 0,
                'attachmentsDeleted', 0,
                'attachmentDeleteFailures', 0,
                'locationsDeleted', 0,
                'firestoreMessagesRedacted', 0,
                'firestoreAttachmentsDeleted', 0,
                'firestoreAttachmentDeleteFailures', 0,
                'firestoreLocationsCleared', 0,
                'managerDocumentsDeleted', 0,
                'managerDocumentDeleteFailures', 0,
                'adminAuditsDeleted', 1
            ),
            null
        );
    exception
        when sqlstate '22023' then
            v_nonzero_admin_counts_rejected := true;
    end;
    if not v_nonzero_admin_counts_rejected then
        raise exception '롤백 뒤 0이 아닌 관리자 감사 파기 집계가 거부되지 않았습니다.';
    end if;
end;
$$;

rollback;
