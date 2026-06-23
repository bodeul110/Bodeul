# 2026-06-20 앱 테스트 준비

## 구현한 내용

- 전체 점검 후속 조치가 `master`에 병합된 상태를 기준으로 앱 테스트 준비를 마감했다.
- 테스트용 Android debug APK 위치, 설치 기준, 우선 확인 시나리오를 정리했다.
- 점검 상세 내역과 변경 근거는 `project-check-followup-2026-06-20.md`에 연결했다.

## 변경된 범위

- 문서
  - `app-test-ready-2026-06-20.md`

## 앱 테스트 준비 상태

- 기준 브랜치: `master`
- 원격 동기화 상태: `origin/master`와 동일
- 추적 대상 작업 트리 상태: 변경 없음
- 테스트 대상 빌드: Android `debug`
- APK 경로: `app/build/outputs/apk/debug/app-debug.apk`
- APK 생성 시각: `2026-06-20 18:07:25`
- APK 크기: `56,305,329 bytes`

## 설치 기준

- 앱 이름: `보들`
- applicationId: `com.example.bodeul`
- versionName: `1.0`
- versionCode: `1`
- minSdk: `24`
- targetSdk: `37`

## 설치 및 실행

- 설치 명령

```powershell
adb install -r D:\BoDeul\app\build\outputs\apk\debug\app-debug.apk
```

- 기존 데이터까지 초기화하고 재설치할 때

```powershell
adb uninstall com.example.bodeul
adb install D:\BoDeul\app\build\outputs\apk\debug\app-debug.apk
```

## 우선 확인 시나리오

1. 첫 실행 후 역할 선택 화면이 바로 열리는지 확인
2. Android 13 이상에서 알림 권한 안내가 한 번만 노출되고, 승인/거부 후 원래 흐름으로 복귀하는지 확인
3. 로그인 후 사용자 역할별 홈 진입이 정상인지 확인
4. 예약 화면 진입, 병원 선택, 위치 선택, 후속 화면 이동이 정상인지 확인
5. 매니저 가이드, 매니저 홈, 관리자 화면의 분리된 include 레이아웃이 깨지지 않는지 확인
6. 알림 수신 가능 기기에서 채팅/문의/위치 알림이 권한 상태에 맞게 동작하는지 확인

## 검증 기준

- `./gradlew.bat assembleDebug` 성공 상태
- `./gradlew.bat testDebugUnitTest` 성공 상태
- `./gradlew.bat --rerun-tasks lintDebug` 성공 상태
- lint 결과: `No issues found.`

## 참고 문서

- 점검 상세: `project-check-followup-2026-06-20.md`

## 남은 범위

- 테스트 계정, 테스트 데이터, 실기기 목록은 운영 기준에 따라 별도로 확정해야 한다.
- Firebase 실데이터 상태 검증은 PR 빌드 게이트에서 분리했으므로, 앱 동선 외 운영 데이터 검증은 별도 점검 절차로 진행해야 한다.
- 현재 문서는 debug APK 기준이다. 배포용 서명 APK 또는 AAB 준비는 이번 범위에 포함하지 않았다.
