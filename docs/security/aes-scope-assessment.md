# AES 적용 범위 판단 메모

기준일: 2026-05-04

## 목적

- 팀에서 말한 `AES-256 이상의 보안` 요구를 실제 프로젝트 구조에 맞게 해석한다.
- 현재 코드 기준으로 `로컬에 남는 데이터`와 `서버에만 두어야 하는 데이터`를 분리한다.
- 앱 레벨 AES를 어디에 써야 하는지, 어디에는 쓰지 말아야 하는지 기준선을 만든다.

## 결론

- 현재 Android 앱과 관리자 웹 코드 기준으로, `앱이 직접 영속 저장하는 민감 개인정보/건강정보는 거의 없다`.
- 따라서 지금 단계에서 `앱 전역 AES-256 도입`을 바로 요구하는 것은 과하다.
- 현재 더 중요한 것은 `민감 데이터 로컬 저장 금지`, `App Check`, `권한 통제`, `운영 도구 경계 분리`다.
- 다만 추후 `오프라인 저장`, `문서 다운로드`, `임시보관함`, `자동저장` 같은 기능이 생기면 그 시점부터는 `AES-256-GCM + Android Keystore`가 필수다.

## 현재 로컬 영속 저장 지점

### 1. 앱 SharedPreferences

- [PermissionGuidePreferences.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java)
- 저장 값:
  - 권한 안내 온보딩 완료 여부 `completed`

판단:

- 민감 정보가 아니다.
- AES 적용 대상이 아니다.

### 2. Firestore 로컬 캐시

- [ServiceLocator.java](../../app/src/main/java/com/example/bodeul/data/ServiceLocator.java)
- 현재 설정:
  - `MemoryCacheSettings`
  - 디스크 영속 캐시 사용 안 함

판단:

- 예약/리포트/문의/관리자 메모가 앱 재시작 후 디스크에 남지 않도록 이미 조정돼 있다.
- 현재 구조에서는 이 경로에 대한 별도 AES 적용 필요성이 낮다.

### 3. Firebase Auth 세션

- Android 앱:
  - Firebase Auth SDK가 로그인 세션을 자체 보관한다.
- 관리자 웹:
  - [firebase.ts](../../admin-web/firebase.ts)
  - [App.tsx](../../admin-web/src/App.tsx)
  - 별도 `localStorage` 인증 상태는 제거했고, Firebase Auth 세션을 사용한다.

판단:

- 이 값은 앱이 직접 평문 비즈니스 데이터를 저장하는 구조와 다르다.
- 앱 레벨 AES 대상이라기보다 `세션 정책`, `로그아웃`, `App Check`, `관리자 MFA` 대상이다.

### 4. 디버그 전용 임시 파일

- [AutomationEntryActivity.java](../../app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)
- `getCacheDir()` 아래에 자동 업로드 검증용 샘플 파일을 만든다.

판단:

- `debug` 전용이다.
- 운영 릴리스 보안 요구와 분리해서 본다.

## 현재 로컬 저장을 금지해야 하는 데이터

아래 데이터는 지금처럼 `서버 조회 + 메모리 사용`까지만 허용하고, 앱 디스크에 자동저장/오프라인 캐시/파일 다운로드 형태로 남기지 않는 것을 기본 원칙으로 잡는다.

### 1. 예약/건강 관련 텍스트

- 예약 요청 메모
- 환자 상태 요약
- 복약 요약

관련 코드:

- [FirebaseBookingRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)
- `specialNotes`
- `patientConditionSummary`
- `medicationSummary`

### 2. 세션 리포트

- 진료 요약
- 처치 메모
- 복약 메모

관련 코드:

- [FirebaseBookingRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)
- [FirebaseGuardianReportRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java)
- `summary`
- `treatmentNotes`
- `medicationNotes`

### 3. 관리자/운영 메모

- 서류 검토 메모
- 후속 처리 메모
- 문의 응답 메모
- 모니터링/정산 관련 운영 메모

관련 코드:

- [FirebaseAdminRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java)

### 4. 매니저 원본 서류

- 신분증
- 자격증
- 범죄경력 조회서

관련 코드:

- [FirebaseManagerDocumentStorageUploader.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java)

판단:

- 이 데이터는 로컬 AES로 보호할지 고민하기 전에, 우선 `로컬에 남기지 않는 것`이 맞다.
- 원본은 Storage에만 두고, 앱에서는 선택 즉시 업로드 후 종료하는 현재 흐름이 더 적절하다.

## AES 적용이 실제로 필요한 경우

아래 기능이 생기면 `AES-256-GCM + Android Keystore`를 필수 기준으로 본다.

### 1. 오프라인 저장

- 예약 상세를 기기에서 다시 열 수 있게 저장
- 보호자 리포트를 오프라인으로 보관
- 관리자 처리 메모를 임시저장

### 2. 파일 다운로드/임시보관

- 매니저 서류 원본을 기기에 내려받아 다시 열기
- PDF 리포트를 저장소나 다운로드 폴더에 보관

### 3. 로컬 초안 저장

- 예약 작성 중 건강 메모 자동저장
- 관리자 반려 메모 초안 저장

### 4. 제3자 검증 요구

- 대외 보안 심사
- 의료/개인정보 관련 계약 요구
- 단말 분실 시 데이터 노출 방지 통제 항목이 명시된 경우

## 적용 기준

### 지금 바로 AES를 넣을 대상

- 없음

조건:

- 현재 릴리스 경로에서 앱이 직접 영속 저장하는 민감 비즈니스 데이터가 거의 없기 때문

### 지금 바로 저장 금지 원칙을 고정할 대상

- 예약 건강 메모
- 세션 리포트 상세
- 관리자 운영 메모
- 매니저 원본 서류

### 다음 기능 추가 시 반드시 AES를 붙일 대상

- 오프라인 예약/리포트 저장
- 로컬 파일 다운로드 보관
- 자동저장 초안

## 권장 후속 순서

1. `App Check` 도입
2. `민감 데이터 로컬 저장 금지`를 문서 기준으로 고정
3. 다운로드/오프라인 기능이 생기면 `AES-256-GCM + Android Keystore` 적용
4. 그 뒤 필요하면 서버 측 필드 암호화 또는 CMEK 검토

## 한 줄 판단

- 현재 프로젝트는 `AES를 당장 전면 도입해야 하는 상태`가 아니라, `민감 데이터를 로컬에 남기지 않는 설계`를 유지해야 하는 상태다.
- AES는 `오프라인 저장/다운로드/자동저장`이 생기는 순간부터 필수다.
