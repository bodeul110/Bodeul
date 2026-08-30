do $$
declare
    v_care_ended_at timestamptz;
    v_report_status text;
    v_message_expires_at timestamptz;
    v_attachment_expires_at timestamptz;
    v_location_expires_at timestamptz;
    v_consent_care_ended_at timestamptz;
    v_consent_expires_at timestamptz;
    v_consent_expiry_finalized boolean;
    v_consent_version bigint;
    v_consent_updated_at timestamptz;
begin
    select care_ended_at, report_generation_status
    into v_care_ended_at, v_report_status
    from bodeul.companion_sessions
    where id = '30000000-0000-0000-0000-000000000001';

    if v_care_ended_at is null or v_report_status <> 'FAILED' then
        raise exception 'V18 legacy COMPLETED 백필 결과가 올바르지 않습니다.';
    end if;

    select expires_at into v_message_expires_at
    from bodeul.companion_chat_messages
    where id = '60000000-0000-0000-0000-000000000001';
    select expires_at into v_attachment_expires_at
    from bodeul.companion_chat_attachments
    where id = '60000000-0000-0000-0000-000000000003';
    select expires_at into v_location_expires_at
    from bodeul.companion_session_locations
    where id = '60000000-0000-0000-0000-000000000004';

    if v_message_expires_at is distinct from v_care_ended_at + interval '180 days'
            or v_attachment_expires_at is distinct from v_care_ended_at + interval '30 days'
            or v_location_expires_at is distinct from v_care_ended_at + interval '24 hours' then
        raise exception 'CARE_ENDED 기준 실시간 데이터 보존 시작점이 올바르지 않습니다.';
    end if;

    select care_ended_at, expires_at, expiry_finalized, version, updated_at
    into v_consent_care_ended_at, v_consent_expires_at, v_consent_expiry_finalized,
         v_consent_version, v_consent_updated_at
    from bodeul.guardian_sharing_consents
    where id = '70000000-0000-0000-0000-000000000001';

    if v_consent_care_ended_at is distinct from v_care_ended_at
            or v_consent_expires_at is distinct from v_care_ended_at + interval '7 days'
            or not v_consent_expiry_finalized
            or v_consent_version <> 5
            or v_consent_updated_at <= '2026-08-28T01:00:00Z'::timestamptz then
        raise exception '보호자 정보공유 동의 만료가 CARE_ENDED 기준으로 확정되지 않았습니다.';
    end if;
end;
$$;

do $$
begin
    begin
        insert into bodeul.appointment_requests (
            id, firestore_id, patient_user_id, guardian_user_id, manager_user_id,
            requester_user_id, requester_role, hospital_name, department_name,
            appointment_at, appointment_at_epoch_millis, appointment_date_key,
            mobility_support_code, trip_type_code, manager_gender_preference_code,
            status, payment_method_code, coupon_code, payment_status_code, created_at
        )
        select
            '20000000-0000-0000-0000-000000000002',
            'v18-active-appointment', patient_user_id, guardian_user_id, manager_user_id,
            requester_user_id, requester_role, hospital_name, department_name,
            appointment_at + interval '1 day', appointment_at_epoch_millis + 86400000,
            '2026-08-30', mobility_support_code, trip_type_code,
            manager_gender_preference_code, 'IN_PROGRESS', payment_method_code,
            coupon_code, payment_status_code, created_at + interval '1 day'
        from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000001';

        begin
            insert into bodeul.guardian_sharing_consents (
                id, appointment_request_id, patient_user_id, guardian_user_id,
                scopes, policy_version, granted_by_user_id,
                adult_self_declared_at, granted_at, expires_at,
                care_ended_at, expiry_finalized
            ) values (
                '70000000-0000-0000-0000-000000000002',
                '20000000-0000-0000-0000-000000000002',
                '10000000-0000-0000-0000-000000000001',
                '10000000-0000-0000-0000-000000000003',
                '["APPOINTMENT", "CHAT"]'::jsonb,
                'adult-guardian-sharing-v1',
                '10000000-0000-0000-0000-000000000001',
                now(), now(), now() + interval '7 days', now(), true
            );
            raise exception '새 동의를 확정 상태로 직접 생성할 수 있습니다.';
        exception when sqlstate '55000' then
            null;
        end;

        insert into bodeul.guardian_sharing_consents (
            id, appointment_request_id, patient_user_id, guardian_user_id,
            scopes, policy_version, granted_by_user_id,
            adult_self_declared_at, granted_at, expires_at
        ) values (
            '70000000-0000-0000-0000-000000000002',
            '20000000-0000-0000-0000-000000000002',
            '10000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000003',
            '["APPOINTMENT", "CHAT"]'::jsonb,
            'adult-guardian-sharing-v1',
            '10000000-0000-0000-0000-000000000001',
            now(), now(), now() + interval '8 days'
        );

        begin
            update bodeul.guardian_sharing_consents
            set care_ended_at = now(),
                expires_at = now() + interval '7 days',
                expiry_finalized = true,
                version = version + 1,
                updated_at = now()
            where id = '70000000-0000-0000-0000-000000000002';
            raise exception '진행 중 동의 만료의 조기 확정이 허용되었습니다.';
        exception when sqlstate '55000' then
            null;
        end;

        begin
            update bodeul.guardian_sharing_consents
            set version = version + 10,
                updated_at = now()
            where id = '70000000-0000-0000-0000-000000000001';
            raise exception '확정된 동의 버전의 임의 변경이 허용되었습니다.';
        exception when sqlstate '55000' then
            null;
        end;

        raise exception '검증용 활성 예약과 동의를 되돌립니다.' using errcode = 'P0002';
    exception when sqlstate 'P0002' then
        null;
    end;
end;
$$;

update bodeul.companion_sessions
set current_status = 'PAYMENT'
where id = '30000000-0000-0000-0000-000000000001';

do $$
begin
    begin
        insert into bodeul.guardian_sharing_consents (
            id, appointment_request_id, patient_user_id, guardian_user_id,
            scopes, policy_version, granted_by_user_id,
            adult_self_declared_at, granted_at, expires_at
        ) values (
            '70000000-0000-0000-0000-000000000002',
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000003',
            '["APPOINTMENT", "CHAT", "ATTACHMENT"]'::jsonb,
            'adult-guardian-sharing-v1',
            '10000000-0000-0000-0000-000000000001',
            now(),
            now(),
            now() + interval '8 days'
        )
        on conflict (appointment_request_id) do update
        set scopes = excluded.scopes,
            granted_at = excluded.granted_at,
            expires_at = excluded.expires_at,
            care_ended_at = null,
            expiry_finalized = false;
        raise exception 'CARE_ENDED 뒤 보호자 동의 재부여가 허용되었습니다.';
    exception when sqlstate '55000' then
        null;
    end;

    begin
        update bodeul.guardian_sharing_consents
        set care_ended_at = null,
            expires_at = now() + interval '8 days',
            expiry_finalized = false
        where id = '70000000-0000-0000-0000-000000000001';
        raise exception '확정된 보호자 동의 만료 경계 초기화가 허용되었습니다.';
    exception when sqlstate '55000' then
        null;
    end;

    begin
        update bodeul.guardian_sharing_consents
        set appointment_request_id = '20000000-0000-0000-0000-000000000002',
            created_at = created_at + interval '1 second'
        where id = '70000000-0000-0000-0000-000000000001';
        raise exception '확정된 보호자 동의의 예약 연결 변경이 허용되었습니다.';
    exception when sqlstate '55000' then
        null;
    end;

    begin
        begin
            update bodeul.guardian_sharing_consents
            set revoked_by_user_id = patient_user_id,
                revoked_at = now(),
                version = version + 1,
                updated_at = now()
            where id = '70000000-0000-0000-0000-000000000001';

            if not exists (
                select 1
                from bodeul.guardian_sharing_consents
                where id = '70000000-0000-0000-0000-000000000001'
                  and revoked_at is not null
                  and expiry_finalized
            ) then
                raise exception '종료 뒤 보호자 동의 철회가 반영되지 않았습니다.';
            end if;

            begin
                update bodeul.guardian_sharing_consents
                set revoked_by_user_id = null,
                    revoked_at = null,
                    version = version + 1,
                    updated_at = now()
                where id = '70000000-0000-0000-0000-000000000001';
                raise exception '확정된 보호자 동의 철회 해제가 허용되었습니다.';
            exception when sqlstate '55000' then
                null;
            end;

            raise exception '검증용 철회 변경을 되돌립니다.' using errcode = 'P0002';
        exception when sqlstate 'P0002' then
            null;
        end;
    end;
end;
$$;

do $$
begin
    begin
        insert into bodeul.companion_chat_messages (
            companion_session_id, client_message_id, sender_user_id,
            sender_role, body, sent_at
        ) values (
            '30000000-0000-0000-0000-000000000001',
            '60000000-0000-0000-0000-000000000007',
            '10000000-0000-0000-0000-000000000001',
            'PATIENT',
            '종료 후 차단되어야 하는 메시지',
            now()
        );
        raise exception 'care_ended_at 이후 채팅 저장이 허용되었습니다.';
    exception when insufficient_privilege then
        null;
    end;

    begin
        insert into bodeul.companion_chat_attachments (
            chat_message_id, storage_path, file_name, content_type, size_bytes
        ) values (
            '60000000-0000-0000-0000-000000000001',
            'companion-chat-attachments/v18/blocked-after-care-end.png',
            'blocked-after-care-end.png',
            'image/png',
            8
        );
        raise exception 'care_ended_at 이후 첨부 저장이 허용되었습니다.';
    exception when insufficient_privilege then
        null;
    end;

    begin
        perform bodeul.record_companion_location(
            '30000000-0000-0000-0000-000000000001',
            '60000000-0000-0000-0000-000000000006',
            '10000000-0000-0000-0000-000000000002',
            37.5665,
            126.9780,
            now()
        );
        raise exception 'care_ended_at 이후 위치 저장이 허용되었습니다.';
    exception when insufficient_privilege then
        null;
    end;
end;
$$;

update bodeul.companion_sessions
set current_status = 'COMPLETED'
where id = '30000000-0000-0000-0000-000000000001';

do $$
declare
    v_care_ended_at timestamptz;
begin
    select care_ended_at into v_care_ended_at
    from bodeul.companion_sessions
    where id = '30000000-0000-0000-0000-000000000001';

    if (select expires_at from bodeul.companion_chat_messages
            where id = '60000000-0000-0000-0000-000000000001')
                is distinct from v_care_ended_at + interval '180 days'
            or (select expires_at from bodeul.companion_chat_attachments
                where id = '60000000-0000-0000-0000-000000000003')
                is distinct from v_care_ended_at + interval '30 days'
            or (select expires_at from bodeul.companion_session_locations
                where id = '60000000-0000-0000-0000-000000000004')
                is distinct from v_care_ended_at + interval '24 hours' then
        raise exception 'COMPLETED 전환이 CARE_ENDED 기준 보존 시각을 덮어썼습니다.';
    end if;
end;
$$;

insert into bodeul.companion_session_artifact_operations (
    companion_session_id,
    purpose,
    client_request_id,
    payload_fingerprint,
    result_revision
) values (
    '30000000-0000-0000-0000-000000000001',
    'PAYMENT_EVIDENCE',
    '40000000-0000-0000-0000-000000000001',
    repeat('a', 64),
    1
);

insert into bodeul.companion_session_artifacts (
    id,
    companion_session_id,
    purpose,
    client_request_id,
    item_order,
    storage_path,
    file_name,
    content_type,
    size_bytes,
    sha256,
    uploaded_by_user_id
) values (
    '50000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    'PAYMENT_EVIDENCE',
    '40000000-0000-0000-0000-000000000001',
    0,
    'companion-session-artifacts/v18/payment.pdf',
    'payment.pdf',
    'application/pdf',
    5,
    repeat('b', 64),
    '10000000-0000-0000-0000-000000000002'
);

do $$
begin
    begin
        insert into bodeul.companion_session_artifacts (
            companion_session_id, purpose, client_request_id, item_order,
            storage_path, file_name, content_type, size_bytes, sha256
        ) values (
            '30000000-0000-0000-0000-000000000001',
            'PAYMENT_EVIDENCE',
            '40000000-0000-0000-0000-000000000002',
            0,
            'companion-session-artifacts/v18/payment-2.pdf',
            'payment-2.pdf',
            'application/pdf',
            5,
            repeat('c', 64)
        );
        raise exception '서로 다른 요청 UUID의 PAYMENT_EVIDENCE item 0 중복이 허용되었습니다.';
    exception when unique_violation then
        null;
    end;

    begin
        insert into bodeul.companion_session_artifacts (
            companion_session_id, purpose, client_request_id, item_order,
            storage_path, file_name, content_type, size_bytes, sha256
        ) values (
            '30000000-0000-0000-0000-000000000001',
            'PRESCRIPTION_IMAGE',
            '40000000-0000-0000-0000-000000000003',
            0,
            'companion-session-artifacts/v18/prescription-invalid.png',
            'prescription-invalid.png',
            'image/png',
            8,
            'invalid'
        );
        raise exception '첨부 SHA-256 제약이 적용되지 않았습니다.';
    exception when check_violation then
        null;
    end;
end;
$$;

insert into bodeul.companion_session_artifacts (
    companion_session_id, purpose, client_request_id, item_order,
    storage_path, file_name, content_type, size_bytes, sha256
) values
    ('30000000-0000-0000-0000-000000000001', 'PRESCRIPTION_IMAGE',
     '40000000-0000-0000-0000-000000000004', 0,
     'companion-session-artifacts/v18/prescription-1.png', 'prescription-1.png',
     'image/png', 8, repeat('d', 64)),
    ('30000000-0000-0000-0000-000000000001', 'PRESCRIPTION_IMAGE',
     '40000000-0000-0000-0000-000000000005', 1,
     'companion-session-artifacts/v18/prescription-2.png', 'prescription-2.png',
     'image/png', 8, repeat('e', 64)),
    ('30000000-0000-0000-0000-000000000001', 'PRESCRIPTION_IMAGE',
     '40000000-0000-0000-0000-000000000006', 2,
     'companion-session-artifacts/v18/prescription-3.png', 'prescription-3.png',
     'image/png', 8, repeat('f', 64));

do $$
begin
    begin
        insert into bodeul.companion_session_artifacts (
            companion_session_id, purpose, client_request_id, item_order,
            storage_path, file_name, content_type, size_bytes, sha256
        ) values (
            '30000000-0000-0000-0000-000000000001',
            'PRESCRIPTION_IMAGE',
            '40000000-0000-0000-0000-000000000007',
            2,
            'companion-session-artifacts/v18/prescription-4.png',
            'prescription-4.png',
            'image/png',
            8,
            repeat('1', 64)
        );
        raise exception '네 번째 PRESCRIPTION_IMAGE가 허용되었습니다.';
    exception when unique_violation then
        null;
    end;
end;
$$;

delete from bodeul.companion_session_artifacts
where id = '50000000-0000-0000-0000-000000000001';

do $$
begin
    if not exists (
        select 1
        from bodeul.companion_session_artifact_operations
        where client_request_id = '40000000-0000-0000-0000-000000000001'
    ) then
        raise exception '첨부 교체 후에도 유지되어야 할 operation ledger가 삭제되었습니다.';
    end if;
end;
$$;
