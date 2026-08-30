insert into bodeul.app_users (id, firebase_uid, role)
values
    ('10000000-0000-0000-0000-000000000001', 'v18-patient', 'PATIENT'),
    ('10000000-0000-0000-0000-000000000002', 'v18-manager', 'MANAGER'),
    ('10000000-0000-0000-0000-000000000003', 'v18-guardian', 'GUARDIAN');

insert into bodeul.appointment_requests (
    id,
    firestore_id,
    patient_user_id,
    guardian_user_id,
    manager_user_id,
    requester_user_id,
    requester_role,
    hospital_name,
    department_name,
    appointment_at,
    appointment_at_epoch_millis,
    appointment_date_key,
    mobility_support_code,
    trip_type_code,
    manager_gender_preference_code,
    status,
    payment_method_code,
    coupon_code,
    payment_status_code,
    created_at
) values (
    '20000000-0000-0000-0000-000000000001',
    'v18-appointment',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000001',
    'PATIENT',
    'V18 검증 병원',
    '내과',
    '2026-08-29T01:00:00Z',
    1787965200000,
    '2026-08-29',
    'INDEPENDENT',
    'ONE_WAY',
    'ANY',
    'COMPLETED',
    'CARD',
    'NONE',
    'AUTHORIZED',
    '2026-08-29T00:00:00Z'
);

insert into bodeul.companion_sessions (
    id,
    firestore_id,
    appointment_request_id,
    manager_user_id,
    current_step_order,
    current_status,
    version,
    started_at,
    completed_at,
    created_at,
    updated_at
) values (
    '30000000-0000-0000-0000-000000000001',
    'v18-session',
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    7,
    'COMPLETED',
    4,
    '2026-08-29T00:30:00Z',
    '2026-08-29T02:00:00Z',
    '2026-08-29T00:00:00Z',
    '2026-08-29T02:00:00Z'
);

insert into bodeul.guardian_sharing_consents (
    id,
    appointment_request_id,
    patient_user_id,
    guardian_user_id,
    scopes,
    policy_version,
    granted_by_user_id,
    adult_self_declared_at,
    granted_at,
    expires_at,
    version,
    created_at,
    updated_at
) values (
    '70000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '["APPOINTMENT", "CHAT"]'::jsonb,
    'adult-guardian-sharing-v1',
    '10000000-0000-0000-0000-000000000001',
    '2026-08-28T01:00:00Z',
    '2026-08-28T01:00:00Z',
    '2026-09-10T01:00:00Z',
    4,
    '2026-08-28T01:00:00Z',
    '2026-08-28T01:00:00Z'
);

insert into bodeul.companion_chat_messages (
    id, companion_session_id, client_message_id, sender_user_id,
    sender_role, body, sent_at
) values (
    '60000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000001',
    'PATIENT',
    'V18 보존 시작점 검증 메시지',
    '2026-08-29T01:00:00Z'
);

insert into bodeul.companion_chat_attachments (
    id, chat_message_id, storage_path, file_name, content_type, size_bytes
) values (
    '60000000-0000-0000-0000-000000000003',
    '60000000-0000-0000-0000-000000000001',
    'companion-chat-attachments/v18/retention.png',
    'retention.png',
    'image/png',
    8
);

insert into bodeul.companion_session_locations (
    id, companion_session_id, client_location_id, manager_user_id,
    latitude, longitude, captured_at
) values (
    '60000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000002',
    37.5665,
    126.9780,
    '2026-08-29T01:30:00Z'
);
