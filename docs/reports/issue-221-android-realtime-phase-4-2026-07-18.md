# Issue 221 Android Realtime 전환 4단계

기준일: 2026-07-19

## 작업 목적

Android 채팅·읽음·위치 영속 쓰기를 Firestore에서 Spring Core API로 옮기고, Supabase private Broadcast를 상태 원본이 아닌 PostgreSQL 변경 신호로 사용한다.

## 선택한 방식

- Core API UUID를 API와 `companion-session:{UUID}` private topic 식별자로 사용한다.
- Android는 채팅·읽음·위치를 Core API에 저장하고 화면 진입, Broadcast 수신과 재연결 때 Realtime snapshot을 다시 조회한다.
- Firebase ID token은 private 채널 연결 직전에 강제 갱신하며 45분마다 연결을 새로 만든다.
- WebSocket은 25초 heartbeat, 최대 30초 지수 backoff와 300ms 이벤트 debounce를 사용한다.
- Broadcast payload는 상태로 병합하지 않고 본문·좌표 없는 변경 신호로만 처리한다.
- Core API는 PostgreSQL 커밋 뒤 `AFTER_COMMIT` listener를 같은 요청 안에서 실행해 FCM을 보낸다. 채팅 본문과 좌표 대신 세션·예약 식별자, 발신 역할과 고정 안내만 전달한다.
- `companionSessions`는 참여자 읽기만 허용하고 Android create·update·delete를 모두 거부한다.

## 구현한 내용

- Android 예약·매니저 저장소의 채팅, 읽음, 위치와 실시간 공유 상태를 Core API 호출로 전환했다.
- Core API snapshot으로 채팅 메시지, 첨부 메타데이터, 역할별 읽음 시각과 최근 위치 10건을 모델에 덮어쓴다.
- 환자·보호자 위치 화면, 안심 채팅 화면과 매니저 가이드 화면에 private Realtime 구독과 Core API 재조회를 연결했다.
- Flyway V12에 실시간 위치 공유 활성 상태·시작 시각과 병원·약국 자동 알림 단계·발송 시각을 추가했다.
- Core runtime에는 V12 네 컬럼 UPDATE만 추가하고 상태 시각은 SQL이 생성한다.
- Core API의 채팅·위치 알림은 transaction commit 이후 요청이 끝나기 전에 발송한다. Firestore token 조회와 FCM 호출은 각각 12초로 제한하며 실패해도 이미 commit된 업무 쓰기를 되돌리지 않는다.
- Firestore Rules emulator 검증을 모든 동행 세션 client 쓰기 거부 기준으로 변경했다.
- Android WebSocket 구현에는 OkHttp 5.3.0을 사용했다. 프로젝트에 없던 RFC 6455 클라이언트를 직접 구현하지 않고 Android 지원과 유지보수 범위가 명확한 라이브러리를 선택했다.

## ID와 첨부 경계

Realtime topic과 Core API는 Core UUID를 사용한다. 기존 Firebase Storage Rules는 legacy Firestore `companionSessions` 문서 참여자를 확인하므로 기존 세션 첨부 업로드는 legacy ID 경로를 유지하고 Core API는 core·legacy prefix를 모두 검증한다.

Firestore 보조 문서가 없는 Core-only 세션은 현재 첨부 binary 업로드 권한을 증명할 수 없다. PostgreSQL metadata 전환은 끝났지만 운영 전 Core API 중계 업로드 또는 짧은 수명의 서명 URL과 다운로드 권한을 구현해야 한다.

## 검증

- `core-api` 전체 `check` 통과
- Android `assembleDebug` 통과
- Firestore·Storage Rules emulator 7개 시나리오 통과
- V12를 개발 Supabase transaction 안에서 적용해 네 컬럼과 타입을 확인한 뒤 rollback했고 잔여 컬럼 0개 확인
- 채팅 생성 시 발신자를 제외한 참여자 알림 이벤트, 위치 알림 단계 변경 시 환자·보호자 이벤트 단위 테스트 추가

## 개발 환경 적용과 종단 검증

- `Core API DB Migration` run `29650223504`로 개발 DB에 V12를 적용하고 네 컬럼, 제약과 runtime grant를 확인했다.
- Cloud Run runtime 서비스 계정에 `roles/datastore.viewer`, `roles/firebasecloudmessaging.admin`을 부여했다. 서비스 계정 key 파일은 만들지 않았고 ADC를 사용한다.
- Preview deploy run `29650605742`에서 Firebase Admin 초기화를 첫 알림까지 지연했고, run `29651623086`에서 요청 밖 비동기 실행을 제거했다. 최신 리비전은 `bodeul-core-api-preview-00014-wnr`, commit은 `f509240`이다.
- 개발 Firestore Rules를 실제 배포해 `companionSessions` client create·update·delete 거부를 적용했다.
- SM-S921N에 최신 debug APK를 설치하고 실제 Firebase 사용자와 PostgreSQL 세션으로 채팅 생성, 읽음 갱신, 매니저 위치 저장, private join과 재연결 snapshot 복구를 확인했다.
- 매니저가 보낸 채팅에서 Core API 로그 `recipientCount=1`, `tokenCount=1`, `successCount=1`, `failureCount=0`을 확인했고, 실기기 알림 센터에 `companionChatUpdates` 알림이 생성됐다.
- private Realtime 10개 동시 연결은 모두 승인됐다. join 지연은 p50 2,379ms, p95·최대 3,734ms였고, Core API 메시지 저장은 200·436ms, `chat.changed` 수신은 10/10이었다.
- 이 부하에서 peak connection은 10이고 Broadcast 과금 메시지는 1회 발행과 10회 수신을 합쳐 11건이다. 운영 목표인 Pro의 월 500 peak connection, 500만 Realtime message 포함량보다 충분히 작다. 사용량 산정은 [Realtime Peak Connections](https://supabase.com/docs/guides/platform/manage-your-usage/realtime-peak-connections)와 [Realtime Messages](https://supabase.com/docs/guides/platform/manage-your-usage/realtime-messages)를 따른다.
- Supabase Security Advisor는 0건이었다. Performance Advisor의 unused index 항목은 저트래픽 개발 DB 특성상 삭제 근거로 사용하지 않았다.
- CLI 통합 요청은 App Check header 없이 실행돼 Core API에서 `app_check_verdict=missing`으로 기록됐다. Preview가 `observe`인 현재는 통과하지만 release App Check 검증 근거로 간주하지 않는다.
- 검증 후 임시 채팅·읽음·위치·세션·예약·사용자 레코드, Firestore 프로필과 Firebase Auth 계정을 삭제하고 DB 잔여 0건, Firestore 문서 없음과 임시 IAM 권한 회수를 확인했다. 실기기 앱 데이터와 검증 중 변경한 접근성 설정도 원복했다.
- 미사용 Rules helper를 제거한 뒤 emulator 7개 시나리오와 `bodeul-dev` 실제 Rules 재배포가 경고 없이 통과했다.

## 대안과 선택 이유

Firebase Functions가 PostgreSQL 채팅을 다시 조회해 푸시를 만드는 구조는 Core API와 별도 서버가 같은 업무 결과를 해석하게 된다. 현재 규모에서는 Core API가 커밋 결과를 알고 있으므로 FCM 보조 알림도 같은 서버 경계에서 발행하고, Firebase는 인증·기기 토큰·메시징 서비스로만 사용한다.

Android가 Broadcast payload를 직접 상태에 반영하는 방식은 빠르지만 중복·유실·순서 변경 복구가 어려워진다. 이벤트마다 Core API snapshot을 다시 읽으면 조회 비용은 늘지만 최종 상태를 PostgreSQL과 일치시킬 수 있어 현재 의료 동행 데이터 경계에 더 적합하다.

## 남은 범위

- #251 Core-only 세션의 첨부 업로드·다운로드 권한 이전
- FCM 실패율·지연 관측과 재시도 경계 보강
- release Play Integrity App Check 유효 요청과 `enforce` rollback 검증
- production 전환은 연말 운영 승인 뒤 별도 실행
- #222에서 채팅 180일, 첨부 30일, 위치 24시간 만료 자료를 정리하는 일일 파기 구현

## 리스크

- FCM은 보조 경로이므로 발송 실패가 채팅 저장을 실패시키지 않는다. 현재 요청 안에서 전송해 Cloud Run request-based CPU 경계는 지키지만 FCM 지연이 API 응답 시간에 포함된다. p95가 5초를 넘거나 재시도가 필요해지면 Cloud Tasks 같은 durable queue로 분리한다.
- Cloud Run runtime의 Firestore 조회 권한은 FCM 토큰 조회에만 사용하지만 IAM은 문서 단위로 제한되지 않는다. 운영 전 토큰 registry를 PostgreSQL 또는 전용 중계 경계로 옮길지 다시 검토한다.
- private channel 인가는 연결 중 cache되므로 참여 관계가 바뀌면 token 갱신 또는 재연결까지 이전 권한이 남을 수 있다.
- Core API snapshot 재조회는 Broadcast 수보다 DB read가 늘어난다. 실제 동시 사용자 수로 부하를 확인한 뒤 polling 주기와 debounce를 조정한다.
