insert into bodeul.app_users (id, firebase_uid, role)
values
    ('10000000-0000-0000-0000-000000000001', 'v18-patient', 'PATIENT'),
    ('10000000-0000-0000-0000-000000000002', 'v18-manager', 'MANAGER');

insert into bodeul.appointment_requests (
    id,
    firestore_id,
    patient_user_id,
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
