# Issue 190 ARM 실기기 검증 기록

기준일: 2026-07-17

관련 이슈: [#190 Android App Check debug/Play Integrity 실기기 검증](https://github.com/bodeul110/Bodeul/issues/190)

## 작업 목적

ARM Android 실기기에서 App Check debug token, 로그인과 역할별 주요 화면, Firestore 세션 조회, Storage 채팅 첨부, Spring Core API 장소 검색, Kakao Map 렌더링을 실제 개발 환경 기준으로 확인한다.

## 선택한 방식

- `bodeul-dev`의 기준선 테스트 계정과 seed 요청·세션을 사용해 데이터 생성 범위를 줄였다.
- 채팅 첨부는 실제 Storage 업로드와 Firestore 메시지 저장까지 수행했다.
- App Check debug token은 실기기 로그에서 메모리로만 추출해 공식 REST API로 등록하고 원문을 파일, Issue, PR에 남기지 않았다.
- Core API는 `observe`를 유지한 채 Cloud Logging의 판정과 Cloud Run HTTP 요청 로그를 함께 확인했다.

## 대안

- 화면만 수동으로 열어 확인할 수 있지만, 역할 전환과 대상 Activity 확인을 반복하기 어렵다.
- App Check 없이 Core API 200만 확인할 수 있지만, 등록되지 않은 앱의 요청도 성공하므로 #190 완료 근거가 되지 않는다.
- release keystore를 임시 생성해 Play Integrity를 시험할 수 있지만, 향후 앱 업데이트 신뢰 체인을 임의 키로 고정하게 되므로 제외했다.

## 선택 이유

현재 개발 단계에서는 실제 Firebase와 Cloud Run 경계를 검증하되 production 데이터나 release 서명 체계를 임의로 만들지 않는 것이 중요하다. 따라서 개발 프로젝트의 debug provider로 기능 경로를 확인하고, 팀 소유 release key가 필요한 범위는 별도 게이트로 남겼다.

## 수정한 문제

### Storage 채팅 첨부 403

기존 Storage Rules는 사용자, 동행 세션, 예약 요청 문서를 읽어 한 요청에서 Firestore 문서 3개에 접근했다. Cloud Storage Rules가 허용하는 Firestore 문서 접근 한도를 넘어서 참여자의 정상 첨부도 거부됐다.

- `companionSessions`에 `patientUserId`, `guardianUserId`를 직접 저장한다.
- 관리자 배치 생성 시 `getAfter()` 예약 요청과 두 참여자 ID가 일치하는지 검증한다.
- 참여자 ID는 세션 생성 후 변경하지 못하게 한다.
- Storage Rules는 사용자 문서와 세션 문서 두 개만 읽어 참여자를 판정한다.
- 기존 개발 세션 2건은 적용 전 로컬 백업 후 backfill했고, 적용 후 미이관 대상 0건을 확인했다.

### Firestore 세션 조회 권한 오류

Rules는 쿼리 결과를 필터링하지 않으므로 `appointmentRequestId` 조건만으로는 현재 사용자가 모든 결과의 참여자임을 증명할 수 없었다.

- 환자 조회에는 `patientUserId == 현재 UID`를 추가했다.
- 보호자 조회에는 `guardianUserId == 현재 UID`를 추가했다.
- 예약 상세, 취소, 채팅 전송, 읽음 처리, 상세 로드의 공통 쿼리에 같은 조건을 적용했다.

### Kakao Map 401

Kakao Developers의 Android 플랫폼에는 `com.example.bodeul`과 현재 debug 서명 키 해시가 올바르게 등록돼 있었지만, 빌드가 다른 Native App Key를 사용하고 있었다.

- 실제 키를 추적되는 `gradle.properties`에서 제거했다.
- Android 플랫폼 전용 Native App Key는 Git에서 제외되는 `local.properties`에만 둔다.
- 재빌드 후 지도 인증 HTTP 200, 지도 엔진 시작, `onRenderViewSuccess`, 지도 타일과 위치 마커 렌더링을 확인했다.

## 실기기 결과

| 항목 | 결과 |
| --- | --- |
| 기기 | Samsung SM-S921N, Android 16, ARM64 |
| 빌드 | debug APK 설치 성공 |
| 역할별 화면 | 15건 통과, 경고 0건, 실패 0건 |
| 채팅 첨부 | 보호자 첨부 업로드, 메시지 저장, 이미지·파일명·열기 동작 확인 |
| Firestore/Storage | 참여자 세션 조회와 첨부 요청에서 권한 오류 없음 |
| App Check | 실기기 debug token 비공개 등록 후 Android token 발급 확인 |
| Core API | `/api/places/search` 3건 모두 `app_check_verdict=valid`, HTTP 200 |
| Kakao Map | 인증 200, 지도 타일과 현재 위치 마커 렌더링 |
| 계측 테스트 | SM-S921N에서 `connectedDebugAndroidTest` 1건 통과 |

역할별 화면 증적에는 환자·보호자 홈, 예약 작성, 예약 상세, 종료 후속, 보호자 리포트, 보호자·매니저 채팅, 매니저 홈·이력·가이드·문의·프로필, 관리자 대시보드와 채팅 첨부 결과가 포함된다. 예약 작성 화면은 진입까지만 확인했으며 새 예약 제출 데이터는 만들지 않았다.

Kakao SDK가 CDN의 한글 굵은 글꼴을 내려받는 과정에서 TLS 경고를 한 번 기록했지만, 기본 지도 글꼴과 타일, 마커는 정상 렌더링됐다. 사용자 흐름을 막는 오류는 아니므로 SDK 자체 경고로 기록하고 반복적인 표시 이상이나 크래시가 생길 때 별도 추적한다.

## 검증 명령

```powershell
npm.cmd --prefix tools/firebase run test:toolkit
npm.cmd --prefix tools/firebase run test:rules
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
npm.cmd --prefix tools/firebase run capture:app -- --preset manager-guide --serial <device> --force-sign-in
```

- Firebase 운영 도구 테스트: 21건 통과
- Firestore/Storage Rules emulator 시나리오: 7건 통과
- Android unit test와 debug APK 빌드 성공
- 개발 Firestore/Storage Rules 배포 성공

## 남은 범위

- 팀 소유 release keystore, alias와 암호 보관 주체를 확정한다.
- release signing certificate SHA-256을 Firebase Android 앱과 Kakao Android 플랫폼에 등록한다.
- Google Play Console과 Firebase Play Integrity 연결 후 release 후보를 ARM 실기기에서 검증한다.
- #192에서 Core API `enforce` 전환과 `observe` 즉시 롤백을 재현한다.
- Next.js 관리자 웹의 reCAPTCHA Enterprise와 custom backend App Check 검증은 관리자 웹 이슈에서 별도로 진행한다.

현재 결론은 `Android debug 실기기 경로 통과, release Play Integrity 보류`다. debug 결과만으로 Firebase 서비스 전체 enforcement를 켜지는 않는다.

## 참고

- [Cloud Storage Rules의 Firestore 문서 접근 한도](https://firebase.google.com/docs/storage/security/rules-conditions)
- [App Check debug token REST API](https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.debugTokens/create)
- [KakaoMaps SDK Android 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)
- [Kakao Android 플랫폼과 키 해시 설정](https://developers.kakao.com/docs/en/android/getting-started)
