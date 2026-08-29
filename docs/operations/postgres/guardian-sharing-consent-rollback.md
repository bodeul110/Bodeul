# 보호자 정보공유 동의 V17 rollback

## 목적

V17의 동의 현재 상태와 감사 이력을 보존하지 않은 채 스키마를 제거하지 않도록 rollback 전 export와 검증 절차를 고정한다.

## 실행 조건

- production에서는 승인된 DB 백업과 복원 가능 증적을 먼저 확보한다.
- Core API와 보호자 앱의 쓰기를 중단한 유지보수 구간에서만 수행한다.
- `V17__remove_guardian_sharing_consents.sql`은 동의 또는 감사 행이 한 건이라도 있으면 실패한다.

## export

접속 비밀번호와 결과 파일은 저장소 밖의 승인된 암호화 경로에서 관리한다.

```bash
pg_dump "$DATABASE_URL" \
  --data-only \
  --table=bodeul.guardian_sharing_consent_settings \
  --table=bodeul.guardian_sharing_consents \
  --table=bodeul.guardian_sharing_consent_events \
  --file=guardian-sharing-consent-v17-data.sql
```

export 전후에 아래 수를 기록하고 덤프 파일의 SHA-256을 함께 보관한다.

```sql
select count(*) from bodeul.guardian_sharing_consents;
select count(*) from bodeul.guardian_sharing_consent_events;
```

별도 복구용 DB에 dump를 복원해 두 행 수와 예약·환자·보호자 외래키를 확인한다. 원본 행 삭제는 이 복원 검증과 별도 승인 뒤에만 수행한다.

## 스키마 rollback

1. `db/bootstrap/rollback/005_guardian_sharing_realtime_authorization_rollback.sql`을 적용한다.
2. 검증된 export와 원본 삭제 승인을 확인한다.
3. `db/rollback/V17__remove_guardian_sharing_consents.sql`을 실행한다.
4. `db/verification/014_guardian_sharing_consent_rollback_checks.sql`로 객체 제거와 보호자 Broadcast 차단 유지를 확인한다.

행이 남은 상태에서 rollback이 실패하는 것은 정상적인 데이터 보호 동작이다. 실패를 우회하기 위해 파일에서 guard를 제거하면 안 된다.
