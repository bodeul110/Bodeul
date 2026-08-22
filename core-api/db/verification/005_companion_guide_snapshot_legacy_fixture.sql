begin;
set local role bodeul_migration;

insert into bodeul.app_users (id, firebase_uid, role, name)
values
    ('00000000-0000-0000-0000-000000000101', 'guide-fixture-patient', 'PATIENT', '가이드 환자'),
    ('00000000-0000-0000-0000-000000000102', 'guide-fixture-manager', 'MANAGER', '가이드 매니저'),
    ('00000000-0000-0000-0000-000000000103', 'guide-fixture-admin', 'ADMIN', '가이드 관리자');

insert into bodeul.hospital_guides (
    id,
    hospital_name,
    department_name,
    steps
) values (
    '00000000-0000-0000-0000-000000000120',
    '기존병원',
    '내과',
    '[
      {"order": 1, "title": "기존 1", "description": "코드 없는 기존 단계"},
      {"order": 2, "title": "기존 2", "description": "코드 없는 기존 단계"},
      {"order": 3, "title": "기존 3", "description": "코드 없는 기존 단계"},
      {"order": 4, "title": "기존 4", "description": "코드 없는 기존 단계"},
      {"order": 5, "title": "기존 5", "description": "코드 없는 기존 단계"},
      {"order": 6, "title": "기존 6", "description": "코드 없는 기존 단계"},
      {"order": 7, "title": "기존 7", "description": "코드 없는 기존 단계"}
    ]'::jsonb
);

insert into bodeul.appointment_requests (
    id,
    firestore_id,
    patient_user_id,
    manager_user_id,
    requester_user_id,
    requester_role,
    patient_name,
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
    created_at,
    updated_at
) values
    (
        '00000000-0000-0000-0000-000000000111',
        null,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000101',
        'PATIENT',
        '가이드 환자',
        '기존병원',
        '내과',
        '2026-09-01T01:00:00Z',
        1788224400000,
        '2026-09-01',
        'INDEPENDENT',
        'ONE_WAY',
        'ANY',
        'MATCHED',
        'CARD',
        'NONE',
        'AUTHORIZED',
        '2026-08-20T00:00:00Z',
        '2026-08-20T00:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000112',
        'guide-fixture-firestore-appointment',
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000101',
        'PATIENT',
        '가이드 환자',
        '기존병원',
        '내과',
        '2026-09-02T01:00:00Z',
        1788310800000,
        '2026-09-02',
        'INDEPENDENT',
        'ONE_WAY',
        'ANY',
        'MATCHED',
        'CARD',
        'NONE',
        'AUTHORIZED',
        '2026-08-20T00:00:00Z',
        '2026-08-20T00:00:00Z'
    );

insert into bodeul.companion_sessions (
    id,
    firestore_id,
    appointment_request_id,
    manager_user_id,
    current_step_order,
    current_status
) values
    (
        '00000000-0000-0000-0000-000000000131',
        null,
        '00000000-0000-0000-0000-000000000111',
        '00000000-0000-0000-0000-000000000102',
        3,
        'IN_TREATMENT'
    ),
    (
        '00000000-0000-0000-0000-000000000132',
        'guide-fixture-firestore-session',
        '00000000-0000-0000-0000-000000000112',
        '00000000-0000-0000-0000-000000000102',
        4,
        'IN_TREATMENT'
    );

commit;
