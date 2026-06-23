# 2026-06-20 전체 프로젝트 점검 후속 조치

## 구현한 내용

- Android 13 이상 알림 권한 안내와 진입 흐름을 정리하고, 안내를 한 번만 거친 뒤 원래 목적지로 복귀하도록 수정했다.
- 알림 notifier 3종이 공통 helper로 권한과 시스템 알림 허용 상태를 확인한 뒤에만 `notify()`를 호출하도록 보강했다.
- 홈 화면과 건강 정보 화면의 `<include>` margin 적용 문제를 수정해 상단 배너 여백이 실제로 반영되도록 맞췄다.
- overdue 카운터 문자열과 권한 안내 문구를 현재 동작과 일치하도록 정리했다.
- `DefaultLocale`, 접근성, RTL, 터치 클릭 처리, 아이콘 설명, 문자열 표기 관련 lint warning을 정리했다.
- 병원 선택, 예약, 관리자, 매니저 가이드 화면의 레이아웃 구조를 include 중심으로 나눠 `NestedScrolling`, `NestedWeights`, `UselessParent`, `TooManyViews` 경고를 제거했다.
- `FirebaseAuthRepository`의 Google 서버 클라이언트 ID 조회를 리소스 직접 참조로 바꾸고, `ServiceLocator`의 `FirebaseFirestore` 정적 보관을 제거해 `DiscouragedApi`, `StaticFieldLeak` 경고를 없앴다.
- 런처 아이콘을 XML 기반 레거시/적응형 조합으로 재구성하고, 중복 PNG 아이콘과 미사용 로고 자산을 제거했다.
- 미사용 drawable, color, layout, Firebase Auth 설정 리소스와 문자열 104개를 삭제해 `UnusedResources` 경고를 정리했다.
- `RoleSelectionActivity`를 실제 런처로 전환하고 `SplashActivity` 및 전용 스플래시 레이아웃을 제거해 `CustomSplashScreen` 경고를 없앴다.
- 앱 SDK와 의존성 버전을 lint 권고값으로 상향했다.
  - `compileSdk = 37`, `targetSdk = 37`
  - Gradle wrapper `9.6.0`
  - Android Gradle Plugin `9.2.1`
  - Google Services `4.5.0`
  - Firebase BOM `34.15.0`
  - AndroidX Credentials `1.6.0`
  - Material `1.14.0`
  - Google ID `1.2.0`
  - AndroidX JUnit `1.3.0`
  - Espresso `3.7.0`
- 로컬 빌드 환경에 `platforms;android-37.0`, `build-tools;37.0.0`을 설치해 SDK 상향 후 바로 검증 가능하도록 맞췄다.

## 변경된 범위

- 인증/진입 흐름
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/example/bodeul/ui/auth/EntryFlowCoordinator.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java`
  - `app/src/main/java/com/example/bodeul/ui/auth/SplashActivity.java` 삭제
  - `app/src/main/res/layout/activity_splash.xml` 삭제
- 알림/데이터 계층
  - `app/src/main/java/com/example/bodeul/firebase/ClientSupportPushNotifier.java`
  - `app/src/main/java/com/example/bodeul/firebase/CompanionChatPushNotifier.java`
  - `app/src/main/java/com/example/bodeul/firebase/CompanionLocationAlertPushNotifier.java`
  - `app/src/main/java/com/example/bodeul/util/NotificationPermissionSupport.java`
  - `app/src/main/java/com/example/bodeul/data/ServiceLocator.java`
  - `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
  - `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
- 화면/레이아웃
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/layout/activity_health_info.xml`
  - `app/src/main/res/layout/activity_booking.xml`
  - `app/src/main/res/layout/activity_booking_hospital_selector.xml`
  - `app/src/main/res/layout/activity_booking_location_selector.xml`
  - `app/src/main/res/layout/activity_login.xml`
  - `app/src/main/res/layout/activity_manager_document_registration.xml`
  - `app/src/main/res/layout/activity_manager_guide.xml`
  - `app/src/main/res/layout/activity_manager_home.xml`
  - `app/src/main/res/layout/activity_manager_profile.xml`
  - `app/src/main/res/layout/activity_role_selection.xml`
  - `app/src/main/res/layout/activity_admin.xml`
  - `app/src/main/res/layout/item_manager_document_registration.xml`
  - `app/src/main/res/layout/item_manager_guide_stage.xml`
  - `app/src/main/res/layout/item_manager_step.xml` 삭제
  - `app/src/main/res/layout/item_permission_guide.xml`
  - `app/src/main/res/layout/include_admin_action_center_section.xml`
  - `app/src/main/res/layout/include_admin_action_delivery_section.xml`
  - `app/src/main/res/layout/include_admin_guide_catalog_section.xml`
  - `app/src/main/res/layout/include_admin_managed_section.xml`
  - `app/src/main/res/layout/include_admin_monitoring_section.xml`
  - `app/src/main/res/layout/include_admin_settlement_section.xml`
  - `app/src/main/res/layout/include_admin_support_section.xml`
  - `app/src/main/res/layout/include_booking_form_health_linked.xml`
  - `app/src/main/res/layout/include_booking_form_notes_actions.xml`
  - `app/src/main/res/layout/include_booking_form_options_payment.xml`
  - `app/src/main/res/layout/include_booking_form_visit.xml`
  - `app/src/main/res/layout/include_manager_guide_location_card.xml`
  - `app/src/main/res/layout/include_manager_guide_notes_card.xml`
  - `app/src/main/res/layout/include_manager_guide_report_card.xml`
- 리소스/아이콘
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values/firebase_auth_config.xml` 삭제
  - `app/src/main/res/drawable/bg_primary_gradient.xml` 삭제
  - `app/src/main/res/drawable/ic_launcher_legacy.xml`
  - `app/src/main/res/drawable/ic_launcher_legacy_background.xml`
  - `app/src/main/res/drawable/ic_launcher_round_legacy.xml`
  - `app/src/main/res/drawable/ic_launcher_round_legacy_background.xml`
  - `app/src/main/res/drawable-nodpi/bodeul_logo_full_white.png` 삭제
  - `app/src/main/res/drawable-nodpi/bodeul_logo_icon.png` 삭제
  - `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
  - `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
  - `app/src/main/res/mipmap-hdpi/ic_launcher.png` 삭제
  - `app/src/main/res/mipmap-hdpi/ic_launcher_round.png` 삭제
  - `app/src/main/res/mipmap-mdpi/ic_launcher.png` 삭제
  - `app/src/main/res/mipmap-mdpi/ic_launcher_round.png` 삭제
  - `app/src/main/res/mipmap-xhdpi/ic_launcher.png` 삭제
  - `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png` 삭제
  - `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` 삭제
  - `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png` 삭제
  - `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` 삭제
  - `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png` 삭제
- 빌드/버전 설정
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `gradle/wrapper/gradle-wrapper.properties`
- 문서
  - `project-check-followup-2026-06-20.md`

## 검증

- `./gradlew.bat assembleDebug` 성공
- `./gradlew.bat testDebugUnitTest` 성공
- `./gradlew.bat --rerun-tasks lintDebug` 성공
- `./gradlew.bat testDebugUnitTest --warning-mode all --no-configuration-cache` 성공
- `androidGradlePlugin = 9.3.0`은 Google Maven 메타데이터에 없어 플러그인 해석에 실패했다.
- `androidGradlePlugin = 9.3.0-rc01` 임시 적용 후 `./gradlew.bat clean assembleDebug testDebugUnitTest --warning-mode all --no-configuration-cache lintDebug --rerun-tasks` 성공
- 최종 선택은 `androidGradlePlugin = 9.2.1` 유지다. 이유는 Android Studio Quail 1 안정 채널의 공식 AGP 지원 범위가 `7.1-9.2`이고, RC 유지 비용이 AGP 내부 deprecation 경고 1건보다 크기 때문이다.
- 현재 lint 결과: `No issues found.`

## 남은 범위

- 앱 코드 기준 lint 에러와 warning은 남아 있지 않다.
- Gradle 10 비호환 deprecation 경고는 `9.2.1`에서 프로젝트 스크립트가 아니라 AGP 내부 `com.android.internal.application`에서 발생했다. `GRADLE_OPTS=-Dorg.gradle.deprecation.trace=true` 기준 `com.android.build.gradle.internal.dependency.VariantDependenciesBuilder.build()` 경로가 확인됐다.
- `9.3.0` 안정판은 현재 Google Maven 메타데이터에 없고, 배포된 최신 9.3 계열은 `9.3.0-rc01`이다.
- `9.3.0-rc01` 적용 후에는 해당 AGP 내부 deprecation이 문제 보고서에서 사라지고, 문제 보고서에는 Java 컴파일러의 일반 deprecated API note 2건만 남는다. 다만 이 결과는 채택 근거가 아니라 비교 근거로만 유지한다.
- 최종적으로는 안정 채널 IDE 공식 지원 범위 안에 머무르기 위해 `9.2.1`을 유지하고, AGP 내부 deprecation은 후속 안정판 `9.3.x`가 실제 배포되고 IDE 지원 범위에 들어오면 다시 올리는 편이 안전하다.
- `.idea/claudeCodeTabState.xml`은 로컬 IDE 상태 파일이라 이번 작업 범위에 포함하지 않았다.
