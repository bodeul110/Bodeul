revoke update (
    live_location_sharing_active,
    live_location_sharing_started_at,
    location_alert_stage,
    location_alert_sent_at
) on table bodeul.companion_sessions from bodeul_core_runtime;

alter table bodeul.companion_sessions
    drop constraint if exists ck_companion_sessions_location_alert_stage,
    drop column if exists location_alert_sent_at,
    drop column if exists location_alert_stage,
    drop column if exists live_location_sharing_started_at,
    drop column if exists live_location_sharing_active;
