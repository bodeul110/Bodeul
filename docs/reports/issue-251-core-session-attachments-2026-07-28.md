# Issue 251 Core-only 세션 첨부 경계

## 작업 목적

Firestore `companionSessions` 문서가 없는 세션에서도 환자·보호자·배정 매니저만 채팅 첨부를 올리고 내려받게 한다.

## 선택한 방식

- Android는 메시지 본문과 JPEG·PNG·PDF 원본을 하나의 multipart 요청으로 Spring Core API에 보낸다.
- Core API는 PostgreSQL 참여 관계를 확인하고 Firebase Storage에 원본을 저장한 뒤 같은 메시지의 metadata를 PostgreSQL에 기록한다.
- 다운로드는 서명 URL을 노출하지 않고 Core API가 참여 관계, 삭제 상태와 30일 만료를 매번 확인한 뒤 `no-store`로 반환한다.
- 객체 경로는 세션 ID, `clientMessageId`, 순서와 SHA-256으로 결정한다. DB 저장 실패 시 이번 요청에서 새로 생성된 객체만 보상 삭제한다.
- 기존 Firestore 세션의 직접 Storage 경로는 구버전 앱 호환을 위해 유지한다. Core-only 경로의 클라이언트 직접 업로드는 Rules에서 거부한다.

## 검토한 대안

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| 짧은 수명의 서명 URL | Core API 메모리와 네트워크 부하가 작다. | 서명 권한, 업로드 완료 확인과 고아 객체 정리가 추가되어 현재 최대 3개·30 MiB 규모에는 과하다. |
| Firestore에 권한 보조 문서 재생성 | 기존 Storage Rules를 재사용할 수 있다. | PostgreSQL 단일 업무 원본 목표와 충돌해 제외한다. |
| 공개 다운로드 URL | 구현이 단순하다. | 세션 탈퇴·만료 후 접근을 회수하기 어려워 제외한다. |

## 선택 이유

현재 MVP 규모에서는 권한 판정과 메시지 저장을 한 서버 요청에서 처리하는 편이 운영과 장애 복구가 단순하다. 파일 수나 트래픽이 커지면 서명 URL과 완료 확인 API로 전환한다.

## 리스크

- multipart 본문은 Cloud Run 메모리 기반 파일 시스템에도 영향을 준다. 파일 바이트는 하나씩 검증·저장하고 1 GiB 인스턴스 concurrency를 8로 낮췄지만, 동시 대용량 업로드가 늘면 메모리와 지연을 다시 측정해야 한다.
- Storage 저장 뒤 DB 저장 실패 시 보상 삭제를 시도하지만 삭제 자체가 실패할 수 있다. #222 일일 정리 작업이 metadata 없는 객체를 최종 회수해야 한다.
- 기존 앱의 직접 Storage 경로는 전환 기간에만 유지한다. 지원 버전 하한이 정해지면 JSON 첨부 metadata 입력과 legacy Rules 제거를 별도 진행한다.

## 현재 검증

- `core-api` 전체 `check` 통과
- Android `assembleDebug` 통과
- Firestore/Storage Rules emulator 7개 시나리오 통과. Firestore 세션 문서가 없는 경로의 클라이언트 직접 업로드 거부 포함
- 개발·production Firebase 기본 버킷에 각 Cloud Run 런타임 계정의 버킷 단위 `roles/storage.objectUser` 적용
- 개발 Storage Rules 배포 완료
- 첨부 저장 전에 PostgreSQL 세션 참여자와 활성 상태를 확인하고, 메시지 저장 시 다시 확인하도록 보완했다. 권한 확인 실패 시 Storage 쓰기가 발생하지 않는 테스트를 포함해 `core-api` 전체 `check`를 재검증했다.
- Core API Preview deploy run `30355824697` 성공. merge SHA `2a3b01f6083dbafd51b8e6b8075bbe36334acebc` 이미지와 리비전 `bodeul-core-api-preview-00016-v94`의 트래픽 100%를 확인했다.
- 배포 리비전에서 `/health` 200, 무인증 multipart 첨부 메시지와 첨부 다운로드 각각 401 `missing_authorization`을 독립 요청으로 확인했다.

## 남은 검증

- 현재 ADB 연결 기기가 없어 인증된 Core-only 세션의 실기기 업로드·다운로드는 확인하지 못했다.
- 실제 DB 저장 실패를 유도한 Storage 보상 삭제와 30일 만료·삭제 종단 시나리오는 #222 파기 작업과 함께 검증한다.
- production Rules와 Core API는 production 출시 게이트가 열릴 때 적용한다.
