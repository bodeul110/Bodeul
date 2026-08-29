\set ON_ERROR_STOP on

begin transaction read only;

select
    to_regclass('bodeul.guardian_sharing_consents') is null
    and to_regclass('bodeul.guardian_sharing_consent_events') is null
    and to_regclass('bodeul.guardian_sharing_consent_settings') is null
    as consent_objects_removed
\gset
\if :consent_objects_removed
\else
    \echo 'V17 보호자 동의 객체 rollback 검증 실패'
    \quit 1
\endif

select pg_get_functiondef(
    'bodeul_realtime_auth.can_receive_companion_broadcast()'::regprocedure
) not like '%appointment.guardian_user_id%' as guardian_broadcast_remains_denied
\gset
\if :guardian_broadcast_remains_denied
\else
    \echo 'rollback 뒤 보호자 Broadcast 차단 유지 검증 실패'
    \quit 1
\endif

rollback;

\echo 'V17 보호자 동의 안전 rollback 검증 통과'
