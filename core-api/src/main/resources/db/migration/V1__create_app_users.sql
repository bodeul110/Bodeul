create table bodeul.app_users (
    id uuid primary key default gen_random_uuid(),
    firebase_uid text not null,
    role text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_app_users_firebase_uid unique (firebase_uid),
    constraint ck_app_users_role
        check (role in ('PATIENT', 'GUARDIAN', 'MANAGER', 'ADMIN'))
);

comment on table bodeul.app_users is 'Firebase UID와 BoDeul 서비스 역할을 연결하는 서버 전용 사용자 테이블';
comment on column bodeul.app_users.firebase_uid is 'Firebase Admin SDK가 검증한 Firebase Auth UID';
comment on column bodeul.app_users.role is 'BoDeul 서비스 인가 역할';

revoke all on table bodeul.app_users from public, anon, authenticated, service_role;
grant select on table bodeul.app_users to bodeul_core_runtime, bodeul_admin_runtime;

alter table bodeul.app_users enable row level security;

create policy app_users_core_select
    on bodeul.app_users
    for select
    to bodeul_core_runtime
    using (true);

create policy app_users_admin_select
    on bodeul.app_users
    for select
    to bodeul_admin_runtime
    using (true);
