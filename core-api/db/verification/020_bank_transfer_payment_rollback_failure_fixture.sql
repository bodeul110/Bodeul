begin;
set local role bodeul_migration;

insert into bodeul.app_users (id, firebase_uid, role, name, email, phone)
values (
    '50000000-0000-0000-0000-000000000001',
    'bank-rollback-patient',
    'PATIENT',
    '롤백 환자',
    'rollback@example.com',
    '010-9999-9999'
);

insert into bodeul.appointment_requests (
    id,
    client_request_id,
    patient_user_id,
    requester_user_id,
    requester_role,
    patient_name,
    patient_phone,
    patient_email,
    hospital_name,
    department_name,
    appointment_at,
    appointment_at_epoch_millis,
    appointment_date_key,
    mobility_support_code,
    trip_type_code,
    manager_gender_preference_code,
    status,
    base_price,
    option_surcharge_price,
    coupon_discount_price,
    final_price,
    payment_method_code,
    coupon_code,
    payment_status_code
) values (
    '50000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    'PATIENT',
    '롤백 환자',
    '010-9999-9999',
    'rollback@example.com',
    '롤백 검증 병원',
    '내과',
    now() + interval '14 days',
    (extract(epoch from now() + interval '14 days') * 1000)::bigint,
    to_char(current_date + 14, 'YYYY-MM-DD'),
    'INDEPENDENT',
    'ONE_WAY',
    'ANY',
    'REQUESTED',
    69000,
    0,
    0,
    69000,
    'BANK_TRANSFER',
    'NONE',
    'AWAITING_DEPOSIT'
);

commit;
