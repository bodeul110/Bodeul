-- BoDeul PostgreSQL schema draft
-- 기준일: 2026-06-26
-- 목적: Firestore 운영 데이터를 Supabase PostgreSQL로 옮기기 위한 초안이다.
-- 주의: 이 파일은 운영 DB에 바로 적용하지 않는다. 실제 migration은 api/ 또는 db/migrations/에서 별도 관리한다.

create extension if not exists pgcrypto;

create table if not exists app_users (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text unique not null,
  role text not null check (role in ('PATIENT', 'GUARDIAN', 'MANAGER', 'ADMIN')),
  name text not null default '',
  email text not null default '',
  phone text not null default '',
  manager_document_status text,
  manager_document_review_note text not null default '',
  manager_document_updated_at timestamptz,
  manager_document_reviewed_at timestamptz,
  manager_document_reviewed_by_name text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists manager_document_files (
  id uuid primary key default gen_random_uuid(),
  manager_user_id uuid not null references app_users(id),
  document_key text not null,
  storage_provider text not null default 'firebase_storage',
  storage_path text not null,
  file_name text not null default '',
  content_type text not null default '',
  uploaded_at timestamptz,
  created_at timestamptz not null default now(),
  unique (manager_user_id, document_key, storage_path)
);

create table if not exists manager_document_reviews (
  id uuid primary key default gen_random_uuid(),
  manager_user_id uuid not null references app_users(id),
  status text not null check (status in ('APPROVED', 'REJECTED', 'PENDING_REVIEW', 'UNDER_REVIEW')),
  review_note text not null default '',
  reviewed_by_user_id uuid references app_users(id),
  reviewed_by_name text not null default '',
  reviewed_at timestamptz not null default now()
);

create table if not exists hospital_guides (
  id uuid primary key default gen_random_uuid(),
  hospital_name text not null,
  department_name text not null,
  steps jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (hospital_name, department_name)
);

create table if not exists appointment_requests (
  id uuid primary key default gen_random_uuid(),
  firestore_id text unique,
  patient_user_id uuid references app_users(id),
  guardian_user_id uuid references app_users(id),
  manager_user_id uuid references app_users(id),
  requester_user_id uuid references app_users(id),
  requester_role text not null default '',
  patient_name text not null default '',
  patient_phone text not null default '',
  guardian_name text not null default '',
  guardian_phone text not null default '',
  hospital_name text not null default '',
  department_name text not null default '',
  hospital_latitude numeric(10, 7),
  hospital_longitude numeric(10, 7),
  appointment_at timestamptz,
  appointment_date_key text not null default '',
  meeting_place text not null default '',
  special_notes text not null default '',
  status text not null,
  base_price integer not null default 0,
  option_surcharge_price integer not null default 0,
  coupon_discount_price integer not null default 0,
  final_price integer not null default 0,
  payment_method_code text not null default '',
  payment_status_code text not null default '',
  payment_approval_code text not null default '',
  payment_approved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists companion_sessions (
  id uuid primary key default gen_random_uuid(),
  firestore_id text unique,
  appointment_request_id uuid not null references appointment_requests(id),
  manager_user_id uuid references app_users(id),
  current_step_order integer not null default 0,
  current_status text not null,
  guardian_update text not null default '',
  location_summary text not null default '',
  field_photo_note text not null default '',
  medication_note text not null default '',
  pharmacy_summary text not null default '',
  prescription_collected boolean not null default false,
  pharmacy_completed boolean not null default false,
  medication_guidance_completed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists session_reports (
  id uuid primary key default gen_random_uuid(),
  firestore_id text unique,
  companion_session_id uuid not null references companion_sessions(id),
  summary text not null default '',
  treatment_notes text not null default '',
  medication_notes text not null default '',
  medication_name text not null default '',
  medication_change_summary text not null default '',
  medication_schedule_note text not null default '',
  next_visit_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists appointment_follow_ups (
  appointment_request_id uuid primary key references appointment_requests(id),
  review_rating_code text not null default '',
  review_comment text not null default '',
  review_saved_at timestamptz,
  settlement_follow_up_status text not null default '',
  settlement_follow_up_note text not null default '',
  settlement_follow_up_saved_at timestamptz,
  support_escalation_status text not null default '',
  support_escalated_at timestamptz,
  updated_at timestamptz not null default now()
);

create table if not exists support_requests (
  id uuid primary key default gen_random_uuid(),
  firestore_id text unique,
  requester_user_id uuid references app_users(id),
  requester_role text not null default '',
  appointment_request_id uuid references appointment_requests(id),
  category_code text not null default '',
  title text not null default '',
  body text not null default '',
  status_code text not null default '',
  response_text text not null default '',
  responded_by_user_id uuid references app_users(id),
  responded_by_name text not null default '',
  responded_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references app_users(id),
  actor_name text not null default '',
  source_type text not null default '',
  request_id uuid references appointment_requests(id),
  inquiry_id uuid references support_requests(id),
  action_summary text not null,
  note text not null default '',
  created_at timestamptz not null default now()
);

create index if not exists idx_app_users_role on app_users(role);
create index if not exists idx_appointment_requests_status_at on appointment_requests(status, appointment_at);
create index if not exists idx_appointment_requests_manager on appointment_requests(manager_user_id, appointment_at);
create index if not exists idx_companion_sessions_request on companion_sessions(appointment_request_id);
create index if not exists idx_support_requests_status_created on support_requests(status_code, created_at desc);
create index if not exists idx_admin_audit_logs_created on admin_audit_logs(created_at desc);
