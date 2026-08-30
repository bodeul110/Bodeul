-- V18 적용 뒤 매니저의 Realtime 구독 권한을 실제 동행 종료 시점에 회수한다.
-- Supabase SQL Editor 또는 postgres 권한 연결에서 실행한다.

begin;

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
          and (
              app_user.id = appointment.patient_user_id
              or (
                  app_user.id = session.manager_user_id
                  and session.care_ended_at is null
                  and session.current_status not in ('CARE_ENDED', 'COMPLETED', 'CANCELED')
              )
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

comment on function bodeul_realtime_auth.can_receive_companion_broadcast() is
    'Firebase JWT와 세션 참여 관계를 확인하고 보호자 및 동행 종료 매니저의 Broadcast 구독을 거부하는 Realtime helper';

commit;
