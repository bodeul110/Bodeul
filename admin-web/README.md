# 보들 관리자 웹

`admin-web`은 매니저 서류 심사와 관리자 세션 검증을 담당하는 Vite + React 기반 관리자 도구입니다. Android 앱과 같은 Firebase 프로젝트를 사용하지만, 배포와 운영 권한은 관리자 웹 전용 GitHub Environment로 분리합니다.

## 현재 기능

- Firebase Auth 기반 관리자 로그인
- `users/{uid}.role == ADMIN` 관리자 권한 확인
- 매니저 서류 심사 대상 목록 조회
- Firebase Storage 원본 파일 미리보기
- 매니저 서류 승인/반려 저장
- `VITE_BODEUL_DATA_BACKEND=api`일 때 병원 가이드 read API 검증
- 목록 기본 마스킹
- 15분 유휴 세션 자동 로그아웃

## 기술 스택

- React
- TypeScript
- Vite
- Tailwind CSS
- Firebase Authentication / Firestore / Storage
- 선택적 `bodeul-api` read API

## 환경 설정

Firebase Web config는 코드에 직접 넣지 않고 Vite 환경변수로 주입합니다. 로컬 실행 전 예시 파일을 복사해 값을 채웁니다.

```powershell
cd D:\BoDeul\admin-web
Copy-Item .env.example .env.local
```

필수 값:

| 이름 | 설명 |
| --- | --- |
| `VITE_FIREBASE_API_KEY` | Firebase Web API key |
| `VITE_FIREBASE_AUTH_DOMAIN` | Firebase Auth domain |
| `VITE_FIREBASE_PROJECT_ID` | Firebase project id |
| `VITE_FIREBASE_STORAGE_BUCKET` | Firebase Storage bucket |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | Firebase sender id |
| `VITE_FIREBASE_APP_ID` | Firebase Web app id |

선택 값:

| 이름 | 설명 |
| --- | --- |
| `VITE_FIREBASE_APPCHECK_SITE_KEY` | App Check reCAPTCHA v3 site key |
| `VITE_FIREBASE_APPCHECK_DEBUG_TOKEN` | 로컬/CI 검증용 App Check debug token |
| `VITE_BODEUL_DATA_BACKEND` | `firebase` 또는 `api`. 기본값은 `firebase`이며, `api`일 때 병원 가이드 검증 화면에서 `bodeul-api`를 호출한다. |
| `VITE_BODEUL_API_BASE_URL` | `bodeul-api` base URL. 예: `http://127.0.0.1:8080` |

`.env.local`은 로컬 전용 파일이며 커밋하지 않습니다. GitHub Actions에서는 `admin-web-preview` Environment의 variables/secrets를 사용합니다.

## bodeul-api 검증 모드

병원 가이드 read API 연결을 확인하려면 관리자 웹과 `bodeul-api`를 함께 실행합니다.

```powershell
$env:VITE_BODEUL_DATA_BACKEND = "api"
$env:VITE_BODEUL_API_BASE_URL = "http://127.0.0.1:8080"
npm --prefix admin-web run dev
```

API 서버는 관리자 웹 origin을 허용해야 합니다. 로컬 기본 origin인 `http://localhost:5173`, `http://127.0.0.1:5173`은 기본 허용값입니다. 운영/preview 도메인은 API 서버의 `BODEUL_API_ALLOWED_ORIGINS`에 별도로 추가합니다.

rollback은 `VITE_BODEUL_DATA_BACKEND=firebase`로 되돌리는 방식이다. 이때 병원 가이드 메뉴는 API를 호출하지 않고 기존 Firebase 기반 관리자 기능은 그대로 유지된다.

환경별 API 전환값과 CORS 기준은 [관리자 웹 API 환경변수와 CORS 기준](../docs/operations/admin-api-environments.md)을 따른다.

## 실행

```powershell
cd D:\BoDeul\admin-web
npm install
npm run dev
```

기본 주소:

- `http://localhost:5173`

관리자 테스트 계정과 비밀번호는 공개 문서에 적지 않고 팀의 비밀 관리 경로에서 확인합니다.

## 주요 파일

- [firebase.ts](firebase.ts): Firebase 설정과 서비스 초기화
- [src/bodeulApi.ts](src/bodeulApi.ts): `bodeul-api` 호출과 응답 검증
- [src/App.tsx](src/App.tsx): 인증 상태, 매니저 목록 구독, 저장 액션, 화면 전환 조합
- [src/components/AdminAuthScreen.tsx](src/components/AdminAuthScreen.tsx): 관리자 로그인 화면
- [src/components/AdminShell.tsx](src/components/AdminShell.tsx): 관리자 웹 공통 레이아웃
- [src/components/HospitalGuideApiPanel.tsx](src/components/HospitalGuideApiPanel.tsx): 병원 가이드 read API 검증 화면
- [src/components/ManagerApprovalList.tsx](src/components/ManagerApprovalList.tsx): 매니저 심사 목록
- [src/components/ManagerReviewModal.tsx](src/components/ManagerReviewModal.tsx): 서류 상세 심사 모달
- [src/hooks/useAdminIdleSession.ts](src/hooks/useAdminIdleSession.ts): 15분 유휴 세션 종료
- [src/hooks/useManagerDocumentPreviews.ts](src/hooks/useManagerDocumentPreviews.ts): Storage 원본 파일 미리보기 상태 관리
- [src/appCheck.ts](src/appCheck.ts): 선택적 App Check 초기화
- [src/vite-env.d.ts](src/vite-env.d.ts): Vite 환경변수 타입 선언
- [vite.config.ts](vite.config.ts): 빌드와 vendor chunk 분리 설정

## 검증

```powershell
npm run lint
npm run build
```

저장소 루트에서는 다음 명령을 사용합니다.

```powershell
npm --prefix admin-web run lint
npm --prefix admin-web run build
```

## Firebase Hosting 배포

관리자 웹 운영 배포는 Firebase Hosting을 기준으로 합니다. 현재 자동 배포 workflow는 켜지지 않았고, `Admin Web Build` workflow는 lint/build 검증과 산출물 업로드만 수행합니다.

수동 preview 배포:

```powershell
firebase hosting:channel:deploy admin-web-preview --project <firebase-project-id> --expires 7d
```

수동 live 배포:

```powershell
firebase deploy --only hosting --project <firebase-project-id>
```

Hosting 설정은 루트 [firebase.json](../firebase.json)의 `hosting` 블록을 기준으로 합니다. `admin-web/dist`만 배포하며, `/assets/**`는 Vite 해시 파일이므로 길게 캐시하고 나머지 HTML/SPA 경로는 배포가 바로 반영되도록 no-cache로 둡니다.

## 보안 메모

- 관리자 계정으로만 로그인해야 합니다.
- 목록 화면의 이메일과 전화번호는 기본 마스킹합니다.
- 상세 심사 모달에서만 원문 정보를 확인합니다.
- 15분 동안 활동이 없으면 자동 로그아웃합니다.
- Firebase Web API key는 서버 비밀값은 아니지만, 레포와 환경 분리를 위해 GitHub Environment secret으로 관리합니다.
- 관리자 권한 검증 절차는 [관리자 권한 QA 체크리스트](../docs/operations/admin-access-qa-checklist.md)를 따릅니다.

## 레포 분리 메모

- 관리자 웹이 사용하는 Firebase 계약은 [관리자 웹 데이터 계약](../docs/architecture/admin-web-data-contract.md)을 기준으로 확인합니다.
- 별도 레포 분리 준비는 [관리자 웹 레포 분리 준비 계획](../docs/operations/admin-web-repository-split.md)을 따릅니다.
- 관리자 웹 GitHub Environment 기준은 [관리자 웹 GitHub Environment 기준](../docs/operations/admin-web-environments.md)을 따릅니다.
