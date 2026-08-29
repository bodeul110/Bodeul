\set ON_ERROR_STOP on

begin transaction read only;

select
    to_regclass('bodeul.guardian_sharing_consents') is not null
    and to_regclass('bodeul.guardian_sharing_consent_events') is not null
    and to_regclass('bodeul.guardian_sharing_consent_settings') is not null
    as consent_tables_exist
\gset
\if :consent_tables_exist
\else
    \echo '보호자 정보공유 동의 테이블 생성 검증 실패'
    \quit 1
\endif

select exists (
    select 1
    from information_schema.columns
    where table_schema = 'bodeul'
      and table_name = 'guardian_sharing_consents'
      and column_name = 'adult_self_declared_at'
      and is_nullable = 'NO'
) as adult_declaration_evidence_exists
\gset
\if :adult_declaration_evidence_exists
\else
    \echo '성인 환자 본인 확인 증적 컬럼 검증 실패'
    \quit 1
\endif

select exists (
    select 1
    from information_schema.columns
    where table_schema = 'bodeul'
      and table_name = 'guardian_sharing_consents'
      and column_name = 'expiry_finalized'
      and is_nullable = 'NO'
) and exists (
    select 1
    from information_schema.columns
    where table_schema = 'bodeul'
      and table_name = 'guardian_sharing_consents'
      and column_name = 'care_ended_at'
) as care_boundary_state_exists
\gset
\if :care_boundary_state_exists
\else
    \echo '실제 동행 종료 기준 만료 상태 검증 실패'
    \quit 1
\endif

select exists (
    select 1
    from bodeul.guardian_sharing_consent_settings
    where singleton
      and policy_version = 'adult-guardian-sharing-v1'
      and not location_sharing_enabled
) as default_settings_are_fail_closed
\gset
\if :default_settings_are_fail_closed
\else
    \echo '현재 정책 버전 또는 위치 공유 기본 차단 검증 실패'
    \quit 1
\endif

select
    has_table_privilege(
        'bodeul_core_runtime',
        'bodeul.guardian_sharing_consents',
        'SELECT,INSERT,UPDATE')
    and not has_table_privilege(
        'bodeul_core_runtime',
        'bodeul.guardian_sharing_consents',
        'DELETE')
    and not has_table_privilege(
        'authenticated',
        'bodeul.guardian_sharing_consents',
        'SELECT')
    and not has_table_privilege(
        'service_role',
        'bodeul.guardian_sharing_consents',
        'SELECT')
    as consent_privileges_are_minimal
\gset
\if :consent_privileges_are_minimal
\else
    \echo '보호자 정보공유 동의 최소 권한 검증 실패'
    \quit 1
\endif

select bool_and(relrowsecurity) as consent_rls_enabled
from pg_class
where oid in (
    'bodeul.guardian_sharing_consents'::regclass,
    'bodeul.guardian_sharing_consent_events'::regclass,
    'bodeul.guardian_sharing_consent_settings'::regclass
)
\gset
\if :consent_rls_enabled
\else
    \echo '보호자 정보공유 동의 RLS 검증 실패'
    \quit 1
\endif

rollback;

\echo '보호자 정보공유 동의 저장·권한 검증 통과'
