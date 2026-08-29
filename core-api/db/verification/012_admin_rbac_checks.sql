begin transaction;
set local role bodeul_migration;

do $$
begin
    if to_regclass('bodeul.admin_role_assignments') is null
        or to_regclass('bodeul.admin_break_glass_grants') is null
        or to_regclass('bodeul.admin_access_audits') is null
        or to_regclass('bodeul.ux_admin_break_glass_unrevoked') is null then
        raise exception '관리자 RBAC 테이블이 모두 생성되지 않았습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.resolve_admin_authorization(text)',
        'EXECUTE'
    ) then
        raise exception '관리자 런타임에 인가 조회 권한이 없습니다.';
    end if;

    if has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.resolve_admin_authorization(text)',
        'EXECUTE'
    ) then
        raise exception '코어 런타임에 관리자 인가 조회 권한이 노출되었습니다.';
    end if;

    if has_table_privilege(
        'bodeul_admin_runtime',
        'bodeul.admin_access_audits',
        'INSERT'
    ) then
        raise exception '관리자 런타임이 감사 테이블을 직접 수정할 수 있습니다.';
    end if;

    if has_table_privilege(
        'bodeul_core_runtime',
        'bodeul.admin_role_assignments',
        'SELECT'
    ) then
        raise exception '코어 런타임에 관리자 역할 테이블이 노출되었습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_retention_runtime',
        'bodeul.purge_expired_admin_access_audits(timestamptz,integer)',
        'EXECUTE'
    ) or has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.purge_expired_admin_access_audits(timestamptz,integer)',
        'EXECUTE'
    ) or has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.purge_expired_admin_access_audits(timestamptz,integer)',
        'EXECUTE'
    ) then
        raise exception '관리자 감사 파기 함수의 런타임 권한 경계가 잘못되었습니다.';
    end if;

    if not exists (
        select 1
        from information_schema.triggers
        where event_object_schema = 'bodeul'
          and event_object_table = 'companion_session_assignment_audits'
          and trigger_name = 'companion_assignment_admin_role_guard'
          and event_manipulation = 'INSERT'
          and action_timing = 'BEFORE'
    ) then
        raise exception '동행 배정 관리자 역할 검증 트리거가 없습니다.';
    end if;

    if exists (
        select 1
        from pg_proc procedure
        join pg_namespace namespace on namespace.oid = procedure.pronamespace
        join pg_roles owner_role on owner_role.oid = procedure.proowner
        where namespace.nspname = 'bodeul'
          and procedure.proname in (
              'resolve_admin_authorization',
              'record_admin_access_audit',
              'preview_expired_admin_access_audits',
              'purge_expired_admin_access_audits',
              'set_admin_role_assignment',
              'revoke_admin_role_assignment',
              'grant_admin_break_glass',
              'revoke_admin_break_glass',
              'list_admin_role_assignments',
              'list_admin_access_audits',
              'enforce_assignment_actor_admin_role'
          )
          and owner_role.rolname <> 'bodeul_migration'
    ) then
        raise exception '관리자 보안 함수 소유자가 마이그레이션 역할이 아닙니다.';
    end if;
end;
$$;

do $$
declare
    v_super_one constant uuid := '00000000-0000-0000-0000-000000003491';
    v_super_two constant uuid := '00000000-0000-0000-0000-000000003492';
    v_operations constant uuid := '00000000-0000-0000-0000-000000003493';
    v_developer constant uuid := '00000000-0000-0000-0000-000000003494';
    v_first_grant uuid;
    v_second_grant uuid;
    v_audit_id uuid;
    v_retry_audit_id uuid;
    v_operation_id constant uuid := '00000000-0000-0000-0000-000000003499';
    v_revoked_actor_operation_id constant uuid := '00000000-0000-0000-0000-000000003498';
    v_snapshot_scope_operation_id constant uuid := '00000000-0000-0000-0000-000000003497';
    v_invalid_snapshot_operation_id constant uuid := '00000000-0000-0000-0000-000000003496';
    v_invalid_snapshot_hash_operation_id constant uuid := '00000000-0000-0000-0000-000000003495';
    v_retention_job_id uuid;
    v_legacy_retention_job_id uuid;
    v_legacy_retention_counts jsonb;
    v_monthly_admin_audit_candidates bigint;
    v_monthly_admin_audits_deleted bigint;
    v_count bigint;
    v_rejected boolean;
begin
    insert into bodeul.app_users (id, firebase_uid, role) values
        (v_super_one, 'verify-admin-rbac-super-one', 'ADMIN'),
        (v_super_two, 'verify-admin-rbac-super-two', 'ADMIN'),
        (v_operations, 'verify-admin-rbac-operations', 'ADMIN'),
        (v_developer, 'verify-admin-rbac-developer', 'ADMIN');

    insert into bodeul.admin_role_assignments (
        admin_user_id, admin_role, granted_by_admin_user_id, grant_reason
    ) values
        (v_super_one, 'SUPER_ADMIN', v_super_one, '검증용 최초 최고 관리자 설정'),
        (v_super_two, 'SUPER_ADMIN', v_super_one, '검증용 보조 최고 관리자 설정'),
        (v_operations, 'OPERATIONS', v_super_one, '검증용 운영 관리자 설정'),
        (v_developer, 'DEVELOPER', v_super_one, '검증용 개발 관리자 설정');

    if not exists (
        select 1
        from bodeul.resolve_admin_authorization('verify-admin-rbac-operations') authorization
        where authorization.app_user_id = v_operations
          and authorization.app_role = 'ADMIN'
          and authorization.admin_role = 'OPERATIONS'
    ) then
        raise exception '관리자 세부 역할 조회 결과가 올바르지 않습니다.';
    end if;

    perform bodeul.record_admin_access_audit(
        v_developer, 'RAW_VIEW', 'VERIFY_RESOURCE', 'denied-attempt',
        '검증용 원문 접근 거부 사유입니다.', 'DENIED', '{}'::jsonb
    );

    v_audit_id := bodeul.record_admin_access_audit(
        v_operations, 'VIEW', 'VERIFY_RESOURCE', 'idempotent-audit',
        '', 'ALLOWED', '{"source":"verification"}'::jsonb, v_operation_id
    );
    v_retry_audit_id := bodeul.record_admin_access_audit(
        v_operations, 'VIEW', 'VERIFY_RESOURCE', 'idempotent-audit',
        '', 'ALLOWED', '{"source":"verification"}'::jsonb, v_operation_id
    );
    if v_audit_id is distinct from v_retry_audit_id then
        raise exception '같은 작업 ID의 감사 재시도가 같은 감사 ID를 반환하지 않았습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.record_admin_access_audit(
            v_operations, 'VIEW', 'VERIFY_RESOURCE', 'different-resource',
            '', 'ALLOWED', '{"source":"verification"}'::jsonb, v_operation_id
        );
    exception
        when sqlstate '22023' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '같은 감사 작업 ID의 다른 내용 재사용이 거부되지 않았습니다.';
    end if;

    perform bodeul.revoke_admin_role_assignment(
        v_operations, v_super_one, '검증용 운영 관리자 역할 회수입니다.'
    );
    perform bodeul.set_admin_role_assignment(
        v_operations, 'DEVELOPER', v_super_one, '검증용 개발 관리자 역할 재부여입니다.'
    );
    v_audit_id := bodeul.record_admin_access_audit(
        v_operations, 'UPDATE', 'MANAGER_REVIEW', 'revoked-actor-review',
        '', 'ALLOWED', jsonb_build_object(
            'status', 'APPROVED',
            'operationId', v_revoked_actor_operation_id,
            'actorAdminRole', 'OPERATIONS',
            'payloadHash', repeat('a', 64)
        ), v_revoked_actor_operation_id
    );
    if not exists (
        select 1
        from bodeul.admin_access_audits audit
        where audit.id = v_audit_id
          and audit.actor_admin_role = 'OPERATIONS'
          and audit.operation_id = v_revoked_actor_operation_id
    ) then
        raise exception '역할 회수 뒤 심사 outbox 감사를 당시 역할로 기록하지 못했습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.record_admin_access_audit(
            v_operations, 'RAW_VIEW', 'MANAGER_DOCUMENT', 'snapshot-scope-check',
            '검증용 snapshot 범위 제한 확인 사유입니다.', 'ALLOWED', jsonb_build_object(
                'actorAdminRole', 'SUPER_ADMIN'
            ), v_snapshot_scope_operation_id
        );
    exception
        when sqlstate '42501' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '심사 outbox 역할 snapshot이 다른 민감 감사에 적용되었습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.record_admin_access_audit(
            v_operations, 'UPDATE', 'MANAGER_REVIEW', 'invalid-snapshot-role',
            '', 'ALLOWED', jsonb_build_object(
                'status', 'APPROVED',
                'operationId', v_invalid_snapshot_operation_id,
                'actorAdminRole', 'DEVELOPER',
                'payloadHash', repeat('b', 64)
            ), v_invalid_snapshot_operation_id
        );
    exception
        when sqlstate '22023' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '개발 관리자 역할 snapshot이 심사 성공 감사에 허용되었습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.record_admin_access_audit(
            v_operations, 'UPDATE', 'MANAGER_REVIEW', 'invalid-snapshot-hash',
            '', 'ALLOWED', jsonb_build_object(
                'status', 'APPROVED',
                'operationId', v_invalid_snapshot_hash_operation_id,
                'actorAdminRole', 'OPERATIONS'
            ), v_invalid_snapshot_hash_operation_id
        );
    exception
        when sqlstate '22023' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception 'payload hash가 없는 심사 역할 snapshot이 허용되었습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.record_admin_access_audit(
            v_developer, 'RAW_VIEW', 'VERIFY_RESOURCE', 'allowed-attempt',
            '검증용 원문 접근 허용 사유입니다.', 'ALLOWED', '{}'::jsonb
        );
    exception
        when sqlstate '42501' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '개발 관리자 원문 접근이 거부되지 않았습니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.grant_admin_break_glass(
            v_super_one, v_super_one, '검증용 본인 승인 거부 사유입니다.', 10
        );
    exception
        when sqlstate '42501' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '긴급 권한 본인 승인이 거부되지 않았습니다.';
    end if;

    v_first_grant := bodeul.grant_admin_break_glass(
        v_super_two, v_super_one, '검증용 첫 번째 긴급 접근 승인입니다.', 10
    );
    v_second_grant := bodeul.grant_admin_break_glass(
        v_super_two, v_super_one, '검증용 두 번째 긴급 접근 승인입니다.', 20
    );

    select count(*) into v_count
    from bodeul.admin_break_glass_grants grant_record
    where grant_record.admin_user_id = v_super_two
      and grant_record.revoked_at is null;
    if v_count <> 1 or v_first_grant = v_second_grant then
        raise exception '재발급 후 미회수 긴급 권한이 하나로 유지되지 않았습니다.';
    end if;

    if bodeul.revoke_admin_break_glass(
        v_first_grant, v_super_one, '검증용 이전 긴급 접근 ID 회수 거부 사유입니다.'
    ) then
        raise exception '이전 긴급 접근 ID가 현재 활성 권한을 회수했습니다.';
    end if;

    select count(*) into v_count
    from bodeul.admin_break_glass_grants grant_record
    where grant_record.id = v_second_grant
      and grant_record.revoked_at is null;
    if v_count <> 1 then
        raise exception '이전 긴급 접근 ID 확인 뒤 현재 활성 권한이 유지되지 않았습니다.';
    end if;

    if not bodeul.revoke_admin_break_glass(
        v_second_grant, v_super_one, '검증용 긴급 접근 전체 회수 사유입니다.'
    ) then
        raise exception '긴급 접근 권한 회수 결과가 올바르지 않습니다.';
    end if;

    select count(*) into v_count
    from bodeul.admin_break_glass_grants grant_record
    where grant_record.admin_user_id = v_super_two
      and grant_record.revoked_at is null;
    if v_count <> 0 then
        raise exception '긴급 접근 회수 후 미회수 권한이 남아 있습니다.';
    end if;

    perform bodeul.revoke_admin_role_assignment(
        v_super_two, v_super_one, '검증용 보조 최고 관리자 권한 회수입니다.'
    );
    v_rejected := false;
    begin
        perform bodeul.revoke_admin_role_assignment(
            v_super_one, v_super_one, '검증용 마지막 최고 관리자 회수 시도입니다.'
        );
    exception
        when sqlstate 'P0001' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '마지막 최고 관리자 권한 회수가 거부되지 않았습니다.';
    end if;

    insert into bodeul.admin_access_audits (
        actor_admin_user_id, actor_admin_role, action, resource_type,
        resource_id, reason, outcome, created_at
    ) values (
        v_super_one, 'SUPER_ADMIN', 'VIEW', 'VERIFY_RESOURCE',
        'expired-audit', '', 'ALLOWED', now() - interval '1 year 1 day'
    );

    if bodeul.preview_expired_admin_access_audits(now()) <> 1 then
        raise exception '1년 경과 관리자 감사 후보 계산이 올바르지 않습니다.';
    end if;
    if bodeul.purge_expired_admin_access_audits(now(), 500) <> 1 then
        raise exception '1년 경과 관리자 감사 파기 결과가 올바르지 않습니다.';
    end if;

    v_retention_job_id := bodeul.begin_retention_job('DRY_RUN', now(), now());
    if not bodeul.finish_retention_job(
        v_retention_job_id,
        'COMPLETED',
        now(),
        jsonb_build_object(
            'postgresMessageCandidates', 1,
            'postgresAttachmentCandidates', 2,
            'postgresLocationCandidates', 3,
            'postgresLegalHoldSkips', 4,
            'firestoreMessageCandidates', 5,
            'firestoreAttachmentCandidates', 6,
            'firestoreLocationCandidates', 7,
            'firestoreLegalHoldSkips', 8,
            'managerDocumentCandidates', 9,
            'managerDocumentLegalHoldSkips', 10,
            'adminAuditCandidates', 11,
            'messagesRedacted', 12,
            'attachmentsDeleted', 13,
            'attachmentDeleteFailures', 14,
            'locationsDeleted', 15,
            'firestoreMessagesRedacted', 16,
            'firestoreAttachmentsDeleted', 17,
            'firestoreAttachmentDeleteFailures', 18,
            'firestoreLocationsCleared', 19,
            'managerDocumentsDeleted', 20,
            'managerDocumentDeleteFailures', 21,
            'adminAuditsDeleted', 22
        ),
        null
    ) then
        raise exception 'V20 파기 집계 22개 키를 완료 기록에 반영하지 못했습니다.';
    end if;

    if not exists (
        select 1
        from bodeul.retention_job_runs run
        where run.id = v_retention_job_id
          and run.admin_audit_candidates = 11
          and run.admin_audits_deleted = 22
    ) then
        raise exception '관리자 감사 후보·삭제 집계가 실행 기록에 저장되지 않았습니다.';
    end if;

    select summary.admin_audit_candidates, summary.admin_audits_deleted
    into v_monthly_admin_audit_candidates, v_monthly_admin_audits_deleted
    from bodeul.retention_monthly_summary(date_trunc('month', now())::date) summary;
    if v_monthly_admin_audit_candidates < 11 or v_monthly_admin_audits_deleted < 22 then
        raise exception '월간 파기 집계에 관리자 감사 후보·삭제 수가 반영되지 않았습니다.';
    end if;

    v_legacy_retention_counts := jsonb_build_object(
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
        'messagesRedacted', 0,
        'attachmentsDeleted', 0,
        'attachmentDeleteFailures', 0,
        'locationsDeleted', 0,
        'firestoreMessagesRedacted', 0,
        'firestoreAttachmentsDeleted', 0,
        'firestoreAttachmentDeleteFailures', 0,
        'firestoreLocationsCleared', 0,
        'managerDocumentsDeleted', 0,
        'managerDocumentDeleteFailures', 0
    );
    v_legacy_retention_job_id := bodeul.begin_retention_job('DRY_RUN', now(), now());
    if not bodeul.finish_retention_job(
        v_legacy_retention_job_id,
        'COMPLETED',
        now(),
        v_legacy_retention_counts,
        null
    ) then
        raise exception 'V20에서 기존 Functions 20개 집계 키 완료 기록에 실패했습니다.';
    end if;

    if not exists (
        select 1
        from bodeul.retention_job_runs run
        where run.id = v_legacy_retention_job_id
          and run.admin_audit_candidates = 0
          and run.admin_audits_deleted = 0
    ) then
        raise exception '기존 Functions 집계의 관리자 감사 기본값이 0이 아닙니다.';
    end if;

    v_rejected := false;
    begin
        perform bodeul.finish_retention_job(
            v_legacy_retention_job_id,
            'COMPLETED',
            now(),
            v_legacy_retention_counts || jsonb_build_object('unexpectedCount', 1),
            null
        );
    exception
        when sqlstate '22023' then v_rejected := true;
    end;
    if not v_rejected then
        raise exception '알 수 없는 파기 집계 키가 거부되지 않았습니다.';
    end if;
end;
$$;

rollback;
