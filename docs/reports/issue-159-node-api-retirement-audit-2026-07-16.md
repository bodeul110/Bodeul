# Issue 159 Node API 종료 판단 기록

기준일: 2026-07-16

## 작업 목적

기존 `api/` Node 서버를 바로 삭제해도 되는지, Spring Core API와 별도 관리자 웹 저장소가 필요한 계약을 모두 대체했는지 현재 코드와 GitHub 설정으로 확인한다.

## 확인 결과

| 항목 | 현재 상태 | 판단 |
| --- | --- | --- |
| 공개 상태 확인 | Node는 `/healthz`, Spring은 `/health`를 제공 | Spring 사용자 서비스의 배포 확인 계약은 대체 완료 |
| Firebase ID token 검증 | 두 서버에 구현 | Spring은 Cloud Run에서 실제 token 시나리오까지 검증 완료 |
| PostgreSQL 역할 인가 | Node는 `ADMIN`, Spring은 사용자 역할을 검증 | 공통 원칙은 같지만 소유 역할이 다르므로 서버별 유지 |
| Kakao Local REST | Spring `GET /api/places/search`로 이관 | Android 직접 호출 제거 완료 |
| 관리자 병원 가이드 조회 | Node `GET /admin/hospital-guides`만 구현 | Next.js 관리자 서버가 아직 대체하지 않음 |
| 관리자 웹 참조 | `bodeul-admin-web`의 `VITE_BODEUL_API_BASE_URL`, `src/bodeulApi.ts`가 Node 계약을 참조 | 현재 `api/` 삭제 불가 |
| Oracle 배포 | 과거 preview 검증 기록만 있고 현재 배포 workflow 없음 | 신규 배포 대상에서 제외 |
| Oracle GitHub secret | workflow 참조가 없는 `OCI_CLI_*` 저장소 secret 6개가 남아 있었음 | 2026-07-16 삭제 완료 |

## 선택한 방식

- `api/`는 전환 과정의 계약과 회귀 비교를 위한 동결된 프로토타입으로 보존한다.
- 신규 운영 도메인과 production 배포 기능은 Node에 추가하지 않는다.
- 보안 수정, 의존성 취약점 조치와 기존 계약 회귀 수정만 허용한다.
- `.github/workflows/api.yml`은 소스가 남아 있는 동안 typecheck, build, test 검증용으로 유지한다.
- 사용자 기능은 Spring Core API로, 관리자 기능은 목표 Next.js 관리자 서버로 각각 이관하며 두 서버를 서로의 proxy로 연결하지 않는다.

## 검토한 대안

| 대안 | 장점 | 제외 이유 |
| --- | --- | --- |
| 지금 `api/` 삭제 | 유지 대상이 줄어듦 | 관리자 웹의 병원 가이드 API 검증 경로가 즉시 깨짐 |
| 관리자 기능도 Spring으로 이관 | 서버가 하나로 줄어듦 | 관리자 서버와 사용자 서버를 분리해 같은 DB를 보게 한다는 목표 경계와 다름 |
| Node를 production 서버로 계속 확장 | 기존 코드를 재사용함 | Spring과 Next.js 목표 경계가 다시 중복되고 Oracle 의존성도 되살아남 |

## 실제 종료 조건

1. `bodeul-admin-web`의 Next.js 서버가 Firebase ID token과 PostgreSQL `ADMIN` 역할을 검증한다.
2. Next.js 서버가 관리자 전용 DB role로 병원 가이드 조회를 직접 제공한다.
3. 관리자 웹에서 `VITE_BODEUL_DATA_BACKEND`, `VITE_BODEUL_API_BASE_URL`, `src/bodeulApi.ts` 참조가 제거된다.
4. Vercel preview에서 인증된 병원 가이드 조회와 Firebase/기존 결과 비교가 통과한다.
5. Node 전용 배포 환경변수와 secret이 없고, 과거 검증 기록과 마지막 commit을 rollback 근거로 남긴다.
6. 위 결과를 확인한 PR에서 `api/`와 `.github/workflows/api.yml`을 함께 제거한다.

## 리스크

- 동결 상태를 명시하지 않으면 신규 기능이 Node와 목표 서버에 중복 구현될 수 있다.
- 관리자 웹 Next.js 전환 전에 Node를 삭제하면 현재 유일한 PostgreSQL 관리자 read 계약을 잃는다.
- 소스를 보존하는 동안 의존성 취약점은 계속 점검해야 한다.

## 결론

현재 정답은 즉시 삭제가 아니라 **운영 후보 제외와 기능 동결**이다. Oracle 자격 증명은 제거했지만 관리자 병원 가이드 계약이 아직 대체되지 않았으므로 #159는 열린 상태로 유지하고, 관리자 Next.js 서버 전환 뒤 실제 삭제를 진행한다.
