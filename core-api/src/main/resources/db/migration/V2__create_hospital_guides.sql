create table bodeul.hospital_guides (
    id uuid primary key default gen_random_uuid(),
    hospital_name text not null,
    department_name text not null,
    steps jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_hospital_guides_hospital_department
        unique (hospital_name, department_name),
    constraint ck_hospital_guides_steps_array
        check (jsonb_typeof(steps) = 'array')
);

comment on table bodeul.hospital_guides is '병원과 진료과별 동행 단계 안내 read model';
comment on column bodeul.hospital_guides.steps is '순서, 제목, 설명을 포함하는 JSON 배열';

revoke all on table bodeul.hospital_guides from public, anon, authenticated, service_role;
grant select on table bodeul.hospital_guides to bodeul_core_runtime, bodeul_admin_runtime;

alter table bodeul.hospital_guides enable row level security;

create policy hospital_guides_core_select
    on bodeul.hospital_guides
    for select
    to bodeul_core_runtime
    using (true);

create policy hospital_guides_admin_select
    on bodeul.hospital_guides
    for select
    to bodeul_admin_runtime
    using (true);

create index ix_hospital_guides_admin_list
    on bodeul.hospital_guides (updated_at desc, hospital_name, department_name);
