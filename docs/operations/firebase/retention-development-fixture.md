# 개발 Firebase 자동 파기 픽스처

## 목적

`bodeul-dev`의 실제 Firestore와 Storage에서 전환 문서와 매니저 증빙 파기를 한 번 검증한다. 일반 개발 데이터와 PostgreSQL은 처리하지 않고, 고정된 합성 픽스처만 대상으로 한다.

## 실행 경계

- 프로젝트는 `bodeul-dev`, 버킷은 `bodeul-dev.firebasestorage.app`만 허용한다.
- production 프로젝트와 Emulator 환경변수가 설정된 실행은 거부한다.
- 문서 ID 4개와 Storage 객체 경로 4개를 코드 allowlist로 고정한다.
- Firestore 문서와 Storage custom metadata의 픽스처 이름, 저장소 소유자, 이슈 번호가 모두 일치하는 항목만 정리한다.
- `setup`, `apply`, `cleanup`은 `--confirm-project bodeul-dev`가 없으면 실행하지 않는다.
- 기존 문서나 객체가 하나라도 있으면 덮어쓰지 않고 `cleanup` 후 재실행하도록 중단한다.
- PostgreSQL adapter는 후보 0건만 반환한다. 이 작업은 Firestore 전환 데이터와 매니저 증빙만 검증한다.
- 정기 예약 함수의 `RETENTION_APPLY_ENABLED` 값은 변경하지 않는다.

기존 `retention:apply`는 PostgreSQL과 Firebase의 전체 만료 후보를 처리한다. 개발 픽스처 검증에는 사용하지 않는다.

## 인증

장기 서비스 계정 JSON key는 만들지 않는다. Firebase Admin SDK가 지원하는 로컬 Application Default Credentials를 사용한다. ADC 파일은 사용자 프로필에만 두고 저장소에 복사하지 않는다.

```powershell
gcloud auth application-default login
gcloud auth application-default set-quota-project bodeul-dev
```

Android Preflight WIF 계정은 읽기 전용이다. 일회 리허설을 위해 해당 계정의 권한을 확대하거나 쓰기 권한을 가진 새 workflow를 만들지 않는다. 이번 리허설의 인증 경로는 ADC 하나로 고정하고 Firebase CLI 사용자 token adapter는 사용하지 않는다.

## 실행 순서

저장소 루트에서 다음 순서로 실행한다.

```powershell
npm --prefix functions run retention:firebase-fixture -- status --project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- setup --project bodeul-dev --confirm-project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- dry-run --project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- apply --project bodeul-dev --confirm-project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- status --project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- cleanup --project bodeul-dev --confirm-project bodeul-dev
npm --prefix functions run retention:firebase-fixture -- status --project bodeul-dev
```

예상 상태는 `ABSENT -> READY -> READY -> APPLIED -> APPLIED -> ABSENT -> ABSENT`다. `setup`, `dry-run`, `apply`, `cleanup`은 예상 상태와 다르면 비정상 종료하고, 각 `status` 출력은 다음 단계 전에 직접 대조한다.

## 기대 결과

| 대상 | dry-run | apply 후 |
| --- | --- | --- |
| 만료 동행 세션 | 본문 1, 첨부 1, 위치 1 | 본문 비식별화, 첨부 참조와 정밀 위치 제거, 원본 삭제 |
| legal hold 동행 세션 | 제외 3 | 본문, 첨부, 위치와 원본 유지 |
| 만료 매니저 증빙 | 후보 1 | Firestore 경로 참조와 Storage 원본 제거 |
| legal hold 매니저 증빙 | 제외 1 | Firestore 참조와 Storage 원본 유지 |
| 삭제 실패 | 0 | 한 건이라도 있으면 명령 실패 |

실패·재시도 주입은 실제 개발 버킷의 권한을 변경하지 않고 기존 Firestore·Storage Emulator 통합 테스트에서 검증한다.

## 중단 및 복구

- 상태가 `PARTIAL`이면 `apply`를 다시 실행하지 않는다.
- `status` 출력으로 합성 문서와 객체의 존재 여부 및 표식을 확인한다.
- 표식이 모두 일치할 때만 `cleanup`을 실행한다.
- 표식이 다른 고정 경로가 발견되면 자동 삭제하지 말고 원인을 확인한다.
- `cleanup` 뒤 최종 `status`가 `ABSENT`인지 확인한다.

검증 근거는 [#222 개인정보 자동 파기 구현 기록](../../reports/issue-222-data-retention-2026-07-19.md)에 남긴다.
