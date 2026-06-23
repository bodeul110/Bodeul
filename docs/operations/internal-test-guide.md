# 내부 테스트 가이드

기준일: 2026-05-05

이 문서는 기획/내부 QA가 `bodeul-dev` 기준선 데이터로 주요 화면과 시나리오를 빠르게 확인할 수 있도록 정리한 가이드다.  
개발자 전용 재시드/점검 명령은 아래 별도 구간에 분리했다.

## 1. 테스트 전제

- Android 앱은 `bodeul-dev` Firebase 프로젝트를 기준으로 동작한다.
- 관리자 웹은 로컬에서 `admin-web` 개발 서버를 띄워 사용한다.
- 기본 기준선 계정과 샘플 예약/세션/서류 데이터가 이미 들어가 있다는 전제로 테스트한다.
- 데이터가 꼬였거나 초기화가 필요하면 개발자가 `tools/firebase` 스크립트로 다시 넣는다.

## 2. 테스트용 계정

### 공통 계정

- 관리자: `admin@bodeul.app` / `bodeul1234`
- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

### 계정별 권장 용도

- 관리자
  - 앱 관리자 대시보드 확인
  - 관리자 웹 서류 심사 확인
- 매니저
  - 홈, 가이드, 과거 이력, 문의, 내 페이지 확인
  - 원본 서류 업로드/상태 확인
- 환자
  - 홈, 예약 생성, 완료 후 후속 처리 확인
- 보호자
  - 홈, 예약 상태, 보호자 리포트 확인

## 3. 기본 더미 데이터

### 예약/세션 시나리오

- `request-seed-requested`
  - 시나리오 이름: `예약 대기`
  - 의미: 보호자가 등록한 향후 예약 요청
  - 확인 포인트: 예약 상세, 대기 상태, 관리자/매니저 배정 전 흐름

- `request-seed-progress`
  - 시나리오 이름: `진행 중 동행`
  - 의미: 매니저가 배정되어 병원 대기 중인 동행 세션
  - 확인 포인트: 진행 상태, 매니저 홈, 보호자 상태 조회

- `request-seed-completed`
  - 시나리오 이름: `종료 후속 처리`
  - 의미: 리포트, 후기, 정산 재확인, SOS 후속이 남아 있는 완료 요청
  - 확인 포인트: 보호자 리포트, 환자 후기/후속, 관리자 후속 처리

### 매니저 서류 시나리오

- `manager@bodeul.app` 계정에는 원본 서류 3종이 준비돼 있다.
  - 신분증
  - 자격증
  - 범죄경력 조회서
- 관리자 웹에서 서류 미리보기와 승인/반려 흐름을 바로 확인할 수 있다.

## 4. 기획/내부 QA 빠른 시작

### Android 앱

1. 최신 테스트 빌드를 설치한다.
2. 역할별 계정으로 로그인한다.
3. 아래 권장 시나리오 순서대로 확인한다.

### 관리자 앱 진입

- 관리자 로그인은 공개 카드가 없다.
- 역할 선택 화면 상단 로고를 `1.5초 안에 5회 탭`하면 관리자 로그인 화면으로 들어간다.
- 관리자 로그인은 이메일 로그인만 허용한다.

### 관리자 웹

```powershell
cd D:\BoDeul\admin-web
npm run dev
```

- 접속 주소: `http://localhost:5173`
- 로그인 계정: `admin@bodeul.app` / `bodeul1234`

## 5. 권장 테스트 순서

### 5-1. 환자

1. `patient@bodeul.app` 로그인
2. 홈 진입 확인
3. 예약 작성 화면 진입 확인
4. `request-seed-completed` 기준 후속 처리 화면 확인

### 5-2. 보호자

1. `guardian@bodeul.app` 로그인
2. 홈 진입 확인
3. `request-seed-requested` 예약 대기 상태 확인
4. `request-seed-progress` 진행 상태 확인
5. `request-seed-completed` 보호자 리포트 확인

### 5-3. 매니저

1. `manager@bodeul.app` 로그인
2. 홈, 가이드, 과거 이력 진입 확인
3. 내 페이지에서 원본 서류 상태 카드 확인
4. 필요하면 원본 파일 업로드 동작 확인

### 5-4. 관리자 앱

1. 역할 선택 화면에서 숨김 진입
2. `admin@bodeul.app` 로그인
3. 관리자 대시보드, 후속 처리, 운영 항목 진입 확인

### 5-5. 관리자 웹

1. `http://localhost:5173` 접속
2. `admin@bodeul.app` 로그인
3. 매니저 목록, 상태 요약 카드 확인
4. `manager@bodeul.app` 상세 열기
5. 원본 서류 미리보기, 승인/반려, 검토 메모 저장 확인

## 6. 개발자 전용 기준선 재설정

기획/내부 QA가 직접 실행할 필요는 없다.  
테스트 데이터가 꼬였을 때 개발자가 아래 순서로 다시 맞춘다.

```powershell
cd D:\BoDeul\tools\firebase
npm run reset:baseline:apply
npm run seed:sample:apply
npm run seed:manager-docs:apply
```

### 주의

- 위 명령은 Firebase 운영 권한이 필요하다.
- `firebase login:ci` 기반 refresh token을 쓰는 환경이면 아래 값도 필요하다.
  - `FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수
  - 또는 `local.properties`의 `firebaseOauthClientSecret`

## 7. 개발자 전용 점검 명령

```powershell
cd D:\BoDeul\tools\firebase
npm run check:state
npm run check:readiness
npm run check:manager-storage -- --strict
```

설명:

- `check:state`
  - Firestore 기준선 상태와 컬렉션 건수 확인
- `check:readiness`
  - 역할별 화면 진입 준비도와 샘플 시나리오 준비도 확인
- `check:manager-storage -- --strict`
  - 매니저 서류 메타데이터와 실제 Storage 객체 일치 여부 확인

주의:

- 이 세 명령도 Firebase 운영 권한과 `firebaseOauthClientSecret` 설정이 필요할 수 있다.
- 설정이 없으면 `Firebase OAuth client secret을 찾지 못했습니다` 오류로 종료된다.

## 8. 테스트 결과 기록 권장 형식

기획/내부 QA는 아래 항목만 간단히 남기면 된다.

- 테스트 날짜
- 앱 버전 또는 커밋 기준
- 테스트 계정
- 확인한 시나리오
- 결과
  - 통과
  - 확인 필요
  - 실패
- 이슈 메모
- 스크린샷 또는 화면 녹화 경로

## 9. 관련 문서

- [현재 구현 상태](../status/implementation-status.md)
- [문서 안내](../README.md)
- [Firebase 설정](firebase/setup.md)
- [Firebase 운영 도구](firebase/tools.md)
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
