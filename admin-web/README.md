# 보들 관리자 웹

`admin-web`은 매니저 서류 승인과 관리자 세션 검증을 담당하는 React/Vite 기반 관리자 웹이다.

## 현재 기능

- Firebase Auth 기준 관리자 로그인
- `users/{uid}.role == ADMIN` 검증
- 매니저 서류 요약 조회
- Firebase Storage 원본 파일 미리보기
- 승인 / 반려 저장
- 목록 기본 마스킹
- 15분 유휴 세션 자동 로그아웃

## 기술 스택

- React
- TypeScript
- Vite
- Tailwind CSS
- Firebase Authentication / Firestore / Storage

## 실행

```powershell
cd D:\BoDeul\admin-web
npm install
npm run dev
```

기본 주소:

- `http://localhost:5173`

## 확인용 계정

- 관리자: `admin@bodeul.app` / `bodeul1234`

## 주요 파일

- [firebase.ts](firebase.ts)
  - Firebase 앱과 서비스 초기화
- [src/App.tsx](src/App.tsx)
  - 인증 상태, 매니저 목록 구독, 저장 액션, 화면 전환 조합
- [src/components/AdminAuthScreen.tsx](src/components/AdminAuthScreen.tsx)
  - 관리자 로그인 화면
- [src/components/AdminShell.tsx](src/components/AdminShell.tsx)
  - 관리자 웹 공통 셸
- [src/components/ManagerApprovalList.tsx](src/components/ManagerApprovalList.tsx)
  - 매니저 승인 목록
- [src/components/ManagerReviewModal.tsx](src/components/ManagerReviewModal.tsx)
  - 서류 상세 심사 모달
- [src/hooks/useAdminIdleSession.ts](src/hooks/useAdminIdleSession.ts)
  - 15분 유휴 세션 종료
- [src/hooks/useManagerDocumentPreviews.ts](src/hooks/useManagerDocumentPreviews.ts)
  - Storage 원본 파일 미리보기 상태 관리
- [src/appCheck.ts](src/appCheck.ts)
  - 선택적 App Check 초기화
- [vite.config.ts](vite.config.ts)
  - 빌드와 vendor chunk 분리 설정

## 환경 변수

실제 App Check를 웹에 붙일 때만 아래 값을 사용한다.

```env
VITE_FIREBASE_APPCHECK_SITE_KEY=...
VITE_FIREBASE_APPCHECK_DEBUG_TOKEN=...
```

사이트 키가 없으면 관리자 웹은 App Check 초기화를 건너뛴다.

## 검증

```powershell
npm run lint
npm run build
```

## 보안 메모

- 관리자 계정으로만 로그인해야 한다.
- 목록 화면의 이메일/전화번호는 기본 마스킹된다.
- 상세 심사 모달에서만 원문을 확인한다.
- 15분 동안 활동이 없으면 자동 로그아웃된다.
- 세부 검증 절차는 [관리자 권한 QA 체크리스트](../docs/operations/admin-access-qa-checklist.md)를 따른다.
