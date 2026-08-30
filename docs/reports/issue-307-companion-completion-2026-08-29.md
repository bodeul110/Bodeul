# 이슈 307 정상 동행 종료·완료 구현 기록

기준일: 2026-08-30

상태: 코드 반영과 최신 전체 로컬 검증 완료. V18 DB 적용, Core API·Android 배포, 실기기 검증과 완료 강제 설정 활성화는 수행하지 않음.

## 작업 목적

가이드 12에서 실제 동행이 끝난 시점과 가이드 13에서 기록 작성이 끝난 시점을 분리하고, 선택 첨부와 리포트 저장 실패가 정상 세션 완료를 되돌리지 않게 한다.

## 선택한 방식

- `CARE_ENDED`는 환자 인계를 마친 실제 동행 종료, `COMPLETED`는 선택 일지 제출을 포함한 업무 완료로 사용한다.
- `/care-end`는 현재 단계·배정 매니저·version을 확인한 뒤 `care_ended_at`을 서버 시각으로 최초 한 번만 기록한다. 중복 요청과 재진입은 같은 시각을 반환한다.
- `care_ended_at`이 기록되는 즉시 새 채팅·첨부·위치 쓰기와 배정 매니저의 기존 채팅·첨부·가이드 첨부·리포트·건강정보·위치 원문 조회를 닫는다. 매니저 세션 응답에는 본인 `managerJournal`, 리포트 생성 상태와 완료 메타데이터만 남긴다.
- 종료 후 환자는 보관기간 안의 기존 채팅·첨부를 읽는다. 보호자의 범위별 인가는 `CHAT`=채팅 본문·읽음 상태, `CHAT+ATTACHMENT`=채팅 첨부, `ATTACHMENT`=가이드 첨부, `REPORT`=최종 리포트·건강정보로 구분한다. 위치는 모든 역할에서 즉시 숨긴다.
- 채팅 본문 180일, 채팅 첨부 30일, 위치 24시간 보존 시각은 최종 리포트 완료가 아니라 최초 `care_ended_at`부터 계산한다. 보호자 정보공유 동의 만료도 같은 시각으로 확정한다.
- 종료 경계가 확정된 보호자 동의는 재부여로 범위나 만료일을 늘릴 수 없고 환자의 철회만 계속 허용한다. 서비스 선검사와 PostgreSQL 세션 행 잠금 가드를 함께 사용한다.
- 가이드 13 일지는 선택이며 최대 300자다. 세션과 예약을 먼저 `COMPLETED`로 확정하고 리포트 저장은 `PENDING`, `READY`, `FAILED`로 별도 추적한다.
- 가이드 8 결제 증빙은 JPEG·PNG·PDF 0~1개, 가이드 10 처방 이미지는 JPEG·PNG 0~3개다. 파일당 10 MiB 제한과 파일 시그니처를 서버에서 다시 검증한다.
- 원본은 기존 Firebase Storage 서버 경계에 저장하고, PostgreSQL에는 용도·경로·파일명·형식·크기·SHA-256을 저장한다. 요청 UUID와 payload fingerprint는 별도 operation ledger에 남겨 교체·삭제 뒤의 지연 재시도도 다시 적용하지 않으며, 같은 UUID의 다른 내용은 충돌로 거부한다.
- 보호자의 채팅 첨부에는 `CHAT + ATTACHMENT`, 가이드 첨부에는 `ATTACHMENT` 동의를 적용한다. 미동의 또는 철회 상태에서는 해당 원본 접근을 거부하고 목록을 비운다.
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
| `COMPLETED` + `READY` | `PUT /report` | 식별자·version만 든 성공 확인 응답 | 새 쓰기와 건강정보 재조회 없음 |

사고·긴급상황은 #297의 중단·인계·지원 계약으로 보내며 정상 `CARE_ENDED`나 `COMPLETED`로 합치지 않는다.

## 구현 범위

- Flyway `V18__separate_companion_care_completion.sql`과 개발용 rollback SQL
- V18의 세션 행 잠금과 DB trigger로 종료 처리와 동시에 들어오는 채팅·첨부·위치 쓰기를 직렬화하고, 구버전 상태가 남아도 `care_ended_at`으로 차단
- 종료 뒤 정보공유 동의 재부여 차단, 매니저 Realtime 신규·재인가 구독 회수, Android의 종료 세션 Realtime 보강 생략·기존 구독 종료와 DB 변경 신호 발행 중단
- 매니저 완료 이력의 리포트 원문 재조회 제거와 본인 `managerJournal` 기반 마스킹 표시
- migration 전 실시간 원문과 동의 만료값을 별도 비공개 ledger에 기록해 V18 rollback 때 원래 `expires_at`과 동의 경계를 복원
- 만료된 채팅 본문 조회 차단과 `CARE_ENDED` 이후 역할별 조회 마스킹
- Core API 종료·완료·리포트 재시도와 첨부 교체·삭제·인증 다운로드
- Android 종료 CTA, 선택 일지 300자 제한, 실패 세션 재진입, SAF 파일 선택·삭제
- Preview·Production 배포 워크플로의 완료 강제 설정 전달과 boolean 검증. 기본값은 `false`
- 서비스·HTTP·migration 계약·Android 진행 정책 테스트

## 검증

- 최신 변경을 포함한 `.\core-api\gradlew.bat -p core-api check --console=plain`: 성공
- 최신 변경을 포함한 `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`: 성공
- `npm --prefix tools/firebase run test:rules`: 7/7 성공. 기존 Core 업무 비교 문서는 환자·관리자 읽기만 유지하고 매니저·보호자 직접 읽기와 채팅 첨부 클라이언트 쓰기를 차단했다.
- V18 순서·기존 완료 행 backfill·용도별 첨부 개수·operation ledger·fail-closed rollback 계약과 `CARE_ENDED` 역할별 접근 테스트는 Core API 전체 검사에서 통과했다.
- 로컬 Docker daemon을 시작했으나 엔진이 준비되지 않아 PostgreSQL 스크립트의 로컬 실행은 생략했다. 새 커밋의 PR `migration-contract`에서 V1→V18, `+180일/+30일/+24시간`, 종료 후 직접 쓰기·동의 재부여·매니저 Realtime 거부, 늦은 `COMPLETED` 전환의 TTL 불변과 rollback 복원을 확인한다.
- GitHub Actions Core API·Preview·Production YAML `yq` 파싱: 성공
- 전체 변경 `git diff --check`와 migration 검증 셸 문법 검사: 성공

## 남은 위험과 적용 순서

1. 새 커밋의 disposable PostgreSQL CI에서 기존 완료 행 backfill, runtime role, `CARE_ENDED` 기준 TTL과 rollback을 확인한 뒤 V18을 개발 DB에 적용한다. 이어 postgres 권한으로 `006_companion_completion_realtime_authorization.sql`과 `015` 권한 시나리오를 실행한다.
2. 완료 강제 설정을 `false`로 유지한 Preview Core API와 새 Android를 배포한다.
3. 실기기에서 중복 종료 탭, 프로세스 재시작, PDF·이미지 선택, 3장 제한, 네트워크 실패와 리포트 재시도를 검증한다.
4. 교체·삭제 중 Storage 정리에 실패한 원본은 #222의 자동 파기·orphan 정리에 연결한다.
5. schema rollback이 필요하면 쓰기와 신규 Realtime 연결을 차단한 maintenance 상태에서 V18 rollback과 bootstrap 006 rollback을 연속 실행하고, 둘 다 성공하기 전에는 트래픽을 다시 열지 않는다.
6. 구버전 잔존과 rollback을 확인한 뒤에만 Preview에서 완료 강제 설정 활성화를 별도 승인한다. Production 활성화는 출시 게이트에서 다시 승인한다.

실제 PG, 녹음·STT·AI 요약, OCR, 새 알림과 사고 처리 상태는 이번 범위에 포함하지 않았다.
