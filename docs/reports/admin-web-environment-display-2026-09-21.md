# 관리자 웹 환경 표시 검증 기록

기준일: 2026-09-21

## 구현한 내용

- 웹 [PR #64](https://github.com/bodeul110/bodeul-admin-web/pull/64)를 squash merge했다. 병합 commit은 `f1ab197bb783fc5f3d8b4580c410b08919af5491`이다.
- 로그인·세션 확인·2차 인증·로그인 후 셸 상단에서 개발/운영 배포 환경을 색상과 한국어 이름으로 표시한다.
- Next.js와 Vite 빌드가 `VERCEL_ENV`를 공개 표시값으로 주입한다. 알 수 없는 배포는 `환경 확인 필요`로 표시한다.
- 별도 환경변수 수동 등록, Firebase·DB 권한 변경, 계정 추가나 데이터 쓰기는 이 기능에 포함하지 않았다.

## 판단 근거

| 항목 | 내용 |
| --- | --- |
| 작업 목적 | 개발 계정으로 운영 사이트에 로그인하거나 다른 환경에서 작업하는 혼동을 줄인다. |
| 선택한 방식 | 기존 인증 화면과 관리 셸에 공통 배너를 넣고 빌드의 배포 정보를 사용한다. |
| 대안 | URL·브랜치명 추정, `NODE_ENV` 판별, 수동 표시용 설정을 검토했다. |
| 선택 이유 | 현재 단일 Vercel 프로젝트의 Preview/Production 구분을 재사용하면 Preview 최적화 빌드를 운영으로 오인하거나 수동 설정이 어긋나는 일을 줄일 수 있다. |
| 리스크 | 표시값은 빌드 시 고정되며 실제 DB 연결 대상·가용성·관리자 권한을 검증하지 않는다. 별칭만 옮기는 승격 대신 대상 환경 재빌드가 필요하다. |

## 검증

### 자동 검사

기능 구현 시 아래 검사를 수행했다. 이번 Markdown 갱신에서 다시 실행한 것으로 기록하지 않는다.

| 검사 | 결과 |
| --- | --- |
| `npm run test` | 145개 통과 |
| `npm run lint` | 통과 |
| `npm run build` | Preview 표시값과 CI용 Firebase 설정으로 Next.js 빌드 통과 |
| `node scripts/check-built-admin-runtime.mjs` | 빌드 산출물의 인증 경계 9개 시나리오 통과 |
| `npm run build:vite` | 통과. 기존 vendor chunk 크기 경고 유지 |
| 병합 commit Build | [35584486792](https://github.com/bodeul110/bodeul-admin-web/actions/runs/35584486792) 성공 |
| 병합 commit CodeQL | [35584487011](https://github.com/bodeul110/bodeul-admin-web/actions/runs/35584487011) 성공 |

### 화면과 배포

| 대상 | 확인 내용 | 한계 |
| --- | --- | --- |
| 로컬 실제 컴포넌트 | 로그인·세션 확인·2차 인증·관리 셸, 1440px/320px 화면, 스크롤 중 상단 유지와 가로 넘침 없음 | DB·인증 서비스를 연결한 실사용자 세션이 아닌 fixture 검증 |
| 로컬 Next.js 최적화 빌드 | Preview의 개발 표시, 브라우저 오류·경고 없음 | 로컬 빌드 확인 |
| 실제 Vercel Preview | 로그인 화면의 개발 표시 | 실제 계정 로그인과 DB 업무를 재검증하지 않음 |
| 실제 Vercel Production | 기본 도메인 로그인 화면의 운영 표시, 데스크톱/390px 모바일 가로 넘침 없음, 브라우저 오류·경고 없음 | 실제 관리자 로그인·DB 조회 성공을 뜻하지 않음 |

- Preview: `dpl_Ex1LBmrGeMu1fSB5UW7BZVBY8wXD`, 기능 commit `9d1dd41b67bb94379d5cc67c80ca2f92dc78f4b3`, `READY`.
- Production: `dpl_7BQ82UQHr85SvbhCcnYpUUsfQtrw`, 병합 commit `f1ab197bb783fc5f3d8b4580c410b08919af5491`, `READY`, `hnd1`.
- 확인한 기본 주소: [관리자 웹](https://bodeul-admin-web-iota.vercel.app/). 이후 배포가 진행되면 최신 deployment ID는 달라질 수 있다.

## 남은 범위

운영 계정 등록과 웹 배포를 운영 서비스 개방으로 간주하지 않는다. 운영 DB 재개·접속 설정, 관리자 역할·MFA, 인증된 App Check 요청과 업무 흐름의 검증은 [관리자 웹 환경 기준](../operations/admin-web-environments.md)에 분리한다. 이 작업에서는 운영 DB를 재개하거나 변경하지 않았다.
