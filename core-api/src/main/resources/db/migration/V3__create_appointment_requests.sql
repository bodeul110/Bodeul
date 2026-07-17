create table bodeul.appointment_requests (
    id uuid primary key default gen_random_uuid(),
    firestore_id text not null,
    patient_user_id uuid,
    guardian_user_id uuid,
    manager_user_id uuid,
    requester_user_id uuid not null,
    requester_role text not null,
    patient_name text not null default '',
    patient_phone text not null default '',
    patient_email text not null default '',
    guardian_name text not null default '',
    guardian_phone text not null default '',
    guardian_email text not null default '',
    requester_name text not null default '',
    requester_phone text not null default '',
    hospital_name text not null,
    department_name text not null,
    hospital_latitude numeric(10, 7),
    hospital_longitude numeric(10, 7),
    appointment_at timestamptz not null,
    appointment_at_epoch_millis bigint not null,
    appointment_date_key text not null,
    meeting_place text not null default '',
    special_notes text not null default '',
    patient_condition_summary text not null default '',
    medication_summary text not null default '',
    mobility_support_code text not null,
    trip_type_code text not null,
    manager_gender_preference_code text not null,
    status text not null,
    base_price integer not null default 0,
    option_surcharge_price integer not null default 0,
    coupon_discount_price integer not null default 0,
    final_price integer not null default 0,
    payment_method_code text not null,
    coupon_code text not null,
    payment_status_code text not null,
    payment_approval_code text not null default '',
    payment_approved_at timestamptz,
    payment_provider_label text not null default '',
    reminder_stages jsonb not null default '[]'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz,
    imported_at timestamptz not null default now(),
    constraint uq_appointment_requests_firestore_id unique (firestore_id),
    constraint fk_appointment_requests_patient_user
        foreign key (patient_user_id) references bodeul.app_users (id),
    constraint fk_appointment_requests_guardian_user
        foreign key (guardian_user_id) references bodeul.app_users (id),
    constraint fk_appointment_requests_manager_user
        foreign key (manager_user_id) references bodeul.app_users (id),
    constraint fk_appointment_requests_requester_user
        foreign key (requester_user_id) references bodeul.app_users (id),
    constraint ck_appointment_requests_firestore_id
        check (btrim(firestore_id) <> ''),
    constraint ck_appointment_requests_requester_role
        check (requester_role in ('PATIENT', 'GUARDIAN')),
    constraint ck_appointment_requests_hospital_name
        check (btrim(hospital_name) <> ''),
    constraint ck_appointment_requests_department_name
        check (btrim(department_name) <> ''),
    constraint ck_appointment_requests_latitude
        check (hospital_latitude is null or hospital_latitude between -90 and 90),
    constraint ck_appointment_requests_longitude
        check (hospital_longitude is null or hospital_longitude between -180 and 180),
    constraint ck_appointment_requests_appointment_epoch
        check (appointment_at_epoch_millis > 0),
    constraint ck_appointment_requests_date_key
        check (appointment_date_key ~ '^\d{4}-\d{2}-\d{2}$'),
    constraint ck_appointment_requests_mobility_support
        check (mobility_support_code in ('INDEPENDENT', 'WALKING_AID', 'WHEELCHAIR')),
    constraint ck_appointment_requests_trip_type
        check (trip_type_code in ('ONE_WAY', 'ROUND_TRIP')),
    constraint ck_appointment_requests_manager_gender
        check (manager_gender_preference_code in ('ANY', 'FEMALE', 'MALE')),
    constraint ck_appointment_requests_status
        check (status in ('REQUESTED', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
    constraint ck_appointment_requests_payment_method
        check (payment_method_code in ('CARD', 'EASY_PAY', 'ON_SITE')),
    constraint ck_appointment_requests_coupon
        check (coupon_code in ('NONE', 'FIRST_VISIT', 'FAMILY')),
    constraint ck_appointment_requests_payment_status
        check (payment_status_code in ('PENDING', 'AUTHORIZED', 'DEFERRED')),
    constraint ck_appointment_requests_prices_nonnegative
        check (
            base_price >= 0
            and option_surcharge_price >= 0
            and coupon_discount_price >= 0
            and final_price >= 0
        ),
    constraint ck_appointment_requests_price_formula
        check (final_price = base_price + option_surcharge_price - coupon_discount_price),
    constraint ck_appointment_requests_reminder_stages_array
        check (jsonb_typeof(reminder_stages) = 'array')
);

comment on table bodeul.appointment_requests is 'Firestore 예약 요청을 PostgreSQL로 검증하기 위한 서버 전용 read model';
comment on column bodeul.appointment_requests.firestore_id is '원본 Firestore appointmentRequests 문서 ID';
comment on column bodeul.appointment_requests.imported_at is '백업 기반 row가 PostgreSQL에 마지막으로 적재된 시각';

revoke all on table bodeul.appointment_requests from public, anon, authenticated, service_role;
grant select on table bodeul.appointment_requests to bodeul_core_runtime, bodeul_admin_runtime;

alter table bodeul.appointment_requests enable row level security;

create policy appointment_requests_core_select
    on bodeul.appointment_requests
    for select
    to bodeul_core_runtime
    using (true);

create policy appointment_requests_admin_select
    on bodeul.appointment_requests
    for select
    to bodeul_admin_runtime
    using (true);

create index ix_appointment_requests_patient_schedule
    on bodeul.appointment_requests (patient_user_id, appointment_at desc);

create index ix_appointment_requests_guardian_schedule
    on bodeul.appointment_requests (guardian_user_id, appointment_at desc);

create index ix_appointment_requests_manager_queue
    on bodeul.appointment_requests (manager_user_id, status, appointment_at);

create index ix_appointment_requests_status_schedule
    on bodeul.appointment_requests (status, appointment_at);

create index ix_appointment_requests_requester_created
    on bodeul.appointment_requests (requester_user_id, created_at desc);
