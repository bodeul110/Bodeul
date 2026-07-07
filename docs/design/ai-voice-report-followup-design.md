# AI 음성 리포트 자동 생성 후속 설계

기준일: 2026-07-07

## 배경

AI 음성 녹음 기반 진료 리포트 자동 생성은 기능설명서와 gap 체크리스트에 남아 있지만 현재 앱 기능으로 구현되어 있지 않다. 현재 MVP에서는 매니저가 동행 종료 후 직접 `sessionReports`를 작성하고, 보호자 화면은 확정된 리포트만 읽는 구조다.

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

구현 전에 녹음 권한, 음성/전사 데이터 저장 위치, 전사와 요약 처리 주체, 민감 의료정보 처리 기준, 매니저 리포트와 보호자 최종 리포트 연결 방식을 정한다.

## 현재 구현 기준

- Android 앱의 최종 리포트 모델은 `SessionReport`이고 핵심 필드는 `summary`, `treatmentNotes`, `medicationNotes`, `medicationName`, `medicationChangeSummary`, `medicationScheduleNote`, `nextVisitAt`이다.
- Firestore 기준 저장 위치는 `sessionReports`이며, PostgreSQL 전환 초안에도 `session_reports` 테이블이 있다.
- 매니저 앱은 `submitSessionReport` 흐름에서 수동 입력값을 저장하고, 저장 완료 시 동행 세션과 예약 상태를 완료로 전환한다.
- `docs/planning/mvp-scope.md`는 AI 음성 리포트를 이번 MVP 제외 항목으로 둔다.
- `docs/design/feature-spec-gap-checklist-2026-05-22.md`는 AI 음성 리포트를 `후속 설계` 항목으로 둔다.

## 선택한 방식

현재 MVP 규모에서는 AI가 최종 리포트를 직접 작성하지 않고, 매니저 수동 리포트 작성 흐름 위에 `검토용 초안 생성` 기능으로 붙인다.

- 최종 보호자 리포트의 source of truth는 매니저가 확인하고 저장한 `sessionReports`다.
- 음성 녹음, 전사문, AI 요약은 확정 전 임시 초안 데이터로만 취급한다.
- 앱이 STT/AI provider key를 직접 들고 외부 API를 호출하지 않는다.
- 처리 주체는 PostgreSQL 전환 후 도메인 API를 맡을 `api/` 백엔드를 우선 기준으로 둔다.
- 원본 음성은 기본적으로 영구 저장하지 않고, provider 처리에 업로드가 필요할 때만 임시 저장 후 삭제한다.
- 외부 provider, secret, 저장소, 배포 설정이 필요 없는 설계/UX/mock 준비는 OCR 이전에도 진행할 수 있다.
- 실제 STT/AI provider 연동과 운영 저장 흐름은 OCR 처방전/복약 비교의 파일 처리, 추출 신뢰도, 검토 UX 기준을 먼저 확인한 뒤 진행한다.

## 대안

| 대안 | 장점 | 단점 | 판단 |
|---|---|---|---|
| 앱에서 직접 STT/AI API 호출 | 구현이 가장 단순하고 서버 작업이 적다. | provider key가 앱에 노출될 수 있고, 권한 검증과 호출량 제어가 어렵다. | 보류 |
| Cloud Functions 프록시 | Firebase Auth, Storage, Firestore Rules와 연결하기 쉽다. | PostgreSQL 전환 후 같은 도메인 로직을 `api/`로 다시 옮겨야 하고, AI/OCR 처리 경계가 Firebase에 남는다. | 보조 역할만 검토 |
| `api/` 백엔드 프록시 | PostgreSQL 전환, provider 교체, 감사 로그, rate limit, 데이터 계약을 한 곳에서 다루기 쉽다. | API 배포/운영/인증 연결을 먼저 안정화해야 한다. | 채택 |
| 기능 미제공 | 민감정보와 환각 리스크가 없다. | 매니저 기록 부담을 줄이지 못한다. | MVP 기본값 |

## 선택 이유

현재 BoDeul은 의료 동행 중 나온 내용을 보호자에게 전달하는 서비스라, 음성 원본과 전사문에는 민감 의료정보가 포함될 가능성이 높다. 따라서 초기 구현 편의보다 최소 수집, 서버 측 권한 검증, 매니저 확인 절차가 더 중요하다.

현재 MVP 규모에서는 별도 AI 파이프라인을 먼저 키우기보다 기존 수동 리포트 흐름을 유지하고, 데이터와 운영 기준이 준비된 뒤 초안 생성만 보조 기능으로 붙이는 쪽이 맞다. PostgreSQL 전환이 확정된 기준에서는 AI 음성 처리도 처음부터 `api/` 백엔드에 붙여 도메인 로직과 감사 로그가 같은 경계에 남도록 한다.

OCR 처방전/복약 비교는 이미지 파일, 약품명 추출, 기존 복약 정보와의 비교 규칙을 먼저 정해야 한다. AI 음성 리포트도 전사 결과를 기존 리포트 필드에 매핑하는 기능이므로, 실제 provider 연동과 운영 저장 흐름은 OCR에서 파일 업로드, 추출 신뢰도, 매니저 확인 UX, 민감정보 삭제 기준을 먼저 검증한 뒤 진행하는 순서가 맞다.

다만 추가 설정이 필요 없는 범위는 OCR 이전에 진행해도 된다. 예를 들어 권한 안내 문구, 화면 진입 위치, mock 전사 결과를 폼에 채우는 UX, `api/` endpoint 계약 초안, 초안 데이터 필드 정의는 외부 계정이나 secret 없이 정리할 수 있으므로 선행 작업으로 분리할 수 있다.

## 사용자 흐름 초안

1. 매니저가 동행 종료 전후 리포트 작성 화면에서 `음성 메모로 초안 만들기`를 선택한다.
2. 앱은 녹음 목적, 민감정보 처리, 저장 기간을 짧게 안내하고 필요한 시점에만 마이크 권한을 요청한다.
3. 매니저가 짧은 음성 메모를 녹음한다.
4. 앱은 서버에 초안 생성을 요청한다. 서버는 세션 담당 매니저인지 확인한다.
5. 서버는 STT와 요약 처리를 수행하고 `summary`, `treatmentNotes`, `medicationNotes`, 복약 변화 관련 초안을 만든다.
6. 앱은 생성 결과를 입력 폼에 채우되, `AI 초안` 상태로 표시한다.
7. 매니저가 내용을 확인하고 수정한 뒤 저장해야만 `sessionReports`에 반영된다.
8. 보호자 화면에는 매니저가 확정 저장한 리포트만 노출한다.

## 녹음 권한 기준

- Android `RECORD_AUDIO` 권한은 앱 시작 시가 아니라 음성 초안 생성 액션을 누른 시점에 요청한다.
- 권한 안내 문구에는 녹음 목적이 리포트 초안 생성이며, 저장 전 매니저가 내용을 확인한다는 점을 포함한다.
- 권한 거부 시 기존 수동 리포트 입력 흐름을 그대로 제공한다.
- 백그라운드 장시간 녹음은 이번 범위에 넣지 않는다.

## 저장과 보관 기준

| 데이터 | 저장 여부 | 위치 초안 | 보관 기준 |
|---|---|---|---|
| 원본 음성 | 기본 미저장 | 필요 시 `voice-report-drafts/{sessionId}/{draftId}.m4a` 임시 경로 | 생성 성공/실패 후 삭제, 최대 24시간 |
| 전사문 | 기본 임시 저장 | `voiceReportDrafts` 또는 `ai_report_drafts` | 매니저 확인 전까지만 보관, 최대 24시간 |
| AI 요약 초안 | 임시 저장 가능 | `voiceReportDrafts` 또는 `ai_report_drafts` | 확정 저장 후 삭제 또는 감사용 최소 메타만 유지 |
| 확정 리포트 | 저장 | `sessionReports`, 전환 후 `session_reports` | 기존 리포트 보관 정책을 따른다 |
| 처리 메타 | 저장 가능 | 서버 로그 또는 audit collection/table | 요청자, 세션, 상태, 오류 코드 중심으로 남기고 원문은 남기지 않는다 |

파일명과 문서 ID에는 환자 이름, 병원명, 주민번호성 정보, 전화번호를 넣지 않는다.

## 처리 주체 기준

PostgreSQL 전환이 확정된 기준에서는 `api/` 백엔드를 1차 처리 주체로 둔다. Cloud Functions는 Firebase Auth/FCM/Storage 트리거처럼 Firebase에 강하게 묶인 작업에 남기고, AI 음성 리포트의 전사/요약/초안 생성 도메인 로직은 `api/`에서 관리한다.

확인한 외부 실행 기준은 다음과 같다.

- Cloud Run 서비스 요청 timeout은 기본 5분, 최대 60분까지 설정 가능하지만 15분을 넘기는 요청은 재시도와 idempotency 설계를 권장한다. AI 음성 처리는 긴 요청이 될 수 있으므로 동기 응답만 전제로 두지 않는다. 참고: https://cloud.google.com/run/docs/configuring/request-timeout
- Cloud Functions for Firebase는 함수별 timeout, memory, instance, concurrency를 런타임 옵션으로 조정할 수 있다. 다만 이 기능은 Firebase 이벤트 처리에 적합한 도구로 보고, PostgreSQL 도메인 데이터와 provider 연동의 최종 소유권은 `api/`에 둔다. 참고: https://firebase.google.com/docs/functions/manage-functions

따라서 구현 순서는 아래처럼 둔다.

1. OCR 이전에 가능한 범위를 먼저 분리한다: 권한 안내 문구, 화면 진입 위치, mock 전사 결과 UX, `api/` endpoint 계약 초안, 초안 데이터 필드 정의.
2. 로컬 mock으로 Android 입력 폼 반영을 검증한다.
3. OCR 처방전/복약 비교 설계에서 파일 업로드, 추출 신뢰도, 매니저 확인 UX, 민감정보 삭제 기준을 확정한다.
4. AI 음성 리포트 provider 후보와 보관 정책을 비교한다.
5. `api/`에 초안 생성 인터페이스를 구현한다.
6. Android는 Firebase ID token 또는 운영 전환 후 확정된 인증 토큰으로 `api/`에 초안 생성을 요청한다.
7. `api/`는 세션 담당 매니저 권한, rate limit, 요청 idempotency, provider 호출, 초안 저장/삭제를 담당한다.
8. provider key를 GitHub/API 서버 secret으로만 주입한다.
9. API 인증, 초안 TTL 삭제, 보호자 미노출 테스트를 추가한다.

앱에서 provider API를 직접 호출하는 방식은 secret 노출과 호출량 제어 문제 때문에 채택하지 않는다.

## 데이터 계약 초안

확정 리포트에는 기존 `SessionReport` 계약을 유지한다. AI 초안은 확정 리포트와 섞지 않고 별도 draft 계약으로 둔다.

```text
voiceReportDrafts/{draftId}
- sessionId
- appointmentRequestId
- managerUserId
- status: CREATED | TRANSCRIBING | SUMMARIZING | READY | FAILED | CONFIRMED | DISCARDED
- transcriptPreview
- summaryDraft
- treatmentNotesDraft
- medicationNotesDraft
- medicationNameDraft
- medicationChangeSummaryDraft
- medicationScheduleNoteDraft
- nextVisitAtDraft
- confidence
- warnings
- audioStoragePath
- providerName
- errorCode
- confirmedReportId
- createdAt
- updatedAt
- expiresAt
```

`sessionReports`에는 매니저가 확인한 값만 저장한다. 초안 ID를 남길 필요가 있으면 `sourceDraftId` 같은 메타 필드를 별도 검토하되, 보호자 화면에는 노출하지 않는다.

## 보안과 운영 리스크

| 리스크 | 영향 | 대응 기준 |
|---|---|---|
| 원본 음성에 민감 의료정보가 포함됨 | 유출 시 피해가 크다. | 원본 음성 기본 미저장, 임시 저장 TTL, 접근 권한 최소화 |
| AI 요약 환각 또는 오기록 | 보호자에게 잘못된 의료 정보가 전달될 수 있다. | 매니저 확인 전에는 보호자 노출 금지, 초안 표시, 필수 검토 |
| provider key 노출 | 무단 호출과 비용 증가가 생길 수 있다. | 앱 직접 호출 금지, 서버 secret 사용 |
| 호출량 증가 | Functions/API 비용과 provider 비용이 증가한다. | rate limit, 최대 녹음 길이, 실패 재시도 제한 |
| provider 지역/보관 정책 불명확 | 개인정보 처리 기준을 설명하기 어렵다. | provider 선정 전 데이터 처리 위치와 보관 정책 검토 |
| 전사문 장기 보관 | 불필요한 민감정보 보관이 된다. | 초안 TTL과 확정 후 삭제 정책 적용 |

## 구현 전 체크리스트

- [ ] OCR 이전에 진행 가능한 무설정 범위와 provider/API 연동이 필요한 범위를 분리한다.
- [ ] 권한 안내 문구, 화면 진입 위치, mock 전사 결과 UX, `api/` endpoint 계약 초안을 먼저 정리한다.
- [ ] OCR 처방전/복약 비교 설계와 구현 기준을 정리한다.
- [ ] STT/요약 provider 후보, 리전, 보관 정책, 비용 기준을 비교한다.
- [ ] 음성 녹음 동의/고지 문구와 마이크 권한 요청 시점을 확정한다.
- [ ] 초안 데이터의 TTL과 삭제 주체를 확정한다.
- [ ] `api/` 백엔드 초안 생성 endpoint와 인증 방식을 확정한다.
- [ ] 매니저 본인 세션만 초안 생성을 요청할 수 있는 API 인증 테스트를 추가한다.
- [ ] 보호자에게는 확정 리포트만 노출되는지 테스트한다.
- [ ] 원본 음성, 전사문, provider 응답 원문이 로그에 남지 않도록 점검한다.
- [ ] 실패 시 수동 입력으로 돌아가는 UX를 준비한다.

## 남은 결정

- STT/요약 provider를 무엇으로 쓸지 결정되지 않았다.
- 음성 원본을 아예 앱 로컬에서만 처리할지, 서버 임시 업로드를 허용할지 결정되지 않았다.
- AI 음성 리포트의 실제 provider 연동 시점은 OCR 처방전/복약 비교 기준 정리 이후로 둔다.
- 의료정보 처리 동의 문구와 개인정보 처리방침 반영 범위는 사람 검토가 필요하다.
