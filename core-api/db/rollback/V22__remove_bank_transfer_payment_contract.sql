begin;
set local role bodeul_migration;

lock table
    bodeul.appointment_requests,
    bodeul.appointment_bank_transfer_payments,
    bodeul.appointment_payment_events
in access exclusive mode;

do $$
begin
    if exists (
        select 1
        from bodeul.appointment_requests
        where payment_method_code = 'BANK_TRANSFER'
           or create_request_fingerprint is not null
    ) or exists (
        select 1 from bodeul.appointment_bank_transfer_payments
    ) or exists (
        select 1 from bodeul.appointment_payment_events
    ) then
        raise exception '무통장입금 예약·감사 이력 또는 생성 요청 지문이 남아 있어 V22 롤백을 중단합니다.'
            using errcode = '55000';
    end if;
end;
$$;

revoke execute on function bodeul.account_deletion_postgres_inventory(uuid)
    from bodeul_core_runtime, bodeul_admin_runtime;
drop function bodeul.account_deletion_postgres_inventory(uuid);

drop trigger if exists appointment_create_request_fingerprint_guard
    on bodeul.appointment_requests;
drop function if exists bodeul.guard_appointment_create_request_fingerprint();

drop trigger if exists appointment_bank_transfer_cancel_transition
    on bodeul.appointment_requests;
drop function if exists bodeul.transition_canceled_bank_transfer_payment();

drop trigger if exists appointment_bank_transfer_update_guard
    on bodeul.appointment_requests;
drop function if exists bodeul.guard_bank_transfer_appointment_update();

drop trigger if exists appointment_bank_transfer_payment_initialize
    on bodeul.appointment_requests;
drop function if exists bodeul.initialize_bank_transfer_payment();

revoke execute on function bodeul.transition_appointment_bank_transfer_payment(
    uuid, uuid, uuid, bigint, text, integer, text
) from bodeul_admin_runtime;
drop function if exists bodeul.transition_appointment_bank_transfer_payment(
    uuid, uuid, uuid, bigint, text, integer, text
);

revoke execute on function bodeul.set_bank_transfer_depositor(uuid, uuid, uuid, bigint, text)
    from bodeul_core_runtime;
drop function if exists bodeul.set_bank_transfer_depositor(uuid, uuid, uuid, bigint, text);

revoke execute on function bodeul.get_bank_transfer_payment(uuid, uuid)
    from bodeul_core_runtime;
drop function if exists bodeul.get_bank_transfer_payment(uuid, uuid);

drop table bodeul.appointment_payment_events;
drop table bodeul.appointment_bank_transfer_payments;

alter table bodeul.appointment_requests
    drop constraint ck_appointment_requests_create_request_fingerprint,
    drop column create_request_fingerprint,
    drop constraint ck_appointment_requests_payment_contract,
    drop constraint ck_appointment_requests_payment_method,
    drop constraint ck_appointment_requests_payment_status,
    add constraint ck_appointment_requests_payment_method
        check (payment_method_code in ('CARD', 'EASY_PAY', 'ON_SITE')),
    add constraint ck_appointment_requests_payment_status
        check (payment_status_code in ('PENDING', 'AUTHORIZED', 'DEFERRED'));

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

create or replace function bodeul.assign_companion_session(
    p_appointment_request_id uuid,
    p_manager_user_id uuid,
    p_actor_admin_user_id uuid,
    p_expected_appointment_version bigint,
    p_reason text default ''
) returns uuid
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_appointment_status text;
    v_appointment_version bigint;
    v_previous_manager_user_id uuid;
    v_hospital_name text;
    v_department_name text;
    v_guide_id uuid;
    v_guide_revision bigint;
    v_guide_step_contract_version smallint;
    v_guide_steps jsonb;
    v_snapshot_source text;
    v_session_id uuid;
begin
    if p_expected_appointment_version is null or p_expected_appointment_version < 0 then
        raise exception '예약 버전이 필요합니다.' using errcode = '22023';
    end if;

    if not exists (
        select 1 from bodeul.app_users
        where id = p_actor_admin_user_id and role = 'ADMIN'
    ) then
        raise exception '관리자 권한을 확인할 수 없습니다.' using errcode = '42501';
    end if;

    if not exists (
        select 1 from bodeul.app_users
        where id = p_manager_user_id and role = 'MANAGER'
    ) then
        raise exception '배정 대상 매니저를 확인할 수 없습니다.' using errcode = '23503';
    end if;

    select
        status,
        version,
        manager_user_id,
        hospital_name,
        department_name
    into
        v_appointment_status,
        v_appointment_version,
        v_previous_manager_user_id,
        v_hospital_name,
        v_department_name
    from bodeul.appointment_requests
    where id = p_appointment_request_id
    for update;

    if not found then
        raise exception '예약을 찾을 수 없습니다.' using errcode = 'P0002';
    end if;
    if v_appointment_status <> 'REQUESTED' then
        raise exception '요청 상태의 예약만 매칭할 수 있습니다.' using errcode = 'P0001';
    end if;
    if v_appointment_version <> p_expected_appointment_version then
        raise exception '예약이 다른 요청에서 변경되었습니다.' using errcode = '40001';
    end if;

    select
        guide.id,
        guide.revision,
        guide.steps,
        guide.step_contract_version
    into
        v_guide_id,
        v_guide_revision,
        v_guide_steps,
        v_guide_step_contract_version
    from bodeul.hospital_guides guide
    where guide.hospital_name = v_hospital_name
      and guide.department_name = v_department_name;

    if found then
        v_snapshot_source := case v_guide_step_contract_version
            when 1 then 'HOSPITAL_GUIDE_STEP_CODE_V1'
            else 'LEGACY_HOSPITAL_GUIDE_V0'
        end;
    else
        v_guide_id := null;
        v_guide_revision := null;
        v_guide_step_contract_version := null;
        v_guide_steps := '[]'::jsonb;
        v_snapshot_source := 'GUIDE_NOT_FOUND';
    end if;

    insert into bodeul.companion_sessions (
        appointment_request_id,
        manager_user_id,
        current_status,
        guide_id,
        guide_revision,
        guide_step_contract_version,
        guide_steps_snapshot,
        guide_snapshot_source,
        created_at,
        updated_at
    ) values (
        p_appointment_request_id,
        p_manager_user_id,
        'READY',
        v_guide_id,
        v_guide_revision,
        v_guide_step_contract_version,
        v_guide_steps,
        v_snapshot_source,
        now(),
        now()
    )
    returning id into v_session_id;

    update bodeul.appointment_requests
    set manager_user_id = p_manager_user_id,
        status = 'MATCHED',
        updated_at = now(),
        version = version + 1
    where id = p_appointment_request_id;

    insert into bodeul.companion_session_assignment_audits (
        appointment_request_id,
        companion_session_id,
        previous_manager_user_id,
        assigned_manager_user_id,
        actor_admin_user_id,
        reason
    ) values (
        p_appointment_request_id,
        v_session_id,
        v_previous_manager_user_id,
        p_manager_user_id,
        p_actor_admin_user_id,
        coalesce(p_reason, '')
    );

    return v_session_id;
end;
$$;

alter function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    owner to bodeul_migration;
revoke all on function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.assign_companion_session(uuid, uuid, uuid, bigint, text)
    to bodeul_admin_runtime;

commit;
