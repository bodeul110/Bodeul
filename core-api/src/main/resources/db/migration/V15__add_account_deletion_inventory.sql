create function bodeul.account_deletion_postgres_inventory(p_user_id uuid)
returns table (
    profile_count bigint,
    appointment_count bigint,
    active_appointment_count bigint,
    companion_session_count bigint,
    active_companion_session_count bigint,
    session_report_count bigint,
    appointment_follow_up_count bigint,
    assignment_audit_count bigint,
    related_chat_message_count bigint,
    sent_chat_message_count bigint,
    related_chat_attachment_count bigint,
    related_chat_read_receipt_count bigint,
    related_location_count bigint,
    active_legal_hold_count bigint
)
language sql
stable
security definer
set search_path = bodeul, pg_temp
as $$
    with related_appointments as (
        select appointment.id, appointment.status
        from bodeul.appointment_requests appointment
        where appointment.patient_user_id = p_user_id
           or appointment.guardian_user_id = p_user_id
           or appointment.manager_user_id = p_user_id
           or appointment.requester_user_id = p_user_id
    ),
    related_sessions as (
        select session.id, session.current_status
        from bodeul.companion_sessions session
        join bodeul.appointment_requests appointment
          on appointment.id = session.appointment_request_id
        where session.manager_user_id = p_user_id
           or appointment.patient_user_id = p_user_id
           or appointment.guardian_user_id = p_user_id
           or appointment.manager_user_id = p_user_id
           or appointment.requester_user_id = p_user_id
    ),
    related_messages as (
        select message.id, message.legal_hold_until
        from bodeul.companion_chat_messages message
        where message.companion_session_id in (select session.id from related_sessions session)
           or message.sender_user_id = p_user_id
    ),
    related_attachments as (
        select attachment.id, attachment.legal_hold_until
        from bodeul.companion_chat_attachments attachment
        where attachment.chat_message_id in (select message.id from related_messages message)
    ),
    related_read_receipts as (
        select receipt.companion_session_id, receipt.user_id
        from bodeul.companion_chat_read_receipts receipt
        where receipt.companion_session_id in (select session.id from related_sessions session)
           or receipt.user_id = p_user_id
           or receipt.last_read_message_id in (select message.id from related_messages message)
    ),
    related_locations as (
        select location.id, location.legal_hold_until
        from bodeul.companion_session_locations location
        where location.companion_session_id in (select session.id from related_sessions session)
           or location.manager_user_id = p_user_id
    ),
    related_follow_ups as (
        select follow_up.appointment_request_id
        from bodeul.appointment_follow_ups follow_up
        where follow_up.appointment_request_id in (
                select appointment.id from related_appointments appointment
            )
           or follow_up.review_saved_by_user_id = p_user_id
           or follow_up.settlement_follow_up_saved_by_user_id = p_user_id
           or follow_up.support_escalated_by_user_id = p_user_id
    ),
    active_legal_holds as (
        select message.id
        from related_messages message
        where message.legal_hold_until > now()
        union all
        select attachment.id
        from related_attachments attachment
        where attachment.legal_hold_until > now()
        union all
        select location.id
        from related_locations location
        where location.legal_hold_until > now()
    )
    select
        (select count(*) from bodeul.app_users app_user where app_user.id = p_user_id),
        (select count(*) from related_appointments),
        (select count(*) from related_appointments appointment
            where appointment.status not in ('COMPLETED', 'CANCELED')),
        (select count(*) from related_sessions),
        (select count(*) from related_sessions session
            where session.current_status not in ('COMPLETED', 'CANCELED')),
        (select count(*) from bodeul.session_reports report
            where report.companion_session_id in (select session.id from related_sessions session)),
        (select count(*) from related_follow_ups),
        (select count(*) from bodeul.companion_session_assignment_audits audit
            where audit.appointment_request_id in (
                    select appointment.id from related_appointments appointment
                )
               or audit.companion_session_id in (select session.id from related_sessions session)
               or audit.previous_manager_user_id = p_user_id
               or audit.assigned_manager_user_id = p_user_id
               or audit.actor_admin_user_id = p_user_id),
        (select count(*) from related_messages),
        (select count(*) from bodeul.companion_chat_messages message
            where message.sender_user_id = p_user_id),
        (select count(*) from related_attachments),
        (select count(*) from related_read_receipts),
        (select count(*) from related_locations),
        (select count(*) from active_legal_holds);
$$;

comment on function bodeul.account_deletion_postgres_inventory(uuid)
    is '계정 삭제 실행 없이 본인 연관 PostgreSQL 데이터 건수와 기술적 차단 사실만 조회하는 함수';

alter function bodeul.account_deletion_postgres_inventory(uuid) owner to bodeul_migration;

revoke all on function bodeul.account_deletion_postgres_inventory(uuid)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.account_deletion_postgres_inventory(uuid)
    to bodeul_core_runtime, bodeul_admin_runtime;
