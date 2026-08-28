alter table bodeul.companion_sessions
    add column pre_consultation_confirmed boolean not null default false;

comment on column bodeul.companion_sessions.pre_consultation_confirmed is
    'PRE_CONSULTATION 단계에서 증상, 질문과 전달 사항을 확인했는지 나타내는 재진입용 상태';

grant update (pre_consultation_confirmed)
    on table bodeul.companion_sessions to bodeul_core_runtime;
