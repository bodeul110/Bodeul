# Issue 222 production 위치 게이트 구현 기록

기준일: 2026-09-03

## 작업 목적

현재 앱과 Core API에 남아 있는 매니저 단말의 1회·연속 위치 경로가 정책과 종단 검증 없이 release·production에서 실행되지 않도록 기본 차단 경계를 둔다. 이번 작업은 기존 경로를 안전하게 닫는 범위이며, 제품 목표인 환자 단말 GPS 1분 공유를 구현하거나 완료 처리하지 않는다.

## 선택한 방식

- Android와 Core API의 legacy 매니저 위치 기능을 기본 `OFF`로 둔다.
- Android release와 Core API production은 하드코딩된 `false`를 사용해 설정 실수로 활성화할 수 없게 한다.
- debug·Preview에서만 테스트 목적의 명시적 opt-in을 허용한다.
- Core API가 `OFF`이면 위치 좌표 read/write와 legacy 위치 필드 PATCH를 거부하고, 이미 저장된 위치 필드를 응답에서 마스킹한다.
- Android가 `OFF`이면 위치 권한 요청, 1회 공유와 연속 공유 진입을 노출하거나 시작하지 않는다.
- Core API의 FCM listener는 `OFF`에서 수신자 조회 전 위치 알림을 중단하고, Firestore legacy 위치 알림 Functions도 기본 `OFF`와 production 프로젝트 강제 `false`를 적용한다.

Android debug opt-in은 로컬 또는 Gradle property `bodeulLegacyManagerLocationEnabled=true`로만 허용한다. Core API의 legacy 게이트는 `BODEUL_SESSION_LEGACY_MANAGER_LOCATION_ENABLED`이며 기본값은 `false`다. 두 설정은 향후 환자 위치 기능의 활성화 스위치로 재사용하지 않는다.

## 검토한 대안

1. 기존 위치 테이블·API·앱 코드를 즉시 삭제하는 방식
   - 운영 노출은 확실히 막을 수 있지만 legacy 데이터 읽기 호환과 rollback 경계를 한 번에 제거해 변경 범위가 커진다.
2. Android 화면만 숨기는 방식
   - 구버전 앱이나 직접 API 요청이 좌표를 쓰거나 읽을 수 있어 서버 보안 경계가 남지 않는다.
3. 서버만 차단하는 방식
   - 데이터 저장은 막아도 앱이 권한을 요청하고 실패 동작을 노출할 수 있어 이용자에게 불필요한 위치 수집 인상을 준다.

## 선택 이유

현재 MVP 규모에서는 아직 환자 위치 수집·동의·철회·보호자별 인가·파기 계약을 종단 검증하지 않았다. 기존 코드를 전부 삭제하기보다 앱과 서버를 함께 기본 거부로 닫으면 legacy 호환과 향후 별도 설계 여지를 유지하면서 release·production의 우발적 위치 처리를 막을 수 있다. Preview opt-in은 기존 경로의 회귀 테스트에만 사용하고 production은 설정과 무관하게 차단하는 편이 현재 보안·운영 위험에 맞다.

## 변경 범위

| 경계 | 변경 후 동작 |
| --- | --- |
| Android debug | 기본 `OFF`, 개발자가 명시적으로 opt-in한 경우에만 legacy 매니저 위치 경로 사용 |
| Android release | 하드코딩 `false`, 위치 권한 요청·1회 공유·연속 공유 시작 차단 |
| Android Firebase 미설정 환경 | Core API 장소 검색과 Supabase Realtime 인증 경로를 시작하지 않고 Mock 경로로 안전하게 대체 |
| Core API 로컬 | `BODEUL_SESSION_LEGACY_MANAGER_LOCATION_ENABLED=false`가 기본값 |
| Core API Preview | 기본 `false`, 허용된 boolean 값을 명시적으로 넣은 경우에만 opt-in |
| Core API production | workflow와 런타임 환경 모두 `false`로 고정 |
| Core API `OFF` | 좌표 read/write, legacy 위치 PATCH 거부 및 저장된 위치 필드 마스킹 |
| Core API FCM | `OFF`이면 위치 알림 이벤트를 수신자 조회와 메시지 생성 전에 중단. 채팅 알림은 유지 |
| Firebase Functions | Firestore legacy 위치 알림은 기본 `OFF`; production 프로젝트는 설정값과 무관하게 강제 `false` |

DB schema와 보존 데이터는 변경하지 않는다. 기존 좌표의 실제 만료·삭제는 별도 #222 파기 작업이 담당한다.

## 제외 범위

- 환자 단말 GPS 1분 수집·공유
- 위치 수집 동의와 즉시 철회
- 보호자별 위치 열람 인가
- 종료 시 즉시 삭제 시도와 실패·지연 건의 24시간 이내 최종 삭제
- production 배포, DB migration, 실데이터 변경·검증

## 검증

- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`: 성공, Android 단위 테스트 212개 통과
- 기본 debug 생성 리소스: `bodeul_legacy_manager_location_enabled=false` 확인
- `-PbodeulLegacyManagerLocationEnabled=true` debug 빌드: 성공, 명시적 개발 opt-in 경로 유지 확인
- 테스트 서명을 사용한 `assembleRelease -PbodeulLegacyManagerLocationEnabled=true --no-configuration-cache`: 성공, 입력값과 무관하게 release 생성 리소스가 `false`임을 확인
- `.\core-api\gradlew.bat -p core-api check --console=plain`: 성공, Core API 테스트 397개 통과
- Node 22.23.2 `functions` 테스트: 88개 중 85개 통과, Emulator 전용 3개 건너뜀, 실패 0
- `yq e '.' .github/workflows/core-api-preview-deploy.yml`: 성공
- `yq e '.' .github/workflows/core-api-production-deploy.yml`: 성공
- `git diff --check`: 성공
- 실기기 검증: Samsung `SM-S921N`, Android 16(API 36)에 기본 `OFF` debug APK 설치 및 매니저 데모 로그인 성공
- 실기기 권한·서비스 검증: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`가 모두 미승인 상태로 유지됐고 `ManagerLocationService`, 앱의 시스템 위치 등록, crash 로그가 없음을 확인
- 실기기 UI 검증: 매니저 가이드 상단부터 하단까지 이동하며 `위치`, `GPS`, `좌표`, `실시간 공유` 문구가 없고 병원 안내·현장 메모 등 비위치 기능은 정상 진입함을 확인
- Firebase 설정 파일이 없는 debug 환경에서 Core API 장소 검색이 `FirebaseApp`을 직접 열어 가이드가 종료되는 기존 문제를 발견해, Firebase 설정 확인 후에만 인증 경로를 시작하도록 보완하고 동일 실기기에서 재현되지 않음을 확인
- Preview 배포·실호출: 미실행
- production 배포·실데이터 검증: 미실행

## 리스크

- debug·Preview opt-in을 공유 환경에서 켜면 기존 매니저 위치 경로가 다시 동작할 수 있으므로 목적과 종료 시점을 명시하고 기본값으로 되돌려야 한다.
- 게이트는 저장된 legacy 좌표를 삭제하지 않는다. 마스킹과 접근 차단만으로 파기 완료를 주장하지 않는다.
- 이번 변경은 HTTP 서비스 경계를 닫지만 PostgreSQL `record_companion_location` 함수 권한과 위치 변경 trigger를 제거하지 않는다. Core runtime 자격 증명으로 DB 함수를 직접 실행하면 좌표 행과 좌표가 아닌 Realtime 이벤트 식별자가 만들어질 수 있으므로, production 위치 기능을 열기 전에는 DB 실행 권한·trigger까지 별도 게이트로 묶어 검증해야 한다.
- 위치 범위만 허용된 보호자는 기능 `OFF`에서 조회할 수 있는 범위가 없으므로 Realtime snapshot이 빈 성공 응답이 아니라 403으로 닫힌다. 위치 외 범위를 임의로 확대하지 않는 fail-closed 계약으로 유지한다.
- 구버전 앱과 최신 서버 또는 최신 앱과 구버전 서버가 섞이면 위치 UI와 API 거부 상태가 다를 수 있다. 배포 전 조합별 실패 동작을 확인해야 한다.
- 향후 환자 GPS 경로가 legacy 플래그를 재사용하면 수집 주체와 인가 경계가 섞인다. 별도 계약과 별도 활성화 조건을 사용해야 한다.

## 남은 범위

- 필요할 때만 Preview에서 명시적 opt-in 후 legacy 회귀 시나리오 확인
- production 배포 뒤 release 빌드와 실제 Core API를 조합해 위치 API·알림이 계속 차단되는지 확인
- 환자 단말 1분 위치 경로를 동의·철회·인가·즉시 파기와 함께 별도 설계·구현

관련 문서:

- [데이터 보관 및 파기 정책](../operations/data-retention-policy.md)
- [매칭·동행·리포트 PostgreSQL 전환 계약](../architecture/companion-session-core-api.md)
- [Notion 제품 기준 정합성](../planning/notion-product-alignment.md)
