\set ON_ERROR_STOP on

begin;

insert into realtime.messages (topic, extension, payload, event, private)
values (
    'companion-session:30000000-0000-0000-0000-000000000001',
    'broadcast',
    '{}'::jsonb,
    'chat.changed',
    true
);

update bodeul.companion_sessions
set current_status = 'PAYMENT',
    care_ended_at = null
where id = '30000000-0000-0000-0000-000000000001';

set local role authenticated;
select set_config(
    'realtime.topic',
    'companion-session:30000000-0000-0000-0000-000000000001',
    true
);
select set_config(
    'request.jwt.claims',
    '{"sub":"v18-manager","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 1 as active_manager_allowed from realtime.messages \gset
\if :active_manager_allowed
\else
    \echo '동행 중 매니저 Realtime 구독 허용 검증 실패'
    \quit 1
\endif

reset role;
update bodeul.companion_sessions
set current_status = 'CARE_ENDED',
    care_ended_at = '2026-08-29T02:00:00Z'
where id = '30000000-0000-0000-0000-000000000001';

set local role authenticated;
select set_config(
    'realtime.topic',
    'companion-session:30000000-0000-0000-0000-000000000001',
    true
);
select set_config(
    'request.jwt.claims',
    '{"sub":"v18-manager","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 0 as care_ended_manager_denied from realtime.messages \gset
\if :care_ended_manager_denied
\else
    \echo '동행 종료 매니저 Realtime 구독 거부 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"v18-patient","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 1 as retained_patient_allowed from realtime.messages \gset
\if :retained_patient_allowed
\else
    \echo '동행 종료 뒤 환자 Realtime 구독 허용 검증 실패'
    \quit 1
\endif

select set_config(
    'request.jwt.claims',
    '{"sub":"v18-guardian","role":"authenticated","aud":"bodeul-dev","iss":"https://securetoken.google.com/bodeul-dev"}',
    true
);
select count(*) = 0 as guardian_denied from realtime.messages \gset
\if :guardian_denied
\else
    \echo '보호자 Realtime 구독 거부 검증 실패'
    \quit 1
\endif

reset role;
rollback;

\echo 'V18 동행 종료 전후 Realtime 권한 시나리오 통과'
