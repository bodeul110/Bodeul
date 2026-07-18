-- Supabase Realtime private 채널 구독 권한을 동행 세션 참여 관계로 제한한다.
-- 환경별 Firebase 프로젝트는 bootstrap/environment 파일에서 별도로 등록한다.

begin;

create schema if not exists bodeul_realtime_auth authorization postgres;
alter schema bodeul_realtime_auth owner to postgres;

revoke all on schema bodeul_realtime_auth from public, anon, authenticated, service_role;
grant usage on schema bodeul_realtime_auth to authenticated;

create table if not exists bodeul_realtime_auth.allowed_firebase_projects (
    project_id text primary key,
    constraint ck_allowed_firebase_project_id
        check (project_id ~ '^[a-z][a-z0-9-]{4,28}[a-z0-9]$')
);

alter table bodeul_realtime_auth.allowed_firebase_projects owner to postgres;
revoke all on table bodeul_realtime_auth.allowed_firebase_projects
    from public, anon, authenticated, service_role;

create or replace function bodeul_realtime_auth.can_receive_companion_broadcast()
returns boolean
language plpgsql
stable
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_claims jsonb;
    v_firebase_uid text;
    v_firebase_project_id text;
    v_topic text;
    v_session_id uuid;
begin
    begin
        v_claims := coalesce(
            nullif(current_setting('request.jwt.claims', true), '')::jsonb,
            '{}'::jsonb
        );
    exception
        when others then
            return false;
    end;

    if v_claims ->> 'role' <> 'authenticated' then
        return false;
    end if;

    v_firebase_uid := nullif(btrim(v_claims ->> 'sub'), '');
    v_firebase_project_id := nullif(btrim(v_claims ->> 'aud'), '');
    if v_firebase_uid is null or v_firebase_project_id is null then
        return false;
    end if;

    if not exists (
        select 1
        from bodeul_realtime_auth.allowed_firebase_projects allowed_project
        where allowed_project.project_id = v_firebase_project_id
          and v_claims ->> 'iss' =
              'https://securetoken.google.com/' || allowed_project.project_id
    ) then
        return false;
    end if;

    v_topic := realtime.topic();
    if v_topic is null or v_topic !~
            '^companion-session:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return false;
    end if;

    v_session_id := split_part(v_topic, ':', 2)::uuid;

    return exists (
        select 1
        from bodeul.companion_sessions session
        join bodeul.appointment_requests appointment
          on appointment.id = session.appointment_request_id
        join bodeul.app_users app_user
          on app_user.firebase_uid = v_firebase_uid
        where session.id = v_session_id
          and app_user.id in (
              session.manager_user_id,
              appointment.patient_user_id,
              appointment.guardian_user_id
          )
    );
end;
$$;

alter function bodeul_realtime_auth.can_receive_companion_broadcast() owner to postgres;
revoke all on function bodeul_realtime_auth.can_receive_companion_broadcast()
    from public, anon, authenticated, service_role,
         bodeul_migration, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul_realtime_auth.can_receive_companion_broadcast()
    to authenticated;

drop policy if exists "bodeul participants can receive companion broadcasts"
    on realtime.messages;
create policy "bodeul participants can receive companion broadcasts"
on realtime.messages
for select
to authenticated
using (
    realtime.messages.extension = 'broadcast'
    and (select bodeul_realtime_auth.can_receive_companion_broadcast())
);

comment on schema bodeul_realtime_auth is
    'Supabase Realtime private 채널 연결 시에만 사용하는 비공개 권한 경계';
comment on table bodeul_realtime_auth.allowed_firebase_projects is
    '이 Supabase 환경이 신뢰하도록 명시적으로 등록한 Firebase 프로젝트';
comment on function bodeul_realtime_auth.can_receive_companion_broadcast() is
    'Firebase JWT와 동행 세션 참여 관계를 검증하는 Realtime SELECT 정책 helper';

commit;
