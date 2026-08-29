-- 동의와 감사 이력은 운영 증적이므로 행이 남아 있으면 rollback을 중단한다.
-- docs/operations/postgres/guardian-sharing-consent-rollback.md의 export·검증을 먼저 완료해야 한다.
do $$
begin
    if exists (select 1 from bodeul.guardian_sharing_consent_events)
            or exists (select 1 from bodeul.guardian_sharing_consents) then
        raise exception using
            errcode = '55000',
            message = 'guardian sharing consent data must be exported and removed before schema rollback';
    end if;
end;
$$;

drop table bodeul.guardian_sharing_consent_events;
drop table bodeul.guardian_sharing_consents;
drop table bodeul.guardian_sharing_consent_settings;
