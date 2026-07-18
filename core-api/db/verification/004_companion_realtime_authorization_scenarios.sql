\set ON_ERROR_STOP on

begin;

insert into bodeul.app_users (id, firebase_uid, role)
values
    ('10000000-0000-0000-0000-000000000001', 'realtime-patient', 'PATIENT'),
    ('10000000-0000-0000-0000-000000000002', 'realtime-guardian', 'GUARDIAN'),
    ('10000000-0000-0000-0000-000000000003', 'realtime-manager', 'MANAGER'),
    ('10000000-0000-0000-0000-000000000004', 'realtime-outsider', 'PATIENT');

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
    'realtime-authorization-appointment',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000001',
    'PATIENT',
    '검증 병원',
    '내과',
    '2026-07-19T01:00:00Z',
    1784422800000,
    '2026-07-19',
    'INDEPENDENT',
    'ROUND_TRIP',
    'ANY',
    'IN_PROGRESS',
    'CARD',
    'NONE',
    'AUTHORIZED',
    now()
);

insert into bodeul.companion_sessions (
    id,
    appointment_request_id,
    manager_user_id,
    current_status
) values (
    '30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    'IN_TREATMENT'
);

insert into realtime.messages (topic, extension, payload, event, private)
values (
    'companion-session:30000000-0000-0000-0000-000000000001',
    'broadcast',
    '{}'::jsonb,
    'chat.changed',
    true
);

set local role authenticated;
select set_config(
    'realtime.topic',
    'companion-session:30000000-0000-0000-0000-000000000001',
    true
);

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-patient","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 1 as patient_allowed from realtime.messages \gset
\if :patient_allowed
\else
    \echo '환자 Realtime 구독 허용 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-guardian","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 1 as guardian_allowed from realtime.messages \gset
\if :guardian_allowed
\else
    \echo '보호자 Realtime 구독 허용 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-manager","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 1 as manager_allowed from realtime.messages \gset
\if :manager_allowed
\else
    \echo '배정 매니저 Realtime 구독 허용 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-outsider","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 0 as outsider_denied from realtime.messages \gset
\if :outsider_denied
\else
    \echo '비참여 사용자 Realtime 구독 거부 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-patient","role":"authenticated","aud":"other-project","iss":"https://securetoken.google.com/other-project"}',
    true
);
select count(*) = 0 as other_project_denied from realtime.messages \gset
\if :other_project_denied
\else
    \echo '다른 Firebase 프로젝트 token 거부 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-patient","role":"PATIENT","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 0 as wrong_role_denied from realtime.messages \gset
\if :wrong_role_denied
\else
    \echo '잘못된 Supabase role claim 거부 검증 실패'
    \quit 1
\endif

select set_config('realtime.topic', 'companion-session:not-a-uuid', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"realtime-patient","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 0 as malformed_topic_denied from realtime.messages \gset
\if :malformed_topic_denied
\else
    \echo '잘못된 Realtime topic 거부 검증 실패'
    \quit 1
\endif

reset role;
rollback;

\echo '동행 Realtime 권한 허용/거부 시나리오 통과'
