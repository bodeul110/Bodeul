# 관리자 웹/앱 권한 QA 체크리스트

기준일: 2026-05-05

이 문서는 `관리자 앱 진입`, `관리자 웹 로그인`, `매니저 서류 검토`, `권한 실패 시나리오`를 반복 검증하기 위한 체크리스트다.

## 목적

- 일반 사용자 경로에서 관리자 기능이 노출되지 않는지 확인
- 관리자 계정만 앱/웹에서 관리자 기능에 접근하는지 확인
- 매니저 서류 원본 업로드와 관리자 웹 미리보기가 같은 Firebase 경로를 읽는지 확인
- Firestore / Storage 권한 축소 이후 주요 화면이 깨지지 않는지 확인

## 사전 준비

### 계정

- 관리자: `admin@bodeul.app` / `bodeul1234`
- 매니저: `manager@bodeul.app` / `bodeul1234`
- 환자: `patient@bodeul.app` / `bodeul1234`
- 보호자: `guardian@bodeul.app` / `bodeul1234`

### 로컬 점검

```powershell
cd D:\BoDeul
.\gradlew.bat assembleDebug
cd D:\BoDeul\tools\firebase
npm run check:state
npm run check:readiness
npm run check:manager-storage -- --strict
```

### 웹 실행

```powershell
cd D:\BoDeul\admin-web
npm run dev
```

- 기본 주소: `http://localhost:5173`

## 1. 앱 관리자 진입

### 1-1. 공개 경로 노출 확인

1. 앱을 실행한다.
2. 역할 선택 화면을 확인한다.

기대 결과:

- 공개 카드에는 `환자`, `보호자`, `매니저`만 보인다.
- `관리자` 카드가 직접 노출되지 않는다.

### 1-2. 숨김 관리자 진입 확인

1. 역할 선택 화면 상단 로고를 `1.5초 안에 5회 탭`한다.
2. 관리자 로그인 화면으로 이동하는지 확인한다.

기대 결과:

- 관리자 로그인 화면으로 진입한다.
- 관리자 로그인 화면에서는 이메일 로그인만 가능하다.
- 회원가입, 소셜 로그인 진입은 보이지 않는다.

### 1-3. 일반 경로 오입력 차단 확인

1. 공개 `환자`, `보호자`, `매니저` 경로 중 하나로 로그인 화면에 들어간다.
2. `admin@bodeul.app / bodeul1234`를 입력한다.

기대 결과:

- `선택한 사용자 유형과 계정 유형이 일치하지 않습니다.` 류의 실패 메시지가 나온다.
- 관리자 화면으로 진입하지 않는다.

### 1-4. 관리자 로그인 성공 확인

1. 숨김 관리자 진입 경로로 로그인 화면에 들어간다.
2. `admin@bodeul.app / bodeul1234`로 로그인한다.

기대 결과:

- 관리자 대시보드로 진입한다.
- 일반 사용자 홈이나 예약 화면으로 가지 않는다.

## 2. 관리자 웹 로그인

### 2-1. 관리자 로그인 성공

1. `http://localhost:5173` 접속
2. `admin@bodeul.app / bodeul1234` 로그인

기대 결과:

- 관리자 웹 메인 화면이 열린다.
- 매니저 심사 목록, 상태 배지, 로그아웃 버튼이 보인다.

### 2-2. 비관리자 로그인 차단

1. 로그아웃한다.
2. `manager@bodeul.app / bodeul1234` 또는 `patient@bodeul.app / bodeul1234`로 로그인한다.

기대 결과:

- 관리자 화면이 열리지 않는다.
- 비관리자 계정은 즉시 차단되거나 로그인 화면으로 되돌아간다.

### 2-3. 로그아웃 동작 확인

1. 관리자 웹 로그인 상태에서 로그아웃을 누른다.
2. 새로고침한다.

기대 결과:

- Firebase 세션이 종료되어 다시 로그인 화면이 보인다.
- `localStorage` 플래그만으로 관리자 화면이 다시 열리지 않는다.

### 2-4. 목록 기본 마스킹 확인

1. 관리자 웹 로그인 상태에서 매니저 승인 목록을 본다.
2. 이메일과 전화번호 열을 확인한다.

기대 결과:

- 목록에서는 이메일과 전화번호가 마스킹되어 보인다.
- 상세 보기 모달을 열기 전까지는 전체 값이 그대로 노출되지 않는다.

### 2-5. 유휴 세션 자동 종료 확인

1. 관리자 웹 로그인 상태로 둔다.
2. 입력, 클릭, 스크롤 없이 `15분` 이상 둔다.

기대 결과:

- 자동 로그아웃된다.
- 로그인 화면으로 돌아간다.
- `보안을 위해 15분 동안 활동이 없어 자동 로그아웃했습니다.` 안내가 보인다.

## 3. 매니저 서류 검토

### 3-1. 매니저 서류 미리보기 확인

1. 관리자 웹에서 `manager@bodeul.app` 항목을 연다.
2. `신분증`, `자격증`, `범죄경력 조회서` 미리보기를 확인한다.

기대 결과:

- 현재 업로드된 원본 파일 기준으로 미리보기가 열린다.
- 메타데이터 경로가 끊긴 경우 다른 파일로 자동 대체하지 않고 오류 상태를 보여준다.

### 3-2. 심사 승인/반려 저장 확인

1. 매니저 심사 항목을 승인한다.
2. 다시 반려 메모를 넣고 반려한다.

기대 결과:

- 심사 상태가 Firestore에 반영된다.
- `managerDocumentStatus`, `managerDocumentReviewNote`, `managerDocumentReviewedAt`, `managerDocumentReviewedByName`, `managerDocumentHistory`가 함께 갱신된다.

## 4. 앱-웹 연동 확인

### 4-1. 매니저 앱 업로드 후 웹 반영 확인

1. 매니저 앱 내 페이지에서 원본 파일을 업로드한다.
2. 관리자 웹에서 같은 매니저 항목을 다시 연다.

기대 결과:

- `managerDocumentFiles` 메타데이터와 Storage 경로가 함께 저장된다.
- 관리자 웹 미리보기가 새 파일 기준으로 갱신된다.

### 4-2. Storage 점검 확인

```powershell
cd D:\BoDeul\tools\firebase
npm run check:manager-storage -- --strict
```

기대 결과:

- `missingObjectCount = 0`
- `pathMismatchCount = 0`
- `orphanObjectCount`는 0이거나, 정리 전이면 리포트로만 남는다.

## 5. 권한 실패 시나리오

### 5-1. Firestore 직접 읽기 차단

검증 기준:

- 일반 사용자(`patient`, `guardian`, `manager`)는 다른 사용자의 `users/{uid}` 문서를 직접 읽지 못해야 한다.
- 본인 `users/{uid}`는 읽을 수 있어야 한다.
- 관리자만 타 사용자 `users/{uid}` 문서를 읽을 수 있어야 한다.

이미 반영된 자동 검증:

- [firestore-security-hardening.md](/D:/BoDeul/docs/firestore-security-hardening.md)

### 5-2. Storage 정리 전 안전장치 확인

```powershell
cd D:\BoDeul\tools\firebase
npm run cleanup:manager-storage:dry-run
```

기대 결과:

- `--apply` 없이 실제 삭제가 일어나지 않는다.
- 누락 객체나 경로 불일치가 있으면 삭제 차단 사유가 출력된다.

## 6. 통과 기준

아래를 모두 만족하면 관리자 권한 QA를 통과로 본다.

- 앱 역할 선택 화면에 관리자 공개 카드가 없다.
- 숨김 진입으로만 관리자 앱 로그인 가능
- 일반 경로에서 관리자 계정 오입력 시 차단
- 관리자 앱 로그인 성공
- 관리자 웹에서 관리자 계정만 로그인 성공
- 관리자 웹 로그아웃 후 새로고침 시 세션 유지되지 않음
- 관리자 웹 목록 기본 마스킹 적용 확인
- 관리자 웹 유휴 세션 자동 종료 확인
- 매니저 서류 미리보기 정상
- 심사 승인/반려 저장 정상
- `check:manager-storage -- --strict` 통과
- 타 사용자 `users/{uid}` 직접 읽기 차단 확인

## 7. 실패 시 기록 항목

실패하면 아래를 같이 남긴다.

- 실패 화면 또는 URL
- 사용 계정
- 실패 단계 번호
- 기대 결과와 실제 결과
- 관련 리포트 경로
  - `tools/firebase/reports/manager-document-storage-check-*.json`
  - `tools/firebase/reports/firestore-operations-report-*.html`
  - `tools/firebase/reports/local-preflight-summary-*.md`
