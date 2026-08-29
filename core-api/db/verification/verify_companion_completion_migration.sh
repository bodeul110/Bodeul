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
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/rollback/V18__merge_companion_care_completion.sql
psql --dbname bodeul_completion_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/014_companion_completion_rollback_checks.sql
