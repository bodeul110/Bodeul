-- PostgreSQL 원생 예약이 생성된 뒤에는 이 rollback을 실행하지 않는다.
do $$
begin
    if exists (
        select 1
        from bodeul.appointment_requests
        where firestore_id is null
           or client_request_id is not null
    ) then
        raise exception 'PostgreSQL 원생 예약이 있어 V4 read model rollback을 중단합니다.';
    end if;
end
$$;

drop policy if exists appointment_requests_core_update on bodeul.appointment_requests;
drop policy if exists appointment_requests_core_insert on bodeul.appointment_requests;

revoke insert, update, delete on table bodeul.appointment_requests from bodeul_core_runtime;

drop index if exists bodeul.uq_appointment_requests_requester_client_request;

alter table bodeul.appointment_requests
    drop constraint if exists ck_appointment_requests_version,
    drop constraint if exists ck_appointment_requests_firestore_id,
    drop column if exists version,
    drop column if exists client_request_id,
    alter column updated_at drop not null,
    alter column updated_at drop default,
    alter column created_at drop default,
    alter column imported_at set default now(),
    alter column imported_at set not null,
    alter column firestore_id set not null,
    add constraint ck_appointment_requests_firestore_id
        check (btrim(firestore_id) <> '');

comment on table bodeul.appointment_requests is 'Firestore 예약 요청을 PostgreSQL로 검증하기 위한 서버 전용 read model';
comment on column bodeul.appointment_requests.firestore_id is '원본 Firestore appointmentRequests 문서 ID';
comment on column bodeul.appointment_requests.imported_at is '백업 기반 row가 PostgreSQL에 마지막으로 적재된 시각';

drop index if exists bodeul.ix_app_users_role_phone;
drop index if exists bodeul.ix_app_users_role_email;

alter table bodeul.app_users
    drop column if exists phone,
    drop column if exists email,
    drop column if exists name;
