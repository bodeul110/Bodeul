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

createdb bodeul_guide_empty
createdb bodeul_guide_upgrade
createdb bodeul_appointment_public_code
createdb bodeul_admin_rbac

bootstrap_database() {
    local database="$1"
    psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/001_database_access.sql
    psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/002_database_access_hardening.sql
    psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/004_retention_runtime.sql
}

migrate_database() {
    local database="$1"
    local target="${2:-}"
    export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${database}?sslmode=disable"
    export MIGRATION_DB_USERNAME="$PGUSER"
    export MIGRATION_DB_PASSWORD="$PGPASSWORD"

    # 보안 bootstrap이 schema와 함수를 먼저 만들므로 disposable DB에서만 version 0으로 기준선을 둔다.
    if [[ -n "$target" ]]; then
        SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
            SPRING_FLYWAY_BASELINE_VERSION=0 \
            SPRING_FLYWAY_TARGET="$target" \
            ./gradlew migrateDatabase --console=plain
    else
        env -u SPRING_FLYWAY_TARGET \
            SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
            SPRING_FLYWAY_BASELINE_VERSION=0 \
            ./gradlew migrateDatabase --console=plain
    fi
}

bootstrap_database bodeul_guide_empty
migrate_database bodeul_guide_empty
psql --dbname bodeul_guide_empty --set ON_ERROR_STOP=1 \
    --file db/verification/006_companion_guide_snapshot_checks.sql
psql --dbname bodeul_guide_empty --set ON_ERROR_STOP=1 \
    --file db/verification/016_companion_guide_video_metadata_checks.sql

bootstrap_database bodeul_guide_upgrade
migrate_database bodeul_guide_upgrade 13
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/005_companion_guide_snapshot_legacy_fixture.sql
migrate_database bodeul_guide_upgrade
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/006_companion_guide_snapshot_checks.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/007_companion_guide_snapshot_upgrade_checks.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/016_companion_guide_video_metadata_checks.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/008_companion_guide_snapshot_rollback_fixture.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/rollback/V21__remove_companion_guide_video_metadata_validation.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/017_companion_guide_video_metadata_rollback_checks.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/rollback/V14__restore_live_companion_guides.sql
psql --dbname bodeul_guide_upgrade --set ON_ERROR_STOP=1 \
    --file db/verification/009_companion_guide_snapshot_rollback_checks.sql

bootstrap_database bodeul_appointment_public_code
migrate_database bodeul_appointment_public_code 19
psql --dbname bodeul_appointment_public_code --set ON_ERROR_STOP=1 \
    --file db/verification/012_appointment_public_code_checks.sql
psql --dbname bodeul_appointment_public_code --set ON_ERROR_STOP=1 \
    --file db/rollback/V19__remove_appointment_public_code.sql
psql --dbname bodeul_appointment_public_code --set ON_ERROR_STOP=1 \
    --file db/verification/013_appointment_public_code_rollback_checks.sql

bootstrap_database bodeul_admin_rbac
migrate_database bodeul_admin_rbac
psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/verification/012_admin_rbac_checks.sql
psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/verification/014_admin_rbac_rollback_failure_fixture.sql
if psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/rollback/V20__remove_admin_rbac_and_access_audit.sql; then
    echo "의존 객체가 있는 V20 롤백이 예상과 달리 성공했습니다." >&2
    exit 1
fi
psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/verification/015_admin_rbac_rollback_atomicity_checks.sql
psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/rollback/V20__remove_admin_rbac_and_access_audit.sql
psql --dbname bodeul_admin_rbac --set ON_ERROR_STOP=1 \
    --file db/verification/013_admin_rbac_rollback_checks.sql
