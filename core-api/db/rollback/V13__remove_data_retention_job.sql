revoke execute on function bodeul.retention_monthly_summary(date)
    from bodeul_retention_runtime, bodeul_admin_runtime;
revoke execute on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    from bodeul_retention_runtime;
revoke execute on function bodeul.begin_retention_job(text, timestamptz, timestamptz)
    from bodeul_retention_runtime;
revoke execute on function bodeul.purge_expired_companion_records(timestamptz, integer)
    from bodeul_retention_runtime;
revoke execute on function bodeul.mark_companion_attachment_deleted(uuid, text, timestamptz)
    from bodeul_retention_runtime;
revoke execute on function bodeul.claim_expired_companion_attachments(timestamptz, integer)
    from bodeul_retention_runtime;
revoke execute on function bodeul.preview_expired_companion_data(timestamptz)
    from bodeul_retention_runtime;

drop function bodeul.retention_monthly_summary(date);
drop function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text);
drop function bodeul.begin_retention_job(text, timestamptz, timestamptz);
drop function bodeul.purge_expired_companion_records(timestamptz, integer);
drop function bodeul.mark_companion_attachment_deleted(uuid, text, timestamptz);
drop function bodeul.claim_expired_companion_attachments(timestamptz, integer);
drop function bodeul.preview_expired_companion_data(timestamptz);
drop table bodeul.retention_job_runs;

alter table bodeul.companion_chat_attachments
    drop constraint ck_chat_attachments_status;
alter table bodeul.companion_chat_attachments
    add constraint ck_chat_attachments_status
        check (status in ('ACTIVE', 'DELETED'));

drop index bodeul.ix_chat_attachments_expiry;
create index ix_chat_attachments_expiry
    on bodeul.companion_chat_attachments (expires_at)
    where expires_at is not null and status = 'ACTIVE';
