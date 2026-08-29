create table bodeul.guardian_sharing_consent_settings (
    singleton boolean primary key default true,
    policy_version text not null,
    location_sharing_enabled boolean not null default false,
    updated_at timestamptz not null default now(),
    constraint ck_guardian_sharing_consent_settings_singleton
        check (singleton),
    constraint ck_guardian_sharing_consent_settings_policy_version
        check (btrim(policy_version) <> '' and char_length(policy_version) <= 64)
);

insert into bodeul.guardian_sharing_consent_settings (
    singleton,
    policy_version,
    location_sharing_enabled
) values (
    true,
    'adult-guardian-sharing-v1',
    false
);

create table bodeul.guardian_sharing_consents (
    id uuid primary key default gen_random_uuid(),
    appointment_request_id uuid not null,
    patient_user_id uuid not null,
    guardian_user_id uuid not null,
    scopes jsonb not null,
    policy_version text not null,
    granted_by_user_id uuid not null,
    adult_self_declared_at timestamptz not null,
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    care_ended_at timestamptz,
    expiry_finalized boolean not null default false,
    revoked_by_user_id uuid,
    revoked_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_guardian_sharing_consents_appointment
        unique (appointment_request_id),
    constraint fk_guardian_sharing_consents_appointment
        foreign key (appointment_request_id)
        references bodeul.appointment_requests (id),
    constraint fk_guardian_sharing_consents_patient
        foreign key (patient_user_id)
        references bodeul.app_users (id),
    constraint fk_guardian_sharing_consents_guardian
        foreign key (guardian_user_id)
        references bodeul.app_users (id),
    constraint fk_guardian_sharing_consents_granted_by
        foreign key (granted_by_user_id)
        references bodeul.app_users (id),
    constraint fk_guardian_sharing_consents_revoked_by
        foreign key (revoked_by_user_id)
        references bodeul.app_users (id),
    constraint ck_guardian_sharing_consents_distinct_users
        check (patient_user_id <> guardian_user_id),
    constraint ck_guardian_sharing_consents_patient_grant
        check (patient_user_id = granted_by_user_id),
    constraint ck_guardian_sharing_consents_adult_declaration
        check (adult_self_declared_at = granted_at),
    constraint ck_guardian_sharing_consents_patient_revoke
        check (revoked_by_user_id is null or patient_user_id = revoked_by_user_id),
    constraint ck_guardian_sharing_consents_scopes
        check (
            jsonb_typeof(scopes) = 'array'
            and jsonb_array_length(scopes) between 1 and 5
            and scopes <@ '["APPOINTMENT", "LOCATION", "CHAT", "ATTACHMENT", "REPORT"]'::jsonb
            and (not scopes @> '["ATTACHMENT"]'::jsonb or scopes @> '["CHAT"]'::jsonb)
        ),
    constraint ck_guardian_sharing_consents_policy_version
        check (btrim(policy_version) <> '' and char_length(policy_version) <= 64),
    constraint ck_guardian_sharing_consents_expiry
        check (expires_at > granted_at),
    constraint ck_guardian_sharing_consents_care_boundary_pair
        check (expiry_finalized = (care_ended_at is not null)),
    constraint ck_guardian_sharing_consents_final_expiry
        check (not expiry_finalized or expires_at = care_ended_at + interval '7 days'),
    constraint ck_guardian_sharing_consents_revocation_pair
        check ((revoked_by_user_id is null) = (revoked_at is null)),
    constraint ck_guardian_sharing_consents_revocation_time
        check (revoked_at is null or revoked_at >= granted_at),
    constraint ck_guardian_sharing_consents_version
        check (version >= 0)
);

create table bodeul.guardian_sharing_consent_events (
    id uuid primary key default gen_random_uuid(),
    consent_id uuid not null,
    appointment_request_id uuid not null,
    patient_user_id uuid not null,
    guardian_user_id uuid not null,
    action text not null,
    scopes jsonb not null,
    policy_version text not null,
    actor_user_id uuid not null,
    adult_self_declared_at timestamptz not null,
    occurred_at timestamptz not null,
    consent_version bigint not null,
    constraint fk_guardian_sharing_consent_events_consent
        foreign key (consent_id)
        references bodeul.guardian_sharing_consents (id),
    constraint fk_guardian_sharing_consent_events_appointment
        foreign key (appointment_request_id)
        references bodeul.appointment_requests (id),
    constraint fk_guardian_sharing_consent_events_patient
        foreign key (patient_user_id)
        references bodeul.app_users (id),
    constraint fk_guardian_sharing_consent_events_guardian
        foreign key (guardian_user_id)
        references bodeul.app_users (id),
    constraint fk_guardian_sharing_consent_events_actor
        foreign key (actor_user_id)
        references bodeul.app_users (id),
    constraint ck_guardian_sharing_consent_events_action
        check (action in ('GRANTED', 'REVOKED')),
    constraint ck_guardian_sharing_consent_events_scopes
        check (
            jsonb_typeof(scopes) = 'array'
            and jsonb_array_length(scopes) between 1 and 5
            and scopes <@ '["APPOINTMENT", "LOCATION", "CHAT", "ATTACHMENT", "REPORT"]'::jsonb
            and (not scopes @> '["ATTACHMENT"]'::jsonb or scopes @> '["CHAT"]'::jsonb)
        ),
    constraint ck_guardian_sharing_consent_events_policy_version
        check (btrim(policy_version) <> '' and char_length(policy_version) <= 64),
    constraint ck_guardian_sharing_consent_events_version
        check (consent_version >= 0)
);

comment on table bodeul.guardian_sharing_consents is
    '예약별 성인 환자 보호자 정보공유 동의의 현재 상태';
comment on table bodeul.guardian_sharing_consent_settings is
    'Core API와 Realtime 인가가 함께 사용하는 현재 정책 버전과 기능 플래그 단일 원본';
comment on table bodeul.guardian_sharing_consent_events is
    '동의 부여와 철회를 사후 확인하기 위한 추가 전용 이력';
comment on column bodeul.guardian_sharing_consents.scopes is
    'APPOINTMENT, LOCATION, CHAT, ATTACHMENT, REPORT 중 환자가 선택한 범위';
comment on column bodeul.guardian_sharing_consents.expires_at is
    '동행 종료 전에는 임시 시각이며 완료·취소 뒤 실제 종료 경계에서 7일 뒤로 확정되는 만료 시각';
comment on column bodeul.guardian_sharing_consents.expiry_finalized is
    'false이면 지연 동행 중 임시 만료를 인가 거부 근거로 사용하지 않고 실제 종료·취소 시 확정한다';
comment on column bodeul.guardian_sharing_consents.adult_self_declared_at is
    '생년 정보가 없는 MVP에서 환자가 성인 본인임을 확인한 시각';

create index ix_guardian_sharing_consents_guardian_expiry
    on bodeul.guardian_sharing_consents (guardian_user_id, expires_at)
    where revoked_at is null;

create index ix_guardian_sharing_consent_events_appointment_occurred
    on bodeul.guardian_sharing_consent_events (appointment_request_id, occurred_at desc);

revoke all on table bodeul.guardian_sharing_consents
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
revoke all on table bodeul.guardian_sharing_consent_events
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
revoke all on table bodeul.guardian_sharing_consent_settings
    from public, anon, authenticated, service_role, bodeul_admin_runtime;

grant select, insert, update on table bodeul.guardian_sharing_consents
    to bodeul_core_runtime;
grant select, insert on table bodeul.guardian_sharing_consent_events
    to bodeul_core_runtime;
grant select on table bodeul.guardian_sharing_consent_settings
    to bodeul_core_runtime;

alter table bodeul.guardian_sharing_consents enable row level security;
alter table bodeul.guardian_sharing_consent_events enable row level security;
alter table bodeul.guardian_sharing_consent_settings enable row level security;

create policy guardian_sharing_consent_settings_core_select
    on bodeul.guardian_sharing_consent_settings
    for select
    to bodeul_core_runtime
    using (singleton);

create policy guardian_sharing_consents_core_select
    on bodeul.guardian_sharing_consents
    for select
    to bodeul_core_runtime
    using (true);

create policy guardian_sharing_consents_core_insert
    on bodeul.guardian_sharing_consents
    for insert
    to bodeul_core_runtime
    with check (true);

create policy guardian_sharing_consents_core_update
    on bodeul.guardian_sharing_consents
    for update
    to bodeul_core_runtime
    using (true)
    with check (true);

create policy guardian_sharing_consent_events_core_select
    on bodeul.guardian_sharing_consent_events
    for select
    to bodeul_core_runtime
    using (true);

create policy guardian_sharing_consent_events_core_insert
    on bodeul.guardian_sharing_consent_events
    for insert
    to bodeul_core_runtime
    with check (true);
