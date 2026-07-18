begin;
set local role bodeul_migration;

drop function if exists bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text);
drop table if exists bodeul.companion_session_assignment_audits;
drop table if exists bodeul.appointment_follow_ups;
drop table if exists bodeul.session_reports;
drop table if exists bodeul.companion_sessions;

commit;
