drop index if exists bodeul.ix_assignment_audits_actor_admin;
drop index if exists bodeul.ix_assignment_audits_assigned_manager;
drop index if exists bodeul.ix_assignment_audits_previous_manager;
drop index if exists bodeul.ix_assignment_audits_session;
drop index if exists bodeul.ix_appointment_follow_ups_support_actor;
drop index if exists bodeul.ix_appointment_follow_ups_settlement_actor;
drop index if exists bodeul.ix_appointment_follow_ups_review_actor;

drop policy if exists session_reports_core_update on bodeul.session_reports;
drop policy if exists session_reports_core_insert on bodeul.session_reports;
drop policy if exists companion_sessions_core_update on bodeul.companion_sessions;

revoke update (
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
) on table bodeul.session_reports from bodeul_core_runtime;

revoke insert (
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
) on table bodeul.session_reports from bodeul_core_runtime;

revoke update (
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
) on table bodeul.companion_sessions from bodeul_core_runtime;
