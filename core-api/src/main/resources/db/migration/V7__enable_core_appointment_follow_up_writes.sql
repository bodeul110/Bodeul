grant insert (
    appointment_request_id,
    review_rating_code,
    review_comment,
    review_saved_by_user_id,
    review_saved_at,
    settlement_follow_up_status,
    settlement_follow_up_note,
    settlement_follow_up_saved_by_user_id,
    settlement_follow_up_saved_at,
    support_escalation_status,
    support_escalated_by_user_id,
    support_escalated_at,
    version,
    updated_at
) on table bodeul.appointment_follow_ups to bodeul_core_runtime;

grant update (
    review_rating_code,
    review_comment,
    review_saved_by_user_id,
    review_saved_at,
    settlement_follow_up_status,
    settlement_follow_up_note,
    settlement_follow_up_saved_by_user_id,
    settlement_follow_up_saved_at,
    support_escalation_status,
    support_escalated_by_user_id,
    support_escalated_at,
    version,
    updated_at
) on table bodeul.appointment_follow_ups to bodeul_core_runtime;

create policy appointment_follow_ups_core_insert
    on bodeul.appointment_follow_ups
    for insert
    to bodeul_core_runtime
    with check (true);

create policy appointment_follow_ups_core_update
    on bodeul.appointment_follow_ups
    for update
    to bodeul_core_runtime
    using (true)
    with check (true);

comment on policy appointment_follow_ups_core_insert on bodeul.appointment_follow_ups is
    'Firebase 인증과 예약 참여 관계를 검증한 Core API만 예약 후속 기록을 생성한다';
comment on policy appointment_follow_ups_core_update on bodeul.appointment_follow_ups is
    'Firebase 인증과 예약 참여 관계를 검증한 Core API만 예약 후속 기록을 수정한다';
