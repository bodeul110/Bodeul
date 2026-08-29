#!/usr/bin/env bash
set -euo pipefail

export PGHOST="${PGHOST:-127.0.0.1}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"

database="bodeul_guardian_consent"
createdb "$database"

psql --dbname postgres --set ON_ERROR_STOP=1 <<'SQL'
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin;
    end if;
end;
$$;
SQL

# Supabase managed Realtime의 CI 최소 대역이다. 권한 함수와 RLS 정책만 실제 PostgreSQL에서 검증한다.
psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
create schema realtime authorization postgres;
create table realtime.messages (
    id bigint generated always as identity primary key,
    topic text not null,
    extension text not null,
    payload jsonb not null,
    event text not null,
    private boolean not null
);
alter table realtime.messages enable row level security;
grant usage on schema realtime to authenticated;
grant select on realtime.messages to authenticated;

create function realtime.topic() returns text
language sql stable
as $$ select current_setting('realtime.topic', true) $$;

create function realtime.send(jsonb, text, text, boolean) returns void
language plpgsql
as $$ begin null; end $$;
SQL

psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/001_database_access.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/002_database_access_hardening.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/004_retention_runtime.sql

export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${database}?sslmode=disable"
export MIGRATION_DB_USERNAME="$PGUSER"
export MIGRATION_DB_PASSWORD="$PGPASSWORD"
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    ./gradlew migrateDatabase --console=plain

psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/verification/012_guardian_sharing_consent_checks.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/verification/013_guardian_sharing_consent_cancellation_scenario.sql

psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/bootstrap/003_companion_realtime_authorization.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
insert into bodeul_realtime_auth.allowed_firebase_projects (project_id)
values ('bodeul-dev');
SQL
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/bootstrap/005_guardian_sharing_realtime_authorization.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/verification/004_companion_realtime_authorization_scenarios.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/bootstrap/rollback/005_guardian_sharing_realtime_authorization_rollback.sql

# 행이 남은 rollback은 반드시 실패하고, export 증적 뒤 빈 테이블만 제거할 수 있어야 한다.
psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
insert into bodeul.app_users (id, firebase_uid, role)
values
    ('61000000-0000-0000-0000-000000000001', 'rollback-patient', 'PATIENT'),
    ('61000000-0000-0000-0000-000000000002', 'rollback-guardian', 'GUARDIAN');
insert into bodeul.appointment_requests (
    id, firestore_id, patient_user_id, guardian_user_id, requester_user_id, requester_role,
    hospital_name, department_name, appointment_at, appointment_at_epoch_millis,
    appointment_date_key, mobility_support_code, trip_type_code, manager_gender_preference_code,
    status, payment_method_code, coupon_code, payment_status_code, created_at
) values (
    '62000000-0000-0000-0000-000000000001', 'rollback-guard-contract',
    '61000000-0000-0000-0000-000000000001',
    '61000000-0000-0000-0000-000000000002',
    '61000000-0000-0000-0000-000000000001', 'PATIENT',
    '검증 병원', '내과', now() + interval '1 day',
    (extract(epoch from now() + interval '1 day') * 1000)::bigint,
    current_date::text, 'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
    'CARD', 'NONE', 'PENDING', now()
);
insert into bodeul.guardian_sharing_consents (
    id, appointment_request_id, patient_user_id, guardian_user_id, scopes,
    policy_version, granted_by_user_id, adult_self_declared_at, granted_at, expires_at
) values (
    '63000000-0000-0000-0000-000000000001',
    '62000000-0000-0000-0000-000000000001',
    '61000000-0000-0000-0000-000000000001',
    '61000000-0000-0000-0000-000000000002',
    '["APPOINTMENT"]'::jsonb, 'adult-guardian-sharing-v1',
    '61000000-0000-0000-0000-000000000001', now(), now(), now() + interval '8 days'
);
insert into bodeul.guardian_sharing_consent_events (
    consent_id, appointment_request_id, patient_user_id, guardian_user_id,
    action, scopes, policy_version, actor_user_id,
    adult_self_declared_at, occurred_at, consent_version
) values (
    '63000000-0000-0000-0000-000000000001',
    '62000000-0000-0000-0000-000000000001',
    '61000000-0000-0000-0000-000000000001',
    '61000000-0000-0000-0000-000000000002',
    'GRANTED', '["APPOINTMENT"]'::jsonb, 'adult-guardian-sharing-v1',
    '61000000-0000-0000-0000-000000000001', now(), now(), 0
);
SQL

if psql --dbname "$database" --set ON_ERROR_STOP=1 \
        --file db/rollback/V17__remove_guardian_sharing_consents.sql; then
    echo "동의 행이 남은 V17 rollback이 성공해서는 안 됩니다." >&2
    exit 1
fi

export_file="${RUNNER_TEMP:-/tmp}/guardian-sharing-consent-v17-data.sql"
pg_dump --dbname "$database" --data-only \
    --table=bodeul.guardian_sharing_consent_settings \
    --table=bodeul.guardian_sharing_consents \
    --table=bodeul.guardian_sharing_consent_events \
    --file="$export_file"
test -s "$export_file"

psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
delete from bodeul.guardian_sharing_consent_events;
delete from bodeul.guardian_sharing_consents;
SQL
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/rollback/V17__remove_guardian_sharing_consents.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 \
    --file db/verification/014_guardian_sharing_consent_rollback_checks.sql
