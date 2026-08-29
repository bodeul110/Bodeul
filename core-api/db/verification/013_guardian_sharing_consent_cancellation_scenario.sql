\set ON_ERROR_STOP on

begin;

insert into bodeul.app_users (id, firebase_uid, role)
values
    ('51000000-0000-0000-0000-000000000001', 'cancel-patient', 'PATIENT'),
    ('51000000-0000-0000-0000-000000000002', 'cancel-guardian', 'GUARDIAN'),
    ('51000000-0000-0000-0000-000000000003', 'cancel-manager', 'MANAGER');

insert into bodeul.appointment_requests (
    id, firestore_id, patient_user_id, guardian_user_id, manager_user_id,
    requester_user_id, requester_role, hospital_name, department_name,
    appointment_at, appointment_at_epoch_millis, appointment_date_key,
    mobility_support_code, trip_type_code, manager_gender_preference_code,
    status, payment_method_code, coupon_code, payment_status_code, version, created_at
) values (
    '52000000-0000-0000-0000-000000000001', 'matched-cancel-contract',
    '51000000-0000-0000-0000-000000000001',
    '51000000-0000-0000-0000-000000000002',
    '51000000-0000-0000-0000-000000000003',
    '51000000-0000-0000-0000-000000000001', 'PATIENT',
    '검증 병원', '내과', now() + interval '1 day',
    (extract(epoch from now() + interval '1 day') * 1000)::bigint,
    current_date::text, 'INDEPENDENT', 'ONE_WAY', 'ANY',
    'MATCHED', 'CARD', 'NONE', 'PENDING', 4, now()
);

insert into bodeul.companion_sessions (
    id, appointment_request_id, manager_user_id, current_status, version
) values (
    '53000000-0000-0000-0000-000000000001',
    '52000000-0000-0000-0000-000000000001',
    '51000000-0000-0000-0000-000000000003',
    'READY', 0
);

insert into bodeul.guardian_sharing_consents (
    id, appointment_request_id, patient_user_id, guardian_user_id,
    scopes, policy_version, granted_by_user_id, adult_self_declared_at,
    granted_at, expires_at
) values (
    '54000000-0000-0000-0000-000000000001',
    '52000000-0000-0000-0000-000000000001',
    '51000000-0000-0000-0000-000000000001',
    '51000000-0000-0000-0000-000000000002',
    '["APPOINTMENT", "CHAT"]'::jsonb, 'adult-guardian-sharing-v1',
    '51000000-0000-0000-0000-000000000001', now(), now(), now() + interval '8 days'
);

update bodeul.appointment_requests
set status = 'CANCELED', updated_at = now(), version = version + 1
where id = '52000000-0000-0000-0000-000000000001'
  and status in ('REQUESTED', 'MATCHED')
  and version = 4;

update bodeul.companion_sessions
set current_status = 'CANCELED', canceled_at = now(), updated_at = now(), version = version + 1
where appointment_request_id = '52000000-0000-0000-0000-000000000001'
  and current_status not in ('COMPLETED', 'CANCELED');

update bodeul.guardian_sharing_consents consent
set care_ended_at = session.canceled_at,
    expires_at = session.canceled_at + interval '7 days',
    expiry_finalized = true,
    version = consent.version + 1,
    updated_at = now()
from bodeul.companion_sessions session
where consent.appointment_request_id = '52000000-0000-0000-0000-000000000001'
  and session.appointment_request_id = consent.appointment_request_id
  and not consent.expiry_finalized;

select count(*) = 1 as matched_cancellation_is_atomic
from bodeul.appointment_requests appointment
join bodeul.companion_sessions session
  on session.appointment_request_id = appointment.id
join bodeul.guardian_sharing_consents consent
  on consent.appointment_request_id = appointment.id
where appointment.id = '52000000-0000-0000-0000-000000000001'
  and appointment.status = 'CANCELED'
  and appointment.version = 5
  and session.current_status = 'CANCELED'
  and session.canceled_at is not null
  and consent.expiry_finalized
  and consent.care_ended_at = session.canceled_at
  and consent.expires_at = session.canceled_at + interval '7 days'
\gset
\if :matched_cancellation_is_atomic
\else
    \echo 'MATCHED 예약·세션 취소와 동의 만료 확정 계약 검증 실패'
    \quit 1
\endif

rollback;

\echo 'MATCHED 예약 취소와 실제 취소 시각 +7일 만료 계약 통과'
