set local role bodeul_migration;

create function bodeul.get_admin_bank_transfer_payment(
    p_actor_admin_user_id uuid,
    p_appointment_request_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_admin_role text;
    v_payment jsonb;
begin
    select assignment.admin_role into v_admin_role
    from bodeul.admin_role_assignments assignment
    join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
    where assignment.admin_user_id = p_actor_admin_user_id
      and assignment.revoked_at is null
      and assignment.admin_role in ('SUPER_ADMIN', 'OPERATIONS')
      and app_user.role = 'ADMIN'
    for share of assignment, app_user;

    if v_admin_role is null then
        raise exception '결제 조회에는 운영 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;
    if p_appointment_request_id is null then
        raise exception '조회할 예약 ID가 필요합니다.' using errcode = '22023';
    end if;

    -- 입금 대조에 필요한 필드만 반환하며 환자 연락처와 작업 지문은 포함하지 않는다.
    select jsonb_build_object(
        'appointmentRequestId', appointment.id,
        'publicCode', appointment.public_code,
        'appointmentStatus', appointment.status,
        'paymentStatusCode', appointment.payment_status_code,
        'expectedAmount', payment.expected_amount,
        'depositorName', payment.depositor_name,
        'paymentDueAt', payment.payment_due_at,
        'receivedAmount', payment.received_amount,
        'confirmedByAdminUserId', payment.confirmed_by_admin_user_id,
        'confirmedAt', payment.confirmed_at,
        'refundRequestedAt', payment.refund_requested_at,
        'refundedAt', payment.refunded_at,
        'paymentVersion', payment.payment_version,
        'events', history.events,
        'hasMoreEvents', history.has_more
    ) into v_payment
    from bodeul.appointment_requests appointment
    join bodeul.appointment_bank_transfer_payments payment
      on payment.appointment_request_id = appointment.id
    cross join lateral (
        select coalesce(jsonb_agg(jsonb_build_object(
            'id', recent.id,
            'actorUserId', recent.actor_user_id,
            'actorRole', recent.actor_role,
            'eventType', recent.event_type,
            'previousStatusCode', recent.previous_status_code,
            'nextStatusCode', recent.next_status_code,
            'receivedAmount', recent.received_amount,
            'reason', recent.reason,
            'createdAt', recent.created_at
        ) order by recent.created_at desc, recent.id desc)
            filter (where recent.position <= 20), '[]'::jsonb) as events,
            count(*) > 20 as has_more
        from (
            select event.*, row_number() over (order by event.created_at desc, event.id desc) as position
            from bodeul.appointment_payment_events event
            where event.appointment_request_id = appointment.id
            order by event.created_at desc, event.id desc
            limit 21
        ) recent
    ) history
    where appointment.id = p_appointment_request_id
      and appointment.payment_method_code = 'BANK_TRANSFER';

    if v_payment is null then
        raise exception '무통장입금 정보를 찾을 수 없습니다.' using errcode = 'P0002';
    end if;

    perform bodeul.record_admin_access_audit(
        p_actor_admin_user_id, 'RAW_VIEW', 'APPOINTMENT_PAYMENT',
        p_appointment_request_id::text,
        '예약 결제 대조를 위한 입금자명과 처리 이력 조회',
        'ALLOWED', '{}'::jsonb
    );
    return v_payment;
end;
$$;

alter function bodeul.get_admin_bank_transfer_payment(uuid, uuid) owner to bodeul_migration;
revoke all on function bodeul.get_admin_bank_transfer_payment(uuid, uuid)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.get_admin_bank_transfer_payment(uuid, uuid) to bodeul_admin_runtime;

comment on function bodeul.get_admin_bank_transfer_payment(uuid, uuid) is
    '운영 관리자 전용 결제 상세·최근 20개 이력 조회. 원장 직접 접근 없이 조회 감사를 남긴다.';
