# 관리자 웹 역할 설명

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

`admin-web`은 단순한 보조 화면이 아니라 서비스 신뢰성을 위한 운영 도구다. 매니저 서류 심사, 운영 상태 확인, 민감정보 마스킹, 관리자 세션 관리를 통해 앱 운영자가 사용자 신뢰와 안전을 관리할 수 있게 한다.

현재 구현의 배포 기준은 별도 `bodeul-admin-web` 저장소의 React + Next.js 애플리케이션이다. 기존 운영 화면은 Firebase를 계속 사용하고, 첫 서버 경계인 병원 가이드는 Vercel Route Handler가 Firebase ID token과 PostgreSQL 관리자 role을 확인한 뒤 Supabase PostgreSQL을 직접 읽도록 구현했다. Vite 빌드는 장애 시 rollback 경로로 남겨 뒀다.

관리자 서버가 별도 Node API나 Spring Core API를 다시 호출한 뒤 DB로 가는 중복 경로는 만들지 않는다. Vercel Preview의 루트 200과 인증 없는 API 401은 확인했지만, 개발 DB 관리자 접속은 아직 `NOLOGIN`이라 실제 ADMIN 200과 비관리자 403은 남아 있다. 상세 기준은 [목표 인프라 구조](target-infrastructure.md)를 따른다.

## 작업 목적

관리자 웹이 왜 필요한지, Android 앱 안의 관리자 화면과 어떤 역할 차이가 있는지, 현재 구현과 목표 배포 구조가 어떻게 다른지 설명한다.

## 현재 구현

- React UI를 Next.js App Router에서 실행하고 Vercel Preview를 기본 검증 경로로 사용한다.
- 브라우저의 Firebase Auth 로그인과 기존 `users/{uid}.role == ADMIN` 화면 진입 검증은 유지한다.
- Firestore에서 매니저 계정과 운영 상태를 읽고, Storage에서 매니저 서류 원본을 확인한다.
- `GET /admin/hospital-guides`는 같은 origin의 Next.js 서버에서 ID token과 PostgreSQL `app_users.role`을 확인한다.
- 서버의 PostgreSQL pool은 1개 연결로 제한하고 transaction pooler 호환 방식으로 조회한다.
- 민감정보는 표시 범위를 제한하고, 관리자 유휴 세션 종료를 둔다.
- Vite/Firebase Hosting 빌드는 rollback용으로 유지한다. production 도메인과 승인 기준은 아직 확정하지 않았다.

## 남은 전환

- 개발 DB의 `bodeul_admin_service`를 별도 자격 증명으로 활성화하고 Vercel Preview에 서버 전용 `ADMIN_DATABASE_URL`을 설정한다.
- 실제 ADMIN token의 200과 비관리자 token의 403을 확인하고, 병원 가이드 결과를 기존 경로와 비교한다.
- 이후 관리자 조회 도메인을 화면별로 이전하고 Firestore 직접 접근 범위를 줄인다.
- Vite rollback과 Node `api/` 제거는 실제 비교와 rollback 판단이 끝난 뒤 같은 변경에서 처리한다.
- production 도메인, Auth authorized domain, App Check, 배포 승인 기준은 메인 이슈 #134에서 확정한다.

## 주요 역할

- 매니저 서류 심사와 보완 메모 기록
- 신고/문의/운영 상태 확인
- 매니저 서류 원본 미리보기
- 관리자 세션 확인과 비관리자 접근 차단
- 민감정보 마스킹과 운영 화면 분리

## 대안

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| Android 앱 안의 관리자 화면만 사용 | 앱 코드만 유지하면 된다. | 운영자는 데스크톱에서 심사와 비교 작업을 하는 편이 효율적이다. |
| 별도 Spring 관리자 서버 | 권한과 감사 로그를 한 서버에 모을 수 있다. | 사용자 Core API와 관리자 경계를 다시 묶고 별도 서비스 운영 부담이 생긴다. |
| Firebase Console 직접 운영 | 별도 개발이 필요 없다. | 비개발자가 쓰기 어렵고, 실수로 원본 데이터를 수정할 위험이 크다. |

## 선택 이유

- 매니저 서류 심사는 파일 미리보기, 상태 비교, 보완 메모가 필요해 웹 화면이 효율적이다.
- Firebase Auth를 유지하면서 민감한 DB 자격 증명과 role 검증을 Vercel 서버 안으로 옮길 수 있다.
- 같은 origin Route Handler를 사용해 별도 Node/Spring proxy와 CORS 경계를 추가하지 않는다.
- Vite rollback을 함께 빌드하므로 첫 서버 전환의 실패 범위를 병원 가이드 화면으로 제한할 수 있다.
- 운영 도구가 있으면 멘토에게 “서비스 신뢰성을 어떻게 관리하는지”를 설명하기 좋다.

## 리스크

- 기존 화면은 Firestore `users.role`, 새 서버 경계는 PostgreSQL `app_users.role`을 사용하므로 전환 중 role 동기화가 필요하다.
- 관리자 DB 접속 role과 Vercel 환경변수가 아직 활성화되지 않아 인증된 조회는 검증 전이다.
- App Check는 site key 준비는 가능하지만 enforcement는 아직 운영 전환 조건이 남아 있다.
- 남은 Firebase 직접 접근 화면은 Rules와 관리자 세션 검증이 계속 맞아야 한다.
- 운영자가 늘면 감사 로그, 권한 변경 이력, MFA를 더 엄격히 다뤄야 한다.

## 보완 계획

- 관리자 웹이 사용하는 Firebase 계약은 [관리자 웹 데이터 계약](admin-web-data-contract.md)을 기준으로 추적한다.
- 관리자 권한 검증은 [Firestore/Storage Rules 검증 정리](../security/firebase-rules-validation.md)와 함께 유지한다.
- 운영 배포 결과는 [관리자 웹 Firebase Hosting live 배포 판단 보고서](../reports/admin-web-live-deploy-2026-06-25.md)에 기록한다.
- App Check, custom domain, GitHub Actions 자동 배포는 [인프라 리스크/보완 계획](infra-risk-review.md)에서 추적한다.
- 레포 분리 판단은 [관리자 웹 레포 분리 준비 계획](../operations/admin-web-repository-split.md)을 기준으로 한다.
