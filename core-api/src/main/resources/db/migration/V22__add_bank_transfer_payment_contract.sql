alter table bodeul.appointment_requests
    add column create_request_fingerprint text,
    add constraint ck_appointment_requests_create_request_fingerprint
        check (
            create_request_fingerprint is null
            or create_request_fingerprint ~ '^[0-9a-f]{64}$'
        );

comment on column bodeul.appointment_requests.create_request_fingerprint is
    '요청자 식별자와 정규화된 클라이언트 예약 생성 입력의 SHA-256 지문. 기존·seed 행은 null이며 재시도를 fail-closed 처리한다.';

create function bodeul.guard_appointment_create_request_fingerprint()
returns trigger
language plpgsql
security invoker
set search_path = pg_catalog, pg_temp
as $$
begin
    if current_user <> 'bodeul_migration'
            and new.create_request_fingerprint is distinct from old.create_request_fingerprint then
        raise exception '예약 생성 요청 지문은 생성 후 변경할 수 없습니다.'
            using errcode = '42501';
    end if;
    return new;
end;
$$;

alter function bodeul.guard_appointment_create_request_fingerprint()
    owner to bodeul_migration;
revoke all on function bodeul.guard_appointment_create_request_fingerprint()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger appointment_create_request_fingerprint_guard
before update of create_request_fingerprint on bodeul.appointment_requests
for each row execute function bodeul.guard_appointment_create_request_fingerprint();

alter table bodeul.appointment_requests
    drop constraint ck_appointment_requests_payment_method,
    drop constraint ck_appointment_requests_payment_status;

alter table bodeul.appointment_requests
    add constraint ck_appointment_requests_payment_method
        check (payment_method_code in ('CARD', 'EASY_PAY', 'ON_SITE', 'BANK_TRANSFER')),
    add constraint ck_appointment_requests_payment_status
        check (payment_status_code in (
            'PENDING', 'AUTHORIZED', 'DEFERRED',
            'AWAITING_DEPOSIT', 'DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED',
            'REFUND_REQUESTED', 'REFUNDED', 'CANCELED'
        )),
    add constraint ck_appointment_requests_payment_contract
        check (
            (
                payment_method_code = 'BANK_TRANSFER'
                and payment_status_code in (
                    'AWAITING_DEPOSIT', 'DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED',
                    'REFUND_REQUESTED', 'REFUNDED', 'CANCELED'
                )
            )
            or (
                payment_method_code <> 'BANK_TRANSFER'
                and payment_status_code in ('PENDING', 'AUTHORIZED', 'DEFERRED')
            )
        );

create table bodeul.appointment_bank_transfer_payments (
    appointment_request_id uuid primary key,
    expected_amount integer not null,
    depositor_name text not null default '',
    payment_due_at timestamptz,
    received_amount integer,
    confirmed_by_admin_user_id uuid,
    confirmed_at timestamptz,
    refund_requested_at timestamptz,
    refunded_at timestamptz,
    payment_version bigint not null default 0,
    updated_at timestamptz not null default now(),
    constraint fk_bank_transfer_payments_appointment
        foreign key (appointment_request_id) references bodeul.appointment_requests (id),
    constraint fk_bank_transfer_payments_confirmer
        foreign key (confirmed_by_admin_user_id) references bodeul.app_users (id),
    constraint ck_bank_transfer_payments_expected_amount
        check (expected_amount >= 0),
    constraint ck_bank_transfer_payments_depositor_name
        check (char_length(depositor_name) <= 100),
    constraint ck_bank_transfer_payments_received_amount
        check (received_amount is null or received_amount >= 0),
    constraint ck_bank_transfer_payments_confirmation
        check (
            (confirmed_by_admin_user_id is null and confirmed_at is null)
            or (confirmed_by_admin_user_id is not null and confirmed_at is not null)
        ),
    constraint ck_bank_transfer_payments_refund_timestamps
        check (refunded_at is null or refund_requested_at is not null),
    constraint ck_bank_transfer_payments_version
        check (payment_version >= 0)
);

comment on table bodeul.appointment_bank_transfer_payments is
    '무통장입금의 입금자명, 금액, 확인과 환불 상세를 보관하는 서버 전용 현재 원장';
comment on column bodeul.appointment_bank_transfer_payments.payment_due_at is
    '운영 설정으로 확정된 입금 기한. 설정 전에는 null이며 앱이 임의 기한을 만들지 않는다.';

create table bodeul.appointment_payment_events (
    id uuid primary key default gen_random_uuid(),
    appointment_request_id uuid not null,
    operation_id uuid,
    actor_user_id uuid,
    actor_role text not null,
    event_type text not null,
    previous_status_code text not null,
    next_status_code text not null,
    expected_payment_version bigint not null,
    received_amount integer,
    reason text not null default '',
    depositor_name_fingerprint text not null default '',
    created_at timestamptz not null default now(),
    constraint fk_appointment_payment_events_appointment
        foreign key (appointment_request_id) references bodeul.appointment_requests (id),
    constraint fk_appointment_payment_events_actor
        foreign key (actor_user_id) references bodeul.app_users (id),
    constraint ck_appointment_payment_events_actor_role
        check (actor_role in ('PATIENT', 'ADMIN', 'SYSTEM')),
    constraint ck_appointment_payment_events_type
        check (event_type in (
            'CREATED', 'DEPOSITOR_UPDATED', 'DEPOSIT_CONFIRMED',
            'REVIEW_REQUIRED', 'REFUND_REQUESTED', 'REFUNDED', 'CANCELED'
        )),
    constraint ck_appointment_payment_events_version
        check (expected_payment_version >= 0),
    constraint ck_appointment_payment_events_received_amount
        check (received_amount is null or received_amount >= 0),
    constraint ck_appointment_payment_events_fingerprint
        check (
            depositor_name_fingerprint = ''
            or depositor_name_fingerprint ~ '^[0-9a-f]{64}$'
        )
);

comment on table bodeul.appointment_payment_events is
    '입금자명 제출과 관리자 결제 상태 변경을 중복 없이 추적하는 추가 전용 감사 원장';
comment on column bodeul.appointment_payment_events.depositor_name_fingerprint is
    '평문 반복 저장과 이벤트 간 단순 상관분석을 줄이기 위해 operation_id를 salt로 포함한 SHA-256 지문';

create index ix_appointment_payment_events_appointment_created
    on bodeul.appointment_payment_events (appointment_request_id, created_at desc, id desc);
create unique index ux_appointment_payment_events_operation
    on bodeul.appointment_payment_events (operation_id)
    where operation_id is not null;

revoke all on table bodeul.appointment_bank_transfer_payments
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on table bodeul.appointment_payment_events
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

alter table bodeul.appointment_bank_transfer_payments enable row level security;
alter table bodeul.appointment_payment_events enable row level security;

revoke execute on function bodeul.account_deletion_postgres_inventory(uuid)
    from bodeul_core_runtime, bodeul_admin_runtime;
drop function bodeul.account_deletion_postgres_inventory(uuid);

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
    active_legal_hold_count bigint,
    bank_transfer_payment_count bigint,
    payment_event_count bigint
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
    related_bank_transfer_payments as (
        select payment.appointment_request_id
        from bodeul.appointment_bank_transfer_payments payment
        where payment.appointment_request_id in (
                select appointment.id from related_appointments appointment
            )
           or payment.confirmed_by_admin_user_id = p_user_id
    ),
    related_payment_events as (
        select event.id
        from bodeul.appointment_payment_events event
        where event.appointment_request_id in (
                select appointment.id from related_appointments appointment
            )
           or event.actor_user_id = p_user_id
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
        (select count(*) from active_legal_holds),
        (select count(*) from related_bank_transfer_payments),
        (select count(*) from related_payment_events);
$$;

comment on function bodeul.account_deletion_postgres_inventory(uuid)
    is '계정 삭제 실행 없이 본인 연관 PostgreSQL 데이터와 결제 원장·이벤트 건수를 조회하는 함수';

alter function bodeul.account_deletion_postgres_inventory(uuid) owner to bodeul_migration;
revoke all on function bodeul.account_deletion_postgres_inventory(uuid)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.account_deletion_postgres_inventory(uuid)
    to bodeul_core_runtime, bodeul_admin_runtime;

create function bodeul.initialize_bank_transfer_payment()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
begin
    if new.payment_method_code <> 'BANK_TRANSFER' then
        return new;
    end if;
    if new.payment_status_code <> 'AWAITING_DEPOSIT' then
        raise exception '무통장입금 예약은 입금 확인 대기 상태로 생성해야 합니다.'
            using errcode = '22023';
    end if;

    insert into bodeul.appointment_bank_transfer_payments (
        appointment_request_id,
        expected_amount
    ) values (
        new.id,
        new.final_price
    );

    insert into bodeul.appointment_payment_events (
        appointment_request_id,
        actor_role,
        event_type,
        previous_status_code,
        next_status_code,
        expected_payment_version
    ) values (
        new.id,
        'SYSTEM',
        'CREATED',
        'AWAITING_DEPOSIT',
        'AWAITING_DEPOSIT',
        0
    );
    return new;
end;
$$;

alter function bodeul.initialize_bank_transfer_payment() owner to bodeul_migration;
revoke all on function bodeul.initialize_bank_transfer_payment()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger appointment_bank_transfer_payment_initialize
after insert on bodeul.appointment_requests
for each row execute function bodeul.initialize_bank_transfer_payment();

create function bodeul.guard_bank_transfer_appointment_update()
returns trigger
language plpgsql
security invoker
set search_path = pg_catalog, pg_temp
as $$
begin
    if (old.payment_method_code = 'BANK_TRANSFER')
            is distinct from (new.payment_method_code = 'BANK_TRANSFER') then
        raise exception '무통장입금 예약의 결제수단은 생성 후 변경할 수 없습니다.'
            using errcode = '22023';
    end if;

    if old.payment_method_code = 'BANK_TRANSFER' then
        if new.final_price is distinct from old.final_price then
            raise exception '무통장입금 예약의 입금액은 생성 후 변경할 수 없습니다.'
                using errcode = '22023';
        end if;
        if current_user <> 'bodeul_migration'
                and (
                    new.payment_status_code is distinct from old.payment_status_code
                    or new.payment_approval_code is distinct from old.payment_approval_code
                    or new.payment_approved_at is distinct from old.payment_approved_at
                    or new.payment_provider_label is distinct from old.payment_provider_label
                ) then
            raise exception '무통장입금 상태는 허용된 서버 함수로만 변경할 수 있습니다.'
                using errcode = '42501';
        end if;
    end if;
    return new;
end;
$$;

alter function bodeul.guard_bank_transfer_appointment_update() owner to bodeul_migration;
revoke all on function bodeul.guard_bank_transfer_appointment_update()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger appointment_bank_transfer_update_guard
before update of payment_method_code, payment_status_code, payment_approval_code,
    payment_approved_at, payment_provider_label, final_price
on bodeul.appointment_requests
for each row execute function bodeul.guard_bank_transfer_appointment_update();

create function bodeul.get_bank_transfer_payment(
    p_appointment_request_id uuid,
    p_actor_user_id uuid
) returns table (
    appointment_request_id uuid,
    payment_method_code text,
    payment_status_code text,
    expected_amount integer,
    depositor_name text,
    payment_due_at timestamptz,
    received_amount integer,
    confirmed_at timestamptz,
    refund_requested_at timestamptz,
    refunded_at timestamptz,
    payment_version bigint
)
language plpgsql
stable
security definer
set search_path = bodeul, pg_temp
as $$
begin
    if not exists (
        select 1
        from bodeul.appointment_requests appointment
        join bodeul.app_users app_user on app_user.id = p_actor_user_id
        where appointment.id = p_appointment_request_id
          and appointment.patient_user_id = p_actor_user_id
          and app_user.role = 'PATIENT'
    ) then
        raise exception '무통장입금 정보를 조회할 권한이 없습니다.' using errcode = '42501';
    end if;

    return query
    select
        appointment.id,
        appointment.payment_method_code,
        appointment.payment_status_code,
        payment.expected_amount,
        payment.depositor_name,
        payment.payment_due_at,
        payment.received_amount,
        payment.confirmed_at,
        payment.refund_requested_at,
        payment.refunded_at,
        payment.payment_version
    from bodeul.appointment_requests appointment
    join bodeul.appointment_bank_transfer_payments payment
      on payment.appointment_request_id = appointment.id
    where appointment.id = p_appointment_request_id
      and appointment.payment_method_code = 'BANK_TRANSFER';

    if not found then
        raise exception '무통장입금 정보를 찾을 수 없습니다.' using errcode = 'P0002';
    end if;
end;
$$;

alter function bodeul.get_bank_transfer_payment(uuid, uuid) owner to bodeul_migration;
revoke all on function bodeul.get_bank_transfer_payment(uuid, uuid)
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
grant execute on function bodeul.get_bank_transfer_payment(uuid, uuid)
    to bodeul_core_runtime;

create function bodeul.set_bank_transfer_depositor(
    p_appointment_request_id uuid,
    p_actor_user_id uuid,
    p_operation_id uuid,
    p_expected_payment_version bigint,
    p_depositor_name text
) returns table (
    appointment_request_id uuid,
    payment_method_code text,
    payment_status_code text,
    expected_amount integer,
    depositor_name text,
    payment_due_at timestamptz,
    received_amount integer,
    confirmed_at timestamptz,
    refund_requested_at timestamptz,
    refunded_at timestamptz,
    payment_version bigint
)
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_normalized_name text := regexp_replace(btrim(coalesce(p_depositor_name, '')), '\s+', ' ', 'g');
    v_fingerprint text;
    v_status text;
    v_payment_version bigint;
    v_existing_event bodeul.appointment_payment_events%rowtype;
begin
    if p_operation_id is null then
        raise exception '입금자명 변경 작업 ID가 필요합니다.' using errcode = '22023';
    end if;
    if p_expected_payment_version is null or p_expected_payment_version < 0 then
        raise exception '결제 버전이 필요합니다.' using errcode = '22023';
    end if;
    if char_length(v_normalized_name) not between 1 and 100 then
        raise exception '입금자명은 1자부터 100자까지 입력해 주세요.' using errcode = '22023';
    end if;
    v_fingerprint := encode(sha256(convert_to(
        p_operation_id::text || ':' || v_normalized_name,
        'UTF8'
    )), 'hex');

    if not exists (
        select 1
        from bodeul.appointment_requests appointment
        join bodeul.app_users app_user on app_user.id = p_actor_user_id
        where appointment.id = p_appointment_request_id
          and appointment.patient_user_id = p_actor_user_id
          and app_user.role = 'PATIENT'
    ) then
        raise exception '입금자명을 변경할 권한이 없습니다.' using errcode = '42501';
    end if;

    select appointment.payment_status_code, payment.payment_version
    into v_status, v_payment_version
    from bodeul.appointment_requests appointment
    join bodeul.appointment_bank_transfer_payments payment
      on payment.appointment_request_id = appointment.id
    where appointment.id = p_appointment_request_id
      and appointment.payment_method_code = 'BANK_TRANSFER'
    for update of appointment, payment;

    if not found then
        raise exception '무통장입금 정보를 찾을 수 없습니다.' using errcode = 'P0002';
    end if;

    select event.* into v_existing_event
    from bodeul.appointment_payment_events event
    where event.operation_id = p_operation_id;

    if found then
        if v_existing_event.appointment_request_id is distinct from p_appointment_request_id
                or v_existing_event.actor_user_id is distinct from p_actor_user_id
                or v_existing_event.actor_role <> 'PATIENT'
                or v_existing_event.event_type <> 'DEPOSITOR_UPDATED'
                or v_existing_event.expected_payment_version is distinct from p_expected_payment_version
                or v_existing_event.depositor_name_fingerprint <> v_fingerprint then
            raise exception '같은 결제 작업 ID를 다른 내용으로 재사용할 수 없습니다.'
                using errcode = 'P0003';
        end if;
        return query select * from bodeul.get_bank_transfer_payment(
            p_appointment_request_id,
            p_actor_user_id
        );
        return;
    end if;

    if v_status not in ('AWAITING_DEPOSIT', 'REVIEW_REQUIRED') then
        raise exception '현재 결제 상태에서는 입금자명을 변경할 수 없습니다.' using errcode = 'P0001';
    end if;
    if v_payment_version <> p_expected_payment_version then
        raise exception '결제 정보가 다른 요청에서 변경되었습니다.' using errcode = '40001';
    end if;

    update bodeul.appointment_bank_transfer_payments payment
    set depositor_name = v_normalized_name,
        payment_version = payment.payment_version + 1,
        updated_at = now()
    where payment.appointment_request_id = p_appointment_request_id;

    insert into bodeul.appointment_payment_events (
        appointment_request_id,
        operation_id,
        actor_user_id,
        actor_role,
        event_type,
        previous_status_code,
        next_status_code,
        expected_payment_version,
        depositor_name_fingerprint
    ) values (
        p_appointment_request_id,
        p_operation_id,
        p_actor_user_id,
        'PATIENT',
        'DEPOSITOR_UPDATED',
        v_status,
        v_status,
        p_expected_payment_version,
        v_fingerprint
    );

    return query select * from bodeul.get_bank_transfer_payment(
        p_appointment_request_id,
        p_actor_user_id
    );
end;
$$;

alter function bodeul.set_bank_transfer_depositor(uuid, uuid, uuid, bigint, text)
    owner to bodeul_migration;
revoke all on function bodeul.set_bank_transfer_depositor(uuid, uuid, uuid, bigint, text)
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
grant execute on function bodeul.set_bank_transfer_depositor(uuid, uuid, uuid, bigint, text)
    to bodeul_core_runtime;

create function bodeul.transition_appointment_bank_transfer_payment(
    p_appointment_request_id uuid,
    p_actor_admin_user_id uuid,
    p_operation_id uuid,
    p_expected_payment_version bigint,
    p_target_status text,
    p_received_amount integer,
    p_reason text
) returns table (
    appointment_request_id uuid,
    payment_status_code text,
    expected_amount integer,
    depositor_name text,
    payment_due_at timestamptz,
    received_amount integer,
    confirmed_at timestamptz,
    refund_requested_at timestamptz,
    refunded_at timestamptz,
    payment_version bigint
)
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_admin_role text;
    v_appointment_status text;
    v_previous_status text;
    v_expected_amount integer;
    v_depositor_name text;
    v_payment_version bigint;
    v_existing_event bodeul.appointment_payment_events%rowtype;
begin
    if p_operation_id is null then
        raise exception '관리자 결제 작업 ID가 필요합니다.' using errcode = '22023';
    end if;
    if p_expected_payment_version is null or p_expected_payment_version < 0 then
        raise exception '결제 버전이 필요합니다.' using errcode = '22023';
    end if;
    if p_target_status is null or p_target_status not in (
        'DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED',
        'REFUND_REQUESTED', 'REFUNDED'
    ) then
        raise exception '지원하지 않는 무통장입금 상태입니다.' using errcode = '22023';
    end if;
    if char_length(btrim(coalesce(p_reason, ''))) not between 10 and 500 then
        raise exception '관리자 상태 변경 사유는 10자부터 500자까지 입력해 주세요.'
            using errcode = '22023';
    end if;

    select assignment.admin_role
    into v_admin_role
    from bodeul.admin_role_assignments assignment
    join bodeul.app_users app_user on app_user.id = assignment.admin_user_id
    where assignment.admin_user_id = p_actor_admin_user_id
      and assignment.revoked_at is null
      and assignment.admin_role in ('SUPER_ADMIN', 'OPERATIONS')
      and app_user.role = 'ADMIN'
    for share of assignment, app_user;

    if v_admin_role is null then
        raise exception '결제 처리에는 운영 관리자 권한이 필요합니다.' using errcode = '42501';
    end if;

    select
        appointment.status,
        appointment.payment_status_code,
        payment.expected_amount,
        payment.depositor_name,
        payment.payment_version
    into
        v_appointment_status,
        v_previous_status,
        v_expected_amount,
        v_depositor_name,
        v_payment_version
    from bodeul.appointment_requests appointment
    join bodeul.appointment_bank_transfer_payments payment
      on payment.appointment_request_id = appointment.id
    where appointment.id = p_appointment_request_id
      and appointment.payment_method_code = 'BANK_TRANSFER'
    for update of appointment, payment;

    if not found then
        raise exception '무통장입금 정보를 찾을 수 없습니다.' using errcode = 'P0002';
    end if;

    select event.* into v_existing_event
    from bodeul.appointment_payment_events event
    where event.operation_id = p_operation_id;

    if found then
        if v_existing_event.appointment_request_id is distinct from p_appointment_request_id
                or v_existing_event.actor_user_id is distinct from p_actor_admin_user_id
                or v_existing_event.actor_role <> 'ADMIN'
                or v_existing_event.event_type is distinct from p_target_status
                or v_existing_event.expected_payment_version is distinct from p_expected_payment_version
                or v_existing_event.received_amount is distinct from p_received_amount
                or v_existing_event.reason is distinct from btrim(p_reason) then
            raise exception '같은 결제 작업 ID를 다른 내용으로 재사용할 수 없습니다.'
                using errcode = 'P0003';
        end if;

        return query
        select
            appointment.id,
            appointment.payment_status_code,
            payment.expected_amount,
            payment.depositor_name,
            payment.payment_due_at,
            payment.received_amount,
            payment.confirmed_at,
            payment.refund_requested_at,
            payment.refunded_at,
            payment.payment_version
        from bodeul.appointment_requests appointment
        join bodeul.appointment_bank_transfer_payments payment
          on payment.appointment_request_id = appointment.id
        where appointment.id = p_appointment_request_id;
        return;
    end if;

    if v_payment_version <> p_expected_payment_version then
        raise exception '결제 정보가 다른 요청에서 변경되었습니다.' using errcode = '40001';
    end if;
    if not (
        (v_previous_status = 'AWAITING_DEPOSIT'
            and p_target_status in ('DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED'))
        or (v_previous_status = 'REVIEW_REQUIRED'
            and p_target_status in ('DEPOSIT_CONFIRMED', 'REFUND_REQUESTED'))
        or (v_previous_status = 'DEPOSIT_CONFIRMED'
            and p_target_status = 'REFUND_REQUESTED')
        or (v_previous_status = 'REFUND_REQUESTED'
            and p_target_status = 'REFUNDED')
        or (v_previous_status = 'CANCELED'
            and p_target_status = 'REVIEW_REQUIRED')
    ) then
        raise exception '현재 결제 상태에서는 요청한 순서로 변경할 수 없습니다.' using errcode = 'P0001';
    end if;

    if v_appointment_status = 'CANCELED'
            and v_previous_status = 'REVIEW_REQUIRED'
            and p_target_status <> 'REFUND_REQUESTED' then
        raise exception '취소된 예약의 검토 대상 입금은 환불 요청으로만 변경할 수 있습니다.'
            using errcode = 'P0001';
    end if;

    if p_target_status = 'DEPOSIT_CONFIRMED' then
        if btrim(v_depositor_name) = '' then
            raise exception '입금자명이 등록된 예약만 입금을 확인할 수 있습니다.' using errcode = 'P0001';
        end if;
        if p_received_amount is distinct from v_expected_amount then
            raise exception '입금액이 예약 금액과 일치하지 않습니다.' using errcode = '22023';
        end if;
    elsif p_target_status = 'REVIEW_REQUIRED' then
        if p_received_amount is null then
            raise exception '검토할 실제 입금액이 필요합니다.' using errcode = '22023';
        end if;
    elsif p_received_amount is not null then
        raise exception '이 상태 변경에는 입금액을 다시 전달하지 않습니다.' using errcode = '22023';
    end if;

    update bodeul.appointment_bank_transfer_payments payment
    set received_amount = case
            when p_target_status in ('DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED')
                then p_received_amount
            else payment.received_amount
        end,
        confirmed_by_admin_user_id = case
            when p_target_status = 'DEPOSIT_CONFIRMED' then p_actor_admin_user_id
            else payment.confirmed_by_admin_user_id
        end,
        confirmed_at = case
            when p_target_status = 'DEPOSIT_CONFIRMED' then now()
            else payment.confirmed_at
        end,
        refund_requested_at = case
            when p_target_status = 'REFUND_REQUESTED' then now()
            else payment.refund_requested_at
        end,
        refunded_at = case
            when p_target_status = 'REFUNDED' then now()
            else payment.refunded_at
        end,
        payment_version = payment.payment_version + 1,
        updated_at = now()
    where payment.appointment_request_id = p_appointment_request_id;

    update bodeul.appointment_requests appointment
    set payment_status_code = p_target_status,
        updated_at = now(),
        version = version + 1
    where appointment.id = p_appointment_request_id;

    insert into bodeul.appointment_payment_events (
        appointment_request_id,
        operation_id,
        actor_user_id,
        actor_role,
        event_type,
        previous_status_code,
        next_status_code,
        expected_payment_version,
        received_amount,
        reason
    ) values (
        p_appointment_request_id,
        p_operation_id,
        p_actor_admin_user_id,
        'ADMIN',
        p_target_status,
        v_previous_status,
        p_target_status,
        p_expected_payment_version,
        p_received_amount,
        btrim(p_reason)
    );

    perform bodeul.record_admin_access_audit(
        p_actor_admin_user_id,
        'UPDATE',
        'APPOINTMENT_PAYMENT',
        p_appointment_request_id::text,
        btrim(p_reason),
        'ALLOWED',
        jsonb_build_object(
            'previousStatus', v_previous_status,
            'nextStatus', p_target_status,
            'amountMatched', case
                when p_received_amount is null then null
                else p_received_amount = v_expected_amount
            end
        ),
        p_operation_id
    );

    return query
    select
        appointment.id,
        appointment.payment_status_code,
        payment.expected_amount,
        payment.depositor_name,
        payment.payment_due_at,
        payment.received_amount,
        payment.confirmed_at,
        payment.refund_requested_at,
        payment.refunded_at,
        payment.payment_version
    from bodeul.appointment_requests appointment
    join bodeul.appointment_bank_transfer_payments payment
      on payment.appointment_request_id = appointment.id
    where appointment.id = p_appointment_request_id;
end;
$$;

alter function bodeul.transition_appointment_bank_transfer_payment(
    uuid, uuid, uuid, bigint, text, integer, text
) owner to bodeul_migration;
revoke all on function bodeul.transition_appointment_bank_transfer_payment(
    uuid, uuid, uuid, bigint, text, integer, text
) from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.transition_appointment_bank_transfer_payment(
    uuid, uuid, uuid, bigint, text, integer, text
) to bodeul_admin_runtime;

create function bodeul.transition_canceled_bank_transfer_payment()
returns trigger
language plpgsql
security definer
set search_path = bodeul, pg_temp
as $$
declare
    v_previous_status text;
    v_next_status text;
    v_payment_version bigint;
begin
    if old.status = 'CANCELED'
            or new.status <> 'CANCELED'
            or new.payment_method_code <> 'BANK_TRANSFER'
            or new.payment_status_code in ('CANCELED', 'REFUND_REQUESTED', 'REFUNDED') then
        return new;
    end if;

    v_previous_status := new.payment_status_code;
    v_next_status := case
        when v_previous_status = 'AWAITING_DEPOSIT' then 'CANCELED'
        when v_previous_status in ('DEPOSIT_CONFIRMED', 'REVIEW_REQUIRED') then 'REFUND_REQUESTED'
        else null
    end;
    if v_next_status is null then
        return new;
    end if;

    select payment.payment_version
    into v_payment_version
    from bodeul.appointment_bank_transfer_payments payment
    where payment.appointment_request_id = new.id
    for update;

    if not found then
        raise exception '무통장입금 상세 원장이 없어 예약 취소를 중단합니다.'
            using errcode = 'P0002';
    end if;

    update bodeul.appointment_bank_transfer_payments payment
    set refund_requested_at = case
            when v_next_status = 'REFUND_REQUESTED' then now()
            else payment.refund_requested_at
        end,
        payment_version = payment.payment_version + 1,
        updated_at = now()
    where payment.appointment_request_id = new.id;

    update bodeul.appointment_requests appointment
    set payment_status_code = v_next_status,
        updated_at = now()
    where appointment.id = new.id;

    insert into bodeul.appointment_payment_events (
        appointment_request_id,
        actor_role,
        event_type,
        previous_status_code,
        next_status_code,
        expected_payment_version,
        reason
    ) values (
        new.id,
        'SYSTEM',
        v_next_status,
        v_previous_status,
        v_next_status,
        v_payment_version,
        '예약 취소에 따른 결제 상태 자동 전이'
    );
    return new;
end;
$$;

alter function bodeul.transition_canceled_bank_transfer_payment() owner to bodeul_migration;
revoke all on function bodeul.transition_canceled_bank_transfer_payment()
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;

create trigger appointment_bank_transfer_cancel_transition
after update of status on bodeul.appointment_requests
for each row execute function bodeul.transition_canceled_bank_transfer_payment();

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
    v_payment_method_code text;
    v_payment_status_code text;
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
        payment_method_code,
        payment_status_code,
        hospital_name,
        department_name
    into
        v_appointment_status,
        v_appointment_version,
        v_previous_manager_user_id,
        v_payment_method_code,
        v_payment_status_code,
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
    if v_payment_method_code = 'BANK_TRANSFER'
            and v_payment_status_code <> 'DEPOSIT_CONFIRMED' then
        raise exception '입금 확인이 끝난 무통장입금 예약만 매칭할 수 있습니다.' using errcode = 'P0001';
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
