# 관리자 웹 역할 설명

기준일: 2026-07-12

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

`admin-web`은 단순한 보조 화면이 아니라 서비스 신뢰성을 위한 운영 도구다. 매니저 서류 심사, 운영 상태 확인, 민감정보 마스킹, 관리자 세션 관리를 통해 앱 운영자가 사용자 신뢰와 안전을 관리할 수 있게 한다.

현재 구현은 React + Vite 정적 SPA이고 Firebase를 기본 데이터 경로로 사용한다. 목표 구현은 `bodeul-admin-web`을 Next.js로 단계 이전해 Vercel의 관리자 서버가 Firebase ID token을 검증하고 Supabase PostgreSQL에 서버 측에서 직접 접근하는 구조다.

관리자 서버가 별도 Node API나 Spring Core API를 다시 호출한 뒤 DB로 가는 중복 경로는 만들지 않는다. 목표 구조의 상세 기준은 [목표 인프라 구조](target-infrastructure.md)를 따른다.

## 작업 목적

관리자 웹이 왜 필요한지, Android 앱 안의 관리자 화면과 어떤 역할 차이가 있는지, 현재 구현과 목표 배포 구조가 어떻게 다른지 설명한다.

## 현재 구현

- React + Vite 기반 정적 SPA로 구현한다.
- Firebase Auth 로그인 후 `users/{uid}.role == ADMIN`인 계정만 운영 화면에 진입시킨다.
- Firestore에서 매니저 계정과 운영 상태를 읽고, Storage에서 매니저 서류 원본을 확인한다.
- 민감정보는 표시 범위를 제한하고, 관리자 유휴 세션 종료를 둔다.
- Vercel preview를 팀 공유 배포 경로로 사용한다. production 기준은 아직 확정하지 않았다.

## 목표 구현

- React UI를 유지하면서 Next.js로 단계 이전한다.
- Vercel의 Next.js 서버 코드에서 Firebase ID token과 PostgreSQL 관리자 role을 검증한다.
- 관리자 서버는 Supabase PostgreSQL의 관리자 runtime role만 사용한다.
- DB connection string과 privileged key는 브라우저 번들에 넣지 않는다.
- Vercel serverless 연결은 Supabase transaction pooler 사용을 기준으로 검증한다.
- 기존 Vite/Firebase 경로는 화면별 전환과 rollback이 끝날 때까지 유지한다.

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
| 별도 서버 기반 백오피스 | 권한과 감사 로그를 서버에서 강하게 통제할 수 있다. | 현재 규모에서는 백오피스 서버 운영 부담이 크다. |
| Firebase Console 직접 운영 | 별도 개발이 필요 없다. | 비개발자가 쓰기 어렵고, 실수로 원본 데이터를 수정할 위험이 크다. |

## 선택 이유

- 매니저 서류 심사는 파일 미리보기, 상태 비교, 보완 메모가 필요해 웹 화면이 효율적이다.
- Firebase Auth와 `users.role` 기준을 Android 앱과 공유하므로 권한 설명이 단순하다.
- Firebase Hosting은 Vite 정적 산출물 배포에 맞고, preview/live 검증 흐름을 빠르게 만들 수 있다.
- 운영 도구가 있으면 멘토에게 “서비스 신뢰성을 어떻게 관리하는지”를 설명하기 좋다.

## 리스크

- 현재 관리자 권한은 custom claims가 아니라 `users/{uid}.role` 문서 필드 기준이다.
- App Check는 site key 준비는 가능하지만 enforcement는 아직 운영 전환 조건이 남아 있다.
- 관리자 화면이 Firestore를 직접 읽으므로 Rules와 관리자 세션 검증이 함께 맞아야 한다.
- 운영자가 늘면 감사 로그, 권한 변경 이력, MFA를 더 엄격히 다뤄야 한다.

## 보완 계획

- 관리자 웹이 사용하는 Firebase 계약은 [관리자 웹 데이터 계약](admin-web-data-contract.md)을 기준으로 추적한다.
- 관리자 권한 검증은 [Firestore/Storage Rules 검증 정리](../security/firebase-rules-validation.md)와 함께 유지한다.
- 운영 배포 결과는 [관리자 웹 Firebase Hosting live 배포 판단 보고서](../reports/admin-web-live-deploy-2026-06-25.md)에 기록한다.
- App Check, custom domain, GitHub Actions 자동 배포는 [인프라 리스크/보완 계획](infra-risk-review.md)에서 추적한다.
- 레포 분리 판단은 [관리자 웹 레포 분리 준비 계획](../operations/admin-web-repository-split.md)을 기준으로 한다.
