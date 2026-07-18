alter table bodeul.app_users
    add column name text not null default '',
    add column email text not null default '',
    add column phone text not null default '';

comment on column bodeul.app_users.name is '예약 참가자 스냅샷과 연결 계정 조회에 사용하는 정규화된 이름';
comment on column bodeul.app_users.email is '연결 계정 조회에 사용하는 정규화된 이메일';
comment on column bodeul.app_users.phone is '연결 계정 조회에 사용하는 정규화된 전화번호';

with participant_profiles as (
    select
        patient_user_id as user_id,
        patient_name as name,
        patient_email as email,
        patient_phone as phone,
        coalesce(updated_at, created_at) as observed_at
    from bodeul.appointment_requests
    where patient_user_id is not null
    union all
    select
        guardian_user_id as user_id,
        guardian_name as name,
        guardian_email as email,
        guardian_phone as phone,
        coalesce(updated_at, created_at) as observed_at
    from bodeul.appointment_requests
    where guardian_user_id is not null
), latest_profiles as (
    select distinct on (user_id)
        user_id,
        name,
        email,
        phone
    from participant_profiles
    order by user_id, observed_at desc
)
update bodeul.app_users as app_user
set name = coalesce(nullif(btrim(profile.name), ''), app_user.name),
    email = coalesce(nullif(lower(btrim(profile.email)), ''), app_user.email),
    phone = coalesce(nullif(btrim(profile.phone), ''), app_user.phone),
    updated_at = now()
from latest_profiles as profile
where app_user.id = profile.user_id;

create index ix_app_users_role_email
    on bodeul.app_users (role, lower(email))
    where email <> '';

create index ix_app_users_role_phone
    on bodeul.app_users (role, phone)
    where phone <> '';

alter table bodeul.appointment_requests
    drop constraint ck_appointment_requests_firestore_id,
    alter column firestore_id drop not null,
    alter column imported_at drop default,
    alter column imported_at drop not null,
    alter column created_at set default now();

update bodeul.appointment_requests
set updated_at = created_at
where updated_at is null;

alter table bodeul.appointment_requests
    alter column updated_at set default now(),
    alter column updated_at set not null,
    add column client_request_id uuid,
    add column version bigint not null default 0,
    add constraint ck_appointment_requests_firestore_id
        check (firestore_id is null or btrim(firestore_id) <> ''),
    add constraint ck_appointment_requests_version
        check (version >= 0);

comment on table bodeul.appointment_requests is '예약 생성, 조회, 수정과 취소를 처리하는 PostgreSQL 운영 원본';
comment on column bodeul.appointment_requests.firestore_id is '전환 전에 생성된 행의 원본 Firestore appointmentRequests 문서 ID';
comment on column bodeul.appointment_requests.imported_at is 'Firestore 백필 행이 PostgreSQL에 마지막으로 적재된 시각. PostgreSQL 원생 행은 null';
comment on column bodeul.appointment_requests.client_request_id is '네트워크 재시도 시 중복 생성을 막는 클라이언트 요청 UUID';
comment on column bodeul.appointment_requests.version is '수정 충돌을 검출하는 낙관적 잠금 버전';

create unique index uq_appointment_requests_requester_client_request
    on bodeul.appointment_requests (requester_user_id, client_request_id)
    where client_request_id is not null;

grant insert, update on table bodeul.appointment_requests to bodeul_core_runtime;
revoke insert, update, delete on table bodeul.appointment_requests from bodeul_admin_runtime;
revoke all on table bodeul.appointment_requests from public, anon, authenticated, service_role;

create policy appointment_requests_core_insert
    on bodeul.appointment_requests
    for insert
    to bodeul_core_runtime
    with check (true);

create policy appointment_requests_core_update
    on bodeul.appointment_requests
    for update
    to bodeul_core_runtime
    using (true)
    with check (true);
