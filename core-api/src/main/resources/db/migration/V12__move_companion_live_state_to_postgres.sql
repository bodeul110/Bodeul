alter table bodeul.companion_sessions
    add column live_location_sharing_active boolean not null default false,
    add column live_location_sharing_started_at timestamptz,
    add column location_alert_stage text not null default 'none',
    add column location_alert_sent_at timestamptz,
    add constraint ck_companion_sessions_location_alert_stage
        check (location_alert_stage in ('none', 'hospital_near', 'pharmacy_near'));

grant update (
    live_location_sharing_active,
    live_location_sharing_started_at,
    location_alert_stage,
    location_alert_sent_at
) on table bodeul.companion_sessions to bodeul_core_runtime;

comment on column bodeul.companion_sessions.live_location_sharing_active is
    '매니저가 연속 위치 공유를 활성화했는지 나타내는 운영 상태';
comment on column bodeul.companion_sessions.live_location_sharing_started_at is
    '연속 위치 공유를 마지막으로 시작한 서버 시각';
comment on column bodeul.companion_sessions.location_alert_stage is
    '현재 세션에서 발송을 완료한 자동 위치 알림 단계';
comment on column bodeul.companion_sessions.location_alert_sent_at is
    '자동 위치 알림 단계를 마지막으로 변경한 서버 시각';
