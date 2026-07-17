# 인프라 개요

기준일: 2026-07-17

## 런타임

| 영역 | 구현 | 배포 |
| --- | --- | --- |
| Android | Java + XML | 로컬·실기기, GitHub Android Preflight |
| 관리자 웹/서버 | 별도 저장소 React + Next.js | Vercel Preview, Production target은 운영값 미연결 |
| 사용자 Core API | Java 21 + Spring Boot | Cloud Run Tokyo preview, production 배포 기반 준비 |
| 공용 DB | PostgreSQL | Supabase Tokyo 개발·production 분리 |
| 인증·푸시·파일 | Firebase Auth, FCM, Storage | `bodeul-dev`, `bodeul-prod-110` 분리 |
| Firebase 결합 로직 | Functions v2 | Firebase |

## 요청 경계

- 관리자 요청은 Next.js Route Handler가 인증·인가하고 PostgreSQL에 직접 접근한다.
- 사용자·매니저 요청은 Spring Core API가 인증·인가하고 PostgreSQL에 접근한다.
- 두 서버는 서로를 호출하지 않는다.
- 클라이언트는 PostgreSQL에 직접 연결하지 않는다.
- Firebase ID token은 두 서버에서 검증하고 DB role은 서버별로 분리한다.

## 배포와 비밀값

- 관리자 Preview 비밀값은 Vercel Preview environment에만 둔다.
- Core API DB URL과 Kakao REST 키는 Google Secret Manager에서 Cloud Run에 주입한다.
- GitHub Actions 배포는 장기 JSON key 대신 WIF를 사용한다.
- DB migration 자격 증명은 runtime 서비스에 전달하지 않는다.
- production GCP/Firebase 식별자, DB migration secret과 Cloud Run DB Secret version은 별도로 만들었다. Vercel Production 값과 Kakao production key는 아직 연결하지 않았다.

## 저장소 경계

메인 저장소는 Android, Core API, DB migration, Firebase Rules·Functions와 공용 문서를 담당한다. 관리자 UI·Next.js 서버·Vercel 배포는 별도 `bodeul-admin-web` 저장소가 담당한다. 과거 Node API와 메인 저장소 관리자 웹 중복본은 2026-07-17 제거했다.

## 현재 리스크

- Firestore와 PostgreSQL 병행 도메인의 데이터 불일치
- production 도메인과 운영자·출시 일정 미확정
- 관리자 App Check 미강제
- production pre-migration dump는 보관했지만 restore 리허설 미완료
- 역할 동기화와 감사 로그의 확장 필요

상세 흐름은 [현재 인프라 구성도](infra-overview.md), 목표와 전환 조건은 [목표 인프라 구조](target-infrastructure.md)를 따른다.
