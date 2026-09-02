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

createdb bodeul_bank_transfer_payment
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/bootstrap/001_database_access.sql
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/bootstrap/002_database_access_hardening.sql
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/bootstrap/004_retention_runtime.sql

export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/bodeul_bank_transfer_payment?sslmode=disable"
export MIGRATION_DB_USERNAME="$PGUSER"
export MIGRATION_DB_PASSWORD="$PGPASSWORD"
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    SPRING_FLYWAY_BASELINE_VERSION=0 \
    ./gradlew migrateDatabase --console=plain

psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/verification/018_bank_transfer_payment_checks.sql
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/verification/020_bank_transfer_payment_rollback_failure_fixture.sql

if psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/rollback/V22__remove_bank_transfer_payment_contract.sql; then
    echo "운영 무통장입금 데이터가 있는 V22 롤백이 예상과 달리 성공했습니다." >&2
    exit 1
fi

psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/verification/021_bank_transfer_payment_rollback_atomicity_checks.sql
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/rollback/V22__remove_bank_transfer_payment_contract.sql
psql --dbname bodeul_bank_transfer_payment --set ON_ERROR_STOP=1 \
    --file db/verification/019_bank_transfer_payment_rollback_checks.sql
