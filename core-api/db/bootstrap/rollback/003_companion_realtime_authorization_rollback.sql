begin;

drop policy if exists "bodeul participants can receive companion broadcasts"
    on realtime.messages;
drop function if exists bodeul_realtime_auth.can_receive_companion_broadcast();
drop table if exists bodeul_realtime_auth.allowed_firebase_projects;
drop schema if exists bodeul_realtime_auth;

commit;
