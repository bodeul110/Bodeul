begin;
set local role bodeul_migration;

insert into bodeul.appointment_requests (
    id,
    patient_user_id,
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
) values (
    '00000000-0000-0000-0000-000000000113',
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000101',
    'PATIENT',
    '가이드 환자',
    'rollback병원',
    '내과',
    '2026-09-03T01:00:00Z',
    1788397200000,
    '2026-09-03',
    'INDEPENDENT',
    'ONE_WAY',
    'ANY',
    'REQUESTED',
    'CARD',
    'NONE',
    'AUTHORIZED',
    '2026-08-22T00:00:00Z',
    '2026-08-22T00:00:00Z'
);

commit;
