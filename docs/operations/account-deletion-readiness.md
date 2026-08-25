# 계정 탈퇴·삭제 준비 상태

기준일: 2026-08-25

## 작업 목적

실제 계정 삭제를 구현하기 전에 인증된 본인과 연결된 저장소별 데이터 범위와 기술적 차단 사실을 원문 개인정보 없이 확인한다. 현재 API는 영향도 조사만 수행하며 탈퇴 승인, 삭제 순서 결정과 데이터 변경을 수행하지 않는다.

## 현재 API 계약

| 항목 | 값 |
| --- | --- |
| 경로 | `GET /api/account/deletion-readiness` |
| 사용자 식별 | 검증된 Firebase token으로 조회한 PostgreSQL principal UUID |
| 요청 사용자 ID | 받지 않음 |
| 캐시 | `Cache-Control: no-store` |
| 삭제 실행 | 항상 `deletionExecuted=false` |
| 삭제 판단 | 항상 `decision=NOT_EVALUATED` |
| 전체 점검 완료 | 항상 `complete=false` |

응답에는 출처별 상태, 집계 건수, 관찰 코드와 점검 차단 코드만 포함한다. 사용자 UUID, Firebase UID, 개별 레코드 ID, 이름, 연락처, 건강정보, 채팅 본문, 좌표, 파일명과 Storage 경로는 반환하지 않는다.

## 출처별 상태

| 출처 | 현재 상태 | 확인 범위 |
| --- | --- | --- |
| PostgreSQL | 집계 성공 시 `COMPLETE`, 연결·함수 오류 시 `ERROR` | 프로필, 예약, 세션, 리포트, 후속 처리, 배정 감사, 채팅, 첨부 메타데이터, 읽음과 위치 건수 |
| Firestore | `NOT_EVALUATED` | 프로필, 지원, 심사와 전환 잔존 문서는 아직 미점검 |
| Firebase Storage | `NOT_EVALUATED` | 첨부·증빙 원본은 아직 미점검 |
| Firebase Auth | `NOT_EVALUATED` | 최근 재인증, token 폐기와 Auth 사용자 삭제는 아직 미점검 |
| 백업 | `NOT_EVALUATED` | 삭제 재적용 목록과 복원 후 재활성화 방지는 아직 미점검 |

PostgreSQL 출처가 `COMPLETE`여도 전체 탈퇴 준비가 완료된 것은 아니다. `INVENTORY_INCOMPLETE`는 나머지 네 출처가 구현되기 전까지 항상 포함한다.

## PostgreSQL 권한 경계

Flyway V15의 `bodeul.account_deletion_postgres_inventory(uuid)` 함수가 집계를 담당한다. 함수는 `bodeul_migration`이 소유하고 `search_path`를 고정한 `SECURITY DEFINER`로 실행한다. Core API와 관리자 서버 runtime에는 함수 실행 권한만 부여하며 배정 감사 테이블의 원문 조회 권한을 Core runtime에 추가하지 않는다.

집계는 현재 사용자와 직접 연결된 FK뿐 아니라 사용자의 예약·세션을 통해 연결된 행을 포함한다. 과거 배정 매니저처럼 현재 세션 관계에서는 빠질 수 있는 경우를 위해 채팅 발신자, 위치 기록자와 후속 처리 작성자 직접 참조도 별도로 포함한다. 유효한 legal hold는 DB 내부 영향도에는 포함하지만 내부 조사·분쟁 보존 사실의 공개 정책이 정해지기 전까지 본인 API의 건수와 관찰 코드에는 노출하지 않는다.

## 관찰 코드

| 코드 | 의미 |
| --- | --- |
| `ACTIVE_APPOINTMENT_PRESENT` | 완료·취소되지 않은 연관 예약이 있음 |
| `ACTIVE_SESSION_PRESENT` | 완료·취소되지 않은 연관 동행 세션이 있음 |
| `POSTGRES_PROFILE_MISSING` | 인증 principal에 대응하는 PostgreSQL 프로필 집계가 없음 |

관찰 코드는 현재 데이터의 사실만 나타내며 탈퇴 가능·불가를 결정하지 않는다. 특히 진행 중 예약·세션을 어떤 절차로 처리할지는 정책·운영 승인 대상이다.

## 점검 차단 코드

| 코드 | 의미 |
| --- | --- |
| `SOURCE_UNAVAILABLE` | PostgreSQL 집계 출처를 조회할 수 없음 |
| `INVENTORY_INCOMPLETE` | 모든 저장소와 백업의 영향도 점검이 아직 끝나지 않음 |

점검 차단 코드는 영향도 조사가 완전하지 않음을 나타낼 뿐 삭제 가능 여부를 판정하지 않는다.

## 선택한 방식과 대안

현재 MVP 규모에서는 실제 삭제 orchestration보다 원문을 노출하지 않는 영향도 API를 먼저 두는 편이 부분 실패와 과삭제 위험을 줄인다. Core runtime에 여러 테이블의 추가 권한을 부여하는 방식은 배정 감사 원문까지 노출하므로 제외하고, 집계 전용 함수 실행 권한만 부여했다.

클라이언트가 사용자 ID를 보내게 하는 방식은 다른 계정의 존재와 건수를 조회할 위험이 있어 제외했다. 실제 삭제 API와 `canDelete` 같은 승인값도 정책이 확정되지 않은 상태에서 클라이언트가 삭제 가능으로 오해할 수 있어 이번 범위에 넣지 않았다.

## 검증 기준

- 인증이 없으면 401이며 다른 사용자 ID를 지정할 입력이 없다.
- 응답은 읽기 전용·미판정·미완료 상태를 유지하고 민감 식별자를 포함하지 않는다.
- PostgreSQL 연결 또는 함수 오류는 `SOURCE_UNAVAILABLE`로 닫힌다.
- migration은 집계 함수 외 DML 권한을 추가하지 않고 rollback은 해당 함수만 제거한다.
- Core API 전체 테스트를 통과해야 한다.

## 남은 범위

- Firestore, Storage, Firebase Auth와 백업 영향도 점검기
- 최근 재인증과 token 폐기 확인
- 법정 보존자료 분리, tombstone·비식별화와 FK 처리 방식
- legal hold 존재 여부를 사용자에게 공개할지에 대한 정책·법률 확인
- 저장소별 삭제 순서, 부분 실패 재시도와 감사 이벤트
- 복원된 백업에 삭제를 재적용하는 삭제 ledger
- 가상 fixture 기반 정상 탈퇴, 진행 중 예약, legal hold, 부분 실패와 재가입 검증

실제 삭제 기능의 완료 조건과 정책 추적은 [#348](https://github.com/bodeul110/Bodeul/issues/348), 보관·파기 기본값은 [데이터 보관 및 파기 정책](data-retention-policy.md)을 따른다.
