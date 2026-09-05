# PR 399 최신 변경 통합 검증

기준일: 2026-09-05

## 변경된 범위

- PR #399에 최신 `master`의 위치 기본 차단, 매니저 홈·가이드 화면, Functions·Firebase 도구 의존성 변경을 반영했다.
- `BookingFollowUpCoordinator.java`와 `strings.xml`의 충돌은 SOS 화면·전화 동작 제거를 유지하는 방향으로 해결했다. 제거된 SOS 코드만 사용하던 위치 정책 필드와 import도 정리했다.
- `notion-product-alignment.md`는 기존 매니저 위치 경로의 기본 `OFF`와 MVP SOS 제외 결정을 모두 유지했다.
- 자동 병합된 관리자 운영·예약 상세·세션 계약도 확인했다. 위치 차단은 유지하고 SOS 신규 동작만 제거했으며, 이미 반영된 매니저 화면을 되돌리지 않았다.

현재 MVP에서 제공하지 않는 동작을 다시 열지 않으면서 이미 승인된 화면·위치 정책을 보존하기 위한 통합이다. 한쪽 파일 전체를 선택하면 다른 정책 변경이 사라질 수 있어 충돌 구간만 정리했다. 기능 경계와 대안·리스크는 [MVP SOS·사고 대응 비활성 경계](../architecture/mvp-accident-response-boundary.md)를 따른다.

## 검증

| 범위 | 실행 | 결과 |
| --- | --- | --- |
| Android | `gradlew.bat testDebugUnitTest assembleDebug --console=plain` | 221개 테스트, 실패·제외 0개, debug 빌드 통과 |
| Core API | `core-api/gradlew.bat -p core-api check --console=plain` | 399개 테스트, 실패·제외 0개 |
| Functions | Node 22에서 `npm --prefix functions test` | 86개 통과, emulator 전용 3개 제외, 실패 0개 |
| Firebase 도구 | Node 22에서 `npm --prefix tools/firebase run test:toolkit` | 75개 통과 |
| 로컬 CI 경로 | `npm --prefix tools/firebase run preflight:ci -- --skip-workflow` | Android 빌드·단위 테스트 통과. 원격 운영 워크플로는 실행하지 않음 |

위치 기본 차단·개발 opt-in·production 강제 차단과 SOS 신규 쓰기 거부, 과거 `EMERGENCY` 전달 작업의 provider 호출 전 종료 테스트가 함께 통과했다. Functions·도구 검증은 `master`에 병합된 #401·#402 잠금파일을 반영하고 `npm ci`를 다시 실행한 뒤 수행했다.

## 남은 범위

- 새 커밋의 GitHub CI와 충돌 해결 내용 재검토를 확인한 뒤 PR을 병합한다.
- 실기기 시연, 실제 provider 종단 검증, JDBC SQL 캡처 테스트는 이번 통합 검증에서 수행하지 않았다.
- 운영 배포, migration, 데이터 적용·삭제는 수행하지 않았다. 기존 PostgreSQL·Firestore 데이터와 읽기 호환은 유지한다.
