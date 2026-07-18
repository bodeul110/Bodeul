grant update (
    current_step_order,
    current_status,
    guardian_update,
    location_summary,
    field_photo_note,
    medication_note,
    pharmacy_summary,
    prescription_collected,
    pharmacy_completed,
    medication_guidance_completed,
    version,
    started_at,
    completed_at,
    canceled_at,
    updated_at
) on table bodeul.companion_sessions to bodeul_core_runtime;

grant insert (
    companion_session_id,
    summary,
    treatment_notes,
    medication_notes,
    medication_name,
    medication_change_summary,
    medication_schedule_note,
    medication_comparison_decision_code,
    medication_comparison_note,
    next_visit_at,
    next_visit_note
) on table bodeul.session_reports to bodeul_core_runtime;

grant update (
    summary,
    treatment_notes,
    medication_notes,
    medication_name,
    medication_change_summary,
    medication_schedule_note,
    medication_comparison_decision_code,
    medication_comparison_note,
    next_visit_at,
    next_visit_note,
    version,
    updated_at
) on table bodeul.session_reports to bodeul_core_runtime;

create policy companion_sessions_core_update
    on bodeul.companion_sessions
    for update
    to bodeul_core_runtime
    using (true)
    with check (true);

create policy session_reports_core_insert
    on bodeul.session_reports
    for insert
    to bodeul_core_runtime
    with check (true);

create policy session_reports_core_update
    on bodeul.session_reports
    for update
    to bodeul_core_runtime
    using (true)
    with check (true);

create index ix_appointment_follow_ups_review_actor
    on bodeul.appointment_follow_ups (review_saved_by_user_id)
    where review_saved_by_user_id is not null;

create index ix_appointment_follow_ups_settlement_actor
    on bodeul.appointment_follow_ups (settlement_follow_up_saved_by_user_id)
    where settlement_follow_up_saved_by_user_id is not null;

create index ix_appointment_follow_ups_support_actor
    on bodeul.appointment_follow_ups (support_escalated_by_user_id)
    where support_escalated_by_user_id is not null;

create index ix_assignment_audits_session
    on bodeul.companion_session_assignment_audits (companion_session_id);

create index ix_assignment_audits_previous_manager
    on bodeul.companion_session_assignment_audits (previous_manager_user_id)
    where previous_manager_user_id is not null;

create index ix_assignment_audits_assigned_manager
    on bodeul.companion_session_assignment_audits (assigned_manager_user_id);

create index ix_assignment_audits_actor_admin
    on bodeul.companion_session_assignment_audits (actor_admin_user_id);

comment on policy companion_sessions_core_update on bodeul.companion_sessions is
    'Firebase 인증과 배정 관계를 검증한 Core API만 동행 진행 정보를 갱신한다';
comment on policy session_reports_core_insert on bodeul.session_reports is
    '배정된 매니저 요청을 검증한 Core API만 동행 리포트를 생성한다';
comment on policy session_reports_core_update on bodeul.session_reports is
    '배정된 매니저 요청을 검증한 Core API만 동행 리포트를 수정한다';
