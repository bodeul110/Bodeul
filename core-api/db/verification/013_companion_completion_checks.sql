do $$
declare
    v_care_ended_at timestamptz;
    v_report_status text;
begin
    select care_ended_at, report_generation_status
    into v_care_ended_at, v_report_status
    from bodeul.companion_sessions
    where id = '30000000-0000-0000-0000-000000000001';

    if v_care_ended_at is null or v_report_status <> 'FAILED' then
        raise exception 'V18 legacy COMPLETED 백필 결과가 올바르지 않습니다.';
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
