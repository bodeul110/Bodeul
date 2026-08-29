# 이슈 307 정상 동행 종료·완료 구현 기록

기준일: 2026-08-29

상태: 코드와 로컬 테스트 완료. V18 DB 적용, Core API·Android 배포, 실기기 검증과 완료 강제 설정 활성화는 수행하지 않음.

## 작업 목적

가이드 12에서 실제 동행이 끝난 시점과 가이드 13에서 기록 작성이 끝난 시점을 분리하고, 선택 첨부와 리포트 저장 실패가 정상 세션 완료를 되돌리지 않게 한다.

## 선택한 방식

- `CARE_ENDED`는 환자 인계를 마친 실제 동행 종료, `COMPLETED`는 선택 일지 제출을 포함한 업무 완료로 사용한다.
- `/care-end`는 현재 단계·배정 매니저·version을 확인한 뒤 `care_ended_at`을 서버 시각으로 최초 한 번만 기록한다. 중복 요청과 재진입은 같은 시각을 반환한다.
- 가이드 13 일지는 선택이며 최대 300자다. 세션과 예약을 먼저 `COMPLETED`로 확정하고 리포트 저장은 `PENDING`, `READY`, `FAILED`로 별도 추적한다.
- 가이드 8 결제 증빙은 JPEG·PNG·PDF 0~1개, 가이드 10 처방 이미지는 JPEG·PNG 0~3개다. 파일당 10 MiB 제한과 파일 시그니처를 서버에서 다시 검증한다.
- 원본은 기존 Firebase Storage 서버 경계에 저장하고, PostgreSQL에는 용도·경로·파일명·형식·크기·SHA-256을 저장한다. 요청 UUID와 payload fingerprint는 별도 operation ledger에 남겨 교체·삭제 뒤의 지연 재시도도 다시 적용하지 않으며, 같은 UUID의 다른 내용은 충돌로 거부한다.
- 보호자 다운로드와 세션 첨부 목록에는 현재 `ATTACHMENT` 동의를 적용한다. 미동의 또는 철회 상태에서는 원본 접근을 거부하고 목록을 비운다.
- DB 반영 전 실패한 요청이 만든 Storage 객체는 동시 요청의 승자 객체를 지우지 않도록 즉시 삭제하지 않고 orphan으로 보존한다. #222 정리 작업이 이후 커밋된 참조 집합과 대조해 회수한다.
- `BODEUL_SESSION_COMPLETION_ENFORCEMENT` 기본값을 `false`로 두어 구버전 앱의 마지막 단계 직접 완료를 한시적으로 허용한다.

## 검토한 대안

- 동행 종료와 리포트 저장을 한 트랜잭션으로 유지하면 리포트 장애가 현장 업무 완료까지 되돌리므로 제외했다.
- 첨부를 Android에서 Storage에 직접 쓰면 PostgreSQL 참여 관계와 현재 단계 인가를 우회하므로 제외했다.
- 모든 첨부를 하나의 공통 제한으로 처리하면 결제 PDF와 처방 이미지의 업무 규칙이 섞이므로 용도별 정책으로 분리했다.
- 구버전 앱을 즉시 차단하면 배포 순서에 따라 마지막 단계가 막힐 수 있어 기본 비활성의 점진적 강제 설정을 선택했다.

## 선택 이유

현재 MVP에서는 외부 결제·OCR·AI 생성을 붙이는 것보다 현장 종료 시각과 사람이 작성한 선택 기록을 안정적으로 남기는 것이 우선이다. 종료·완료·리포트 상태를 분리하면 네트워크나 DB 쓰기 오류가 발생해도 이미 끝난 동행을 다시 진행 중으로 보이지 않게 할 수 있다. Storage 원본과 PostgreSQL 인가 메타데이터를 나누는 방식은 기존 채팅 첨부 운영 경계를 재사용하면서 Android 직접 권한을 늘리지 않는다.

## 상태 전이

| 시작 상태 | 요청 | 결과 | 재요청 |
| --- | --- | --- | --- |
| 가이드 12 `CARE_COMPLETION` | `POST /care-end` | `CARE_ENDED`, 최초 `care_ended_at` 저장, 위치 공유 종료 | 같은 종료 시각과 현재 상태 반환 |
| `CARE_ENDED` | `PUT /report` | 세션·예약 `COMPLETED`, 리포트 `PENDING` 후 저장 시도 | 완료 전 version 충돌 검출 |
| `COMPLETED` + `FAILED`·`PENDING` | `PUT /report` | 세션 완료를 유지하고 리포트만 재시도 | 최신 세션으로 반복 가능 |
| `COMPLETED` + `READY` | `PUT /report` | 기존 리포트 반환 | 새 쓰기 없음 |

사고·긴급상황은 #297의 중단·인계·지원 계약으로 보내며 정상 `CARE_ENDED`나 `COMPLETED`로 합치지 않는다.

## 구현 범위

- Flyway `V18__separate_companion_care_completion.sql`과 개발용 rollback SQL
- Core API 종료·완료·리포트 재시도와 첨부 교체·삭제·인증 다운로드
- Android 종료 CTA, 선택 일지 300자 제한, 실패 세션 재진입, SAF 파일 선택·삭제
- Preview·Production 배포 워크플로의 완료 강제 설정 전달과 boolean 검증. 기본값은 `false`
- 서비스·HTTP·migration 계약·Android 진행 정책 테스트

## 검증

- `./core-api/gradlew.bat -p core-api check --console=plain`: 성공
- `./gradlew.bat testDebugUnitTest --console=plain`: 성공
- `./gradlew.bat assembleDebug --console=plain`: 성공
- V18 순서·기존 완료 행 backfill·용도별 첨부 개수·operation ledger·fail-closed rollback: 단위 계약 통과, disposable PostgreSQL CI에서 실제 실행하도록 연결
- 로컬 Docker daemon이 실행 중이 아니어서 PostgreSQL 스크립트의 로컬 실행은 생략했으며 PR의 `migration-contract` 결과를 적용 근거로 남긴다.
- GitHub Actions Preview·Production YAML: `yq` 파싱 확인
- `git diff --check`: 통과

## 남은 위험과 적용 순서

1. V18을 개발 DB에 먼저 적용하고 기존 완료 행 backfill, runtime role, rollback을 실제 PostgreSQL에서 검증한다.
2. 완료 강제 설정을 `false`로 유지한 Preview Core API와 새 Android를 배포한다.
3. 실기기에서 중복 종료 탭, 프로세스 재시작, PDF·이미지 선택, 3장 제한, 네트워크 실패와 리포트 재시도를 검증한다.
4. 교체·삭제 중 Storage 정리에 실패한 원본은 #222의 자동 파기·orphan 정리에 연결한다.
5. 구버전 잔존과 rollback을 확인한 뒤에만 Preview에서 완료 강제 설정 활성화를 별도 승인한다. Production 활성화는 출시 게이트에서 다시 승인한다.

실제 PG, 녹음·STT·AI 요약, OCR, 새 알림과 사고 처리 상태는 이번 범위에 포함하지 않았다.
