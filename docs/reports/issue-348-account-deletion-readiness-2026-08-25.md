# Issue 348 계정 삭제 영향도 1단계

기준일: 2026-08-25

관련 이슈: [#348 탈퇴·삭제와 법정 보존 분리 구현](https://github.com/bodeul110/Bodeul/issues/348)

## 구현한 내용

- 인증된 본인의 PostgreSQL 연관 데이터 건수와 객관적 관찰 코드만 반환하는 읽기 전용 API를 추가했다.
- 요청에서 사용자 ID를 받지 않고 Spring Security principal의 내부 UUID만 사용한다.
- PostgreSQL V15 집계 함수에 프로필, 예약, 세션, 리포트, 후속 처리, 배정 감사, 채팅, 첨부 메타데이터, 읽음, 위치와 legal hold를 포함했다.
- legal hold 존재 여부는 공개 정책이 정해지기 전까지 본인 API 응답에서 제외했다.
- Core runtime에는 배정 감사 원문 권한을 추가하지 않고 집계 함수 실행 권한만 부여했다.
- Firestore, Storage, Firebase Auth와 백업은 구현된 것처럼 추정하지 않고 `NOT_EVALUATED`로 반환한다.

## 변경된 범위

| 범위 | 변경 |
| --- | --- |
| Core API | `GET /api/account/deletion-readiness`, 서비스와 JDBC 저장소 추가 |
| PostgreSQL | 집계 전용 `SECURITY DEFINER` 함수와 V15 rollback 추가 |
| 인증 | 기존 Firebase token·PostgreSQL principal 경계를 그대로 사용 |
| 데이터 변경 | 없음. INSERT, UPDATE, DELETE와 Firebase Auth 삭제를 실행하지 않음 |

## 검증

- 계정 readiness, DB 계약과 Firebase 인증 통합 집중 테스트 32건 통과
- `core-api` 전체 테스트 217건과 `check` 통과
- 인증 누락 401, 캐시 금지, 고정 미판정 응답과 식별자 미노출 확인
- 진행 중 예약·세션은 탈퇴 차단으로 단정하지 않고 관찰 코드로만 반환하며 legal hold는 응답에 노출하지 않는지 확인
- DB 집계 열이 `NULL`이거나 음수이면 0건으로 해석하지 않고 출처 오류로 닫히는지 확인
- repository 미구성·DB 오류 시 `SOURCE_UNAVAILABLE`과 `INVENTORY_INCOMPLETE` 확인
- migration 문자열 계약에서 고정 `search_path`, 최소 실행 권한, 원문·민감 컬럼 미조회와 제한된 rollback 확인

개발 `Core API DB Migration` run `32861819498`에서 PostgreSQL 17.6의 기존 V14를 V15로 올렸다. Flyway는 migration 15개를 검증하고 V15 한 건을 적용해 schema version 15로 종료했다.

후속 run `32864048561`에서는 V15가 이미 최신이라 migration 없이 종료된 뒤 `verifyAccountDeletionInventory`가 통과했다. 이 읽기 전용 검증은 함수 소유자·`SECURITY DEFINER`·고정 `search_path`, 실제 Core/Admin 서비스 역할의 schema `USAGE`와 함수 `EXECUTE`, 공개 역할의 실행 차단, Core runtime/service의 배정 감사 원문 조회 차단을 확인했다. 합성 UUID 조회는 정의된 14개 열을 순서대로 반환했고 모든 집계값이 0이었다.

로컬 Docker 엔진은 기동되지 않았지만 `Core API CI`의 disposable PostgreSQL 17에서 V1~V15 연속 적용이 통과했다. 공유 개발 DB에는 rollback을 실행하지 않았고 CI도 V15 rollback 자체는 실행하지 않으므로, V15 rollback SQL은 제한된 문자열 계약까지만 확인했다.

## 남은 범위

- 실제 탈퇴 승인과 삭제 endpoint
- Firestore, Storage, Firebase Auth와 백업 점검·삭제 구현
- 최근 재인증, token 폐기와 진행 중 예약·분쟁 처리 절차
- 법정 보존 분리와 FK tombstone·비식별화 정책 확정
- 저장소 부분 실패 재시도, 삭제 ledger와 백업 복원 재적용
- 합성 fixture 기반 PostgreSQL 집계와 전체 삭제 시나리오 실검증

현재 결론은 `PostgreSQL 영향도 읽기 1단계 완료, 실제 계정 삭제 미구현`이다.
