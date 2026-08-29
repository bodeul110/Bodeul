#!/usr/bin/env bash
set -euo pipefail

export PGHOST="${PGHOST:-127.0.0.1}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"

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

createdb bodeul_completion_upgrade
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/bootstrap/001_database_access.sql
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/bootstrap/002_database_access_hardening.sql
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/bootstrap/004_retention_runtime.sql

export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/bodeul_completion_upgrade?sslmode=disable"
export MIGRATION_DB_USERNAME="$PGUSER"
export MIGRATION_DB_PASSWORD="$PGPASSWORD"

SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    SPRING_FLYWAY_TARGET=17 \
    ./gradlew migrateDatabase --console=plain

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/012_companion_completion_legacy_fixture.sql

env -u SPRING_FLYWAY_TARGET \
    SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    ./gradlew migrateDatabase --console=plain

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/013_companion_completion_checks.sql

if psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
        --file db/rollback/V18__merge_companion_care_completion.sql; then
    echo "첨부 행이 있는데 V18 rollback이 성공했습니다." >&2
    exit 1
fi

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "copy (select storage_path from bodeul.companion_session_artifacts order by storage_path) to stdout" \
    > "${RUNNER_TEMP:-/tmp}/bodeul-v18-artifact-paths.txt"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "delete from bodeul.companion_session_artifacts"

if psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
        --file db/rollback/V18__merge_companion_care_completion.sql; then
    echo "operation ledger 행이 있는데 V18 rollback이 성공했습니다." >&2
    exit 1
fi

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "copy (select companion_session_id, purpose, client_request_id, payload_fingerprint, result_revision from bodeul.companion_session_artifact_operations order by companion_session_id, purpose, result_revision) to stdout" \
    > "${RUNNER_TEMP:-/tmp}/bodeul-v18-artifact-operations.txt"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "delete from bodeul.companion_session_artifact_operations"

expect_rollback_failure() {
    local label="$1"
    if psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
            --file db/rollback/V18__merge_companion_care_completion.sql; then
        echo "${label} 데이터가 있는데 V18 rollback이 성공했습니다." >&2
        exit 1
    fi
}

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set manager_journal = 'baseline 변조' where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "baseline 이후 변경"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions as session set current_status = 'COMPLETED', completed_at = baseline.expected_completed_at, care_ended_at = baseline.expected_care_ended_at, manager_journal = '', report_generation_status = baseline.expected_report_generation_status, report_generation_attempts = baseline.expected_report_generation_attempts, report_generation_last_error = baseline.expected_report_generation_last_error, report_generation_updated_at = baseline.expected_report_generation_updated_at from bodeul.companion_completion_v18_baseline as baseline where session.id = baseline.companion_session_id and session.id = '30000000-0000-0000-0000-000000000001'"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set current_status = 'PAYMENT', care_ended_at = null, manager_journal = '', report_generation_status = 'NOT_REQUESTED', report_generation_attempts = 0, report_generation_last_error = '', report_generation_updated_at = null where id = '30000000-0000-0000-0000-000000000001'"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "delete from bodeul.companion_completion_v18_baseline where companion_session_id = '30000000-0000-0000-0000-000000000001'"

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set care_ended_at = now() where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "care_ended_at"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set care_ended_at = null, manager_journal = '검증 일지' where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "manager_journal"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set manager_journal = '', report_generation_status = 'FAILED' where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "report_generation_status"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set report_generation_status = 'NOT_REQUESTED', report_generation_attempts = 1 where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "report_generation_attempts"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set report_generation_attempts = 0, report_generation_last_error = '검증 실패' where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "report_generation_last_error"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set report_generation_last_error = '', report_generation_updated_at = now() where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "report_generation_updated_at"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set current_status = 'CARE_ENDED', care_ended_at = now(), report_generation_updated_at = null where id = '30000000-0000-0000-0000-000000000001'"
expect_rollback_failure "CARE_ENDED"
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --command "update bodeul.companion_sessions set current_status = 'PAYMENT', care_ended_at = null where id = '30000000-0000-0000-0000-000000000001'"

psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/rollback/V18__merge_companion_care_completion.sql
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/014_companion_completion_rollback_checks.sql

createdb bodeul_completion_clean_rollback
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/bootstrap/001_database_access.sql
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/bootstrap/002_database_access_hardening.sql
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/bootstrap/004_retention_runtime.sql
export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/bodeul_completion_clean_rollback?sslmode=disable"
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    SPRING_FLYWAY_TARGET=17 \
    ./gradlew migrateDatabase --console=plain
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/verification/012_companion_completion_legacy_fixture.sql
env -u SPRING_FLYWAY_TARGET \
    SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    ./gradlew migrateDatabase --console=plain
injected_rollback="${RUNNER_TEMP:-/tmp}/bodeul-v18-injected-rollback.sql"
sed 's/^commit;$/select 1\/0;\ncommit;/' \
    db/rollback/V18__merge_companion_care_completion.sql > "$injected_rollback"
if psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
        --file "$injected_rollback"; then
    echo "후반 오류를 주입한 V18 rollback이 성공했습니다." >&2
    exit 1
fi
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 <<'SQL'
do $$
begin
    if to_regclass('bodeul.companion_completion_v18_baseline') is null
            or to_regclass('bodeul.companion_session_artifacts') is null
            or to_regclass('bodeul.companion_session_artifact_operations') is null
            or not exists (
                select 1
                from information_schema.columns
                where table_schema = 'bodeul'
                  and table_name = 'companion_sessions'
                  and column_name = 'care_ended_at'
            ) then
        raise exception '후반 실패 rollback이 V18 schema를 부분 삭제했습니다.';
    end if;
end;
$$;
SQL
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/rollback/V18__merge_companion_care_completion.sql
psql --dbname bodeul_completion_clean_rollback --set ON_ERROR_STOP=1 \
    --file db/verification/014_companion_completion_rollback_checks.sql
