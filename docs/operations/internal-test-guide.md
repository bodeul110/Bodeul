# 내부 테스트 가이드

기준일: 2026-09-21

개발 환경의 실제 연동 시험과 Mock 화면 데모를 구분한다. Notion의 앱 화면 피드백은 내부 테스트 결과이며, 구현 상태와 남은 항목은 [현재 구현 상태](../status/implementation-status.md)에서 확인한다.

## 시작 전 확인

| 항목 | 확인 기준 |
| --- | --- |
| 빌드 | commit SHA·앱 버전·설치 대상 기기 기록. Codex 터미널에서 빌드 가능, Android Studio 필수 아님 |
| 모드 | Firebase 설정이 없는 Mock인지, 개발 Firebase + Core API 연동인지 확인 |
| 앱 환경 | `bodeulCoreApiBaseUrl`과 Firebase 프로젝트가 개발 환경을 가리키는지 확인 |
| 웹 환경 | 별도 관리자 웹의 `개발 환경` 표시 확인. Production 표시는 운영 DB 연결 성공을 의미하지 않음 |
| 서버 | health와 인증된 예약·세션 요청 확인. Preview 500/503 재확인 항목 #429를 테스트 결과와 분리하지 말고 기록 |
| 데이터 | 이번 시험용 합성 예약·세션인지 확인. 과거 Firestore seed가 PostgreSQL 업무 데이터까지 준비하지는 않음 |
| 계정 | 환경별 계정을 비공개 전달. 공유 기본 비밀번호를 문서에 싣거나 실제 사용자 계정을 fixture로 사용하지 않음 |

Mock은 화면 개발용이다. Core API 오류를 Mock 성공이나 Firestore 쓰기로 대체하지 않는다. 운영 DB 재개·역할 부여·실제 결제·운영 fixture는 테스트 시작에 포함하지 않는다.

## 실행

저장소 루트에서 Android를 빌드한다. JDK 21, Android SDK와 개발용 로컬 설정을 먼저 준비한다.

```powershell
.\gradlew.bat assembleDebug --console=plain
```

관리자 웹은 메인 저장소의 과거 `admin-web/`가 아니라 별도 저장소에서 실행한다.

```powershell
cd D:\bodeul-admin-web
npm ci
npm run dev
```

기본 주소는 `http://localhost:3000`이다. 웹의 최신 준비 절차는 [관리자 저장소 README](https://github.com/bodeul110/bodeul-admin-web/blob/master/README.md), 환경 구분은 [관리자 웹 환경](admin-web-environments.md)을 따른다.

## 역할별 시나리오

| 역할 | 확인할 흐름 | 주의 |
| --- | --- | --- |
| 환자 | 브랜드 스플래시·홈, 예약 입력·병원 검색·건강 상태·날짜/시간·접수 완료, 예약·결제 상태 | 현재 예약 쓰기는 환자 본인만 허용. 실제 송금 시험 아님 |
| 보호자 | 본인 로그인, 동의 범위에 따른 상태·채팅·리포트 조회와 회수 후 거부 | 대리 예약은 제품 목표 #419이며 현재 쓰기 허용으로 간주하지 않음 |
| 매니저 | 자격 증빙 1종 제출, 배정된 동행, 가이드·자유 메모·종료 흐름 | 직접 수락·SOS·자동 119는 MVP 범위 밖. 위치 버튼이 서버 공유를 뜻하지 않음 |
| 운영 관리자 | 별도 웹의 역할별 목록·배정·서류 보호 미리보기·심사, 개발 결제 상태 처리 | ADMIN 외 활성 세부 역할 필요. 사유·감사·MFA·App Check를 우회하지 않음 |
| 개발 역할 | 비식별 진단 허용과 심사·결제 등 운영 기능 거부 | 개발자라는 이유로 원본 서류 접근을 허용하지 않음 |

최근 병합된 #430 현재 위치 보기와 #431 최종 자유 메모 수집은 내부 테스트 증상 재확인 대상이다. #422 가이드 시작 단계, #391 가이드 2 영상 플레이어, #420 내부 STT는 별도 잔여 작업으로 다룬다. 코드 병합을 실제 기기 재시험 완료로 기록하지 않는다.

Firebase 연동 Android의 기존 관리자 진입은 관리자 웹 안내로 중단되어야 한다. Mock 관리자 대시보드 시연과 실제 관리자 업무를 구분한다.

## 데이터와 읽기 전용 점검

```powershell
npm --prefix tools/firebase run check:state
npm --prefix tools/firebase run check:readiness
npm --prefix tools/firebase run check:manager-storage -- --strict
```

위 명령은 Firebase에 남은 문서·기존 seed·Storage를 검사한다. PostgreSQL 예약, Core API 가용성, 관리자 서버 권한을 모두 검증하는 명령이 아니다. 필요한 로컬 인증이 없으면 실패 원인만 기록하고 비밀값을 공개하지 않는다.

새 테스트 데이터가 필요하면 [Core API 가이드 fixture](core-api-guide-device-fixture.md), [Firebase 운영 도구](firebase/tools.md)의 대상 환경·dry-run·cleanup 절차를 따른다. 전체 reset/seed/apply를 빠른 시작 명령으로 실행하지 않는다. 기존 자료를 백업하고 명시적 승인을 받은 범위만 변경한다.

자격 증빙은 합성 JPEG/PNG/WebP 1종(`license` 또는 `nursingLicense`)을 사용한다. 신분증·범죄경력·건강 원본이나 실제 환자 음성을 테스트용으로 추가하지 않는다.

## 결과 기록

테스트 날짜, commit/빌드, 개발·운영·Mock 구분, 익명 역할, 시나리오, 기대/실제 결과와 재현 순서를 남긴다. 결과는 `통과`, `실패`, `미실행`으로 구분하고 캡처에서는 개인정보·토큰·기기 식별자를 가린다.

- 서버 오류와 앱 화면 오류를 구분한다.
- 합성 응답·fixture 검증은 실제 인증·DB·실기기 검증과 별도 표시한다.
- 테스트 데이터 cleanup 여부와 남은 재시험을 기록한다.
- 관리자 권한 시험은 [QA 체크리스트](admin-access-qa-checklist.md)를 함께 사용한다.
