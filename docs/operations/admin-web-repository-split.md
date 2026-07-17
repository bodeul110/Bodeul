# 관리자 웹 저장소 분리 기록

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결정

관리자 웹의 source of truth를 [bodeul110/bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web)으로 이전했다. 메인 저장소의 중복 `admin-web/`, 관리자 전용 workflow와 Firebase Hosting 설정은 제거했다.

production 전환은 저장소 분리와 별개다. production Google Cloud/Firebase와 Supabase 기반은 생성했지만, 별도 저장소 master와 Vercel 기본 도메인은 현재 개발·공유 검증용이며 production 관리자 DB 자격 증명은 없다.

## 완료 근거

- `git subtree split`로 기존 관리자 웹 이력을 보존해 별도 저장소를 만들었다.
- 별도 저장소에 CODEOWNERS, PR 템플릿, Dependabot, build·CodeQL·Vercel CI를 구성했다.
- React UI를 Next.js App Router에 연결하고 Vite 빌드를 rollback 자산으로 유지했다.
- Vercel Preview에서 루트 200, API 401·403·200과 실제 개발 DB 조회를 확인했다.
- `bodeul_admin_service`를 Preview 전용으로 활성화하고 SELECT만 허용했다.
- Supabase Root CA를 명시해 TLS 인증서 검증을 유지했다.
- 메인 저장소 Node API의 관리자 계약이 Next.js로 대체됐음을 확인했다.
- 임시 Firebase 사용자와 DB 검증 row를 삭제하고 잔여 0건을 확인했다.
- 메인·관리자 저장소의 기존 `admin-web-preview`·`admin-web-production` GitHub Environment를 삭제하고 Vercel이 관리하는 `Preview`·`Production` deployment environment만 유지했다.
- 관리자 저장소 `master`에 PR, build·CodeQL·Vercel 체크, 대화 해결과 squash merge를 요구하는 ruleset을 적용했다.
- Vercel Functions를 Supabase와 같은 Tokyo `hnd1`로 고정하고 Preview 배포 산출물에서 확인했다.
- Firebase Hosting site를 비활성화해 기존 `bodeul-dev.web.app` 응답이 404인지 확인했다.
- 관리자 Hosting 전용 WIF provider, 배포 서비스 계정과 남은 Hosting IAM binding을 삭제했다.

## 소유권

| 범위 | 소유 저장소 |
| --- | --- |
| 관리자 React/Next.js 코드 | `bodeul-admin-web` |
| 관리자 Vite rollback | `bodeul-admin-web` |
| 관리자 Vercel 배포와 환경변수 | `bodeul-admin-web` |
| Android와 Spring Core API | `Bodeul` |
| PostgreSQL DDL/Flyway migration | `Bodeul/core-api` |
| Firestore·Storage Rules와 Functions | `Bodeul` |
| 공용 데이터 계약과 운영 기준 | `Bodeul/docs` |

Rules, schema 또는 공용 계약 변경은 양쪽 이슈/PR을 서로 링크한다. 관리자 웹 전용 UI 변경은 별도 저장소에서만 처리한다.

## 선택 이유

- 웹 담당자의 배포와 리뷰 주기를 Android/Core API와 분리할 수 있다.
- Vercel Preview와 웹 의존성 업데이트가 메인 Android CI를 불필요하게 실행하지 않는다.
- 관리자 서버 환경변수와 권한을 별도 저장소·배포 단위에 제한할 수 있다.
- 중복 소스를 제거해 어느 저장소가 기준인지 모호한 상태를 끝낸다.

## 리스크와 대응

| 리스크 | 대응 |
| --- | --- |
| 공용 계약 변경 누락 | 양쪽 이슈 링크와 계약 문서 갱신 |
| Rules 변경으로 관리자 화면 회귀 | 메인 PR에서 관리자 웹 영향 여부 확인 |
| production과 Preview 혼동 | 프로젝트 생성과 출시를 구분하고 Preview 전용 DB 자격 증명, Vercel production 미설정 유지 |
| Vite rollback 노후화 | 별도 저장소 CI에서 `build:vite`를 계속 검증 |
| DB schema 소유권 중복 | Flyway migration은 메인 `core-api/`만 소유 |

## 남은 production 준비

- [x] 별도 운영 Google Cloud/Firebase와 Supabase 프로젝트 생성
- [x] production 관리자 DB role 생성. Vercel 연결 전까지 `NOLOGIN` 유지
- [x] 비공개 pre-migration schema dump와 보관 정책 준비
- 기준 도메인 구매와 `admin.<기준-도메인>`·Auth authorized domain 연결
- App Check site key와 enforcement
- Vercel Production 관리자 DB 자격 증명 등록과 401·403·200 검증
- production backup/restore와 Vercel deployment rollback 리허설
- 결제 책임자, 실명 운영자 2명과 출시 일정 확정

Vercel은 기존 프로젝트의 Production 환경을 사용하고 보호된 `master` 병합을 live 배포 승인으로 간주한다. 리소스 이름, 리전과 배포 기본값은 [Production 인프라 기본값](production-infrastructure-defaults.md)에 확정했고, 실제 생성 결과는 [Production 인프라 구축 기록](../reports/production-infrastructure-bootstrap-2026-07-17.md)에 남겼다. 남은 출시 게이트와 사람 결정은 메인 이슈 #134에서 추적한다. 저장소 분리나 프로젝트 생성을 production 출시 완료로 해석하지 않는다.

## 관련 이슈

- [#135 관리자 웹 저장소 분리](https://github.com/bodeul110/Bodeul/issues/135)
- [#159 Node API 종료](https://github.com/bodeul110/Bodeul/issues/159)
- [#134 production 배포 기준](https://github.com/bodeul110/Bodeul/issues/134)
