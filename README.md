<div align="center">

# 보들 (BoDeul)

**환자·보호자·매니저·운영자를 연결하는 병원 동행 플랫폼**

[![Android Preflight](https://github.com/bodeul110/Bodeul/actions/workflows/android-preflight.yml/badge.svg?branch=master)](https://github.com/bodeul110/Bodeul/actions/workflows/android-preflight.yml)
[![API Build](https://github.com/bodeul110/Bodeul/actions/workflows/api.yml/badge.svg?branch=master)](https://github.com/bodeul110/Bodeul/actions/workflows/api.yml)
[![Firebase Rules](https://github.com/bodeul110/Bodeul/actions/workflows/firebase-rules.yml/badge.svg?branch=master)](https://github.com/bodeul110/Bodeul/actions/workflows/firebase-rules.yml)
[![CodeQL](https://github.com/bodeul110/Bodeul/actions/workflows/codeql.yml/badge.svg?branch=master)](https://github.com/bodeul110/Bodeul/actions/workflows/codeql.yml)

[문서 홈](docs/README.md) · [현재 구현 상태](docs/status/implementation-status.md) · [관리자 웹 공유 배포](https://bodeul-admin-web-iota.vercel.app/) · [관리자 웹 저장소](https://github.com/bodeul110/bodeul-admin-web) · [GitHub Issues](https://github.com/bodeul110/Bodeul/issues)

</div>

> [!IMPORTANT]
> 현재 Android 앱과 관리자 웹의 주요 운영 데이터는 Firebase를 기본 경로로 사용합니다. Node `bodeul-api`와 Supabase 연동은 전환 검증용이며 production 전환은 아닙니다. 목표 구조는 Next.js 관리자 서버와 OCI Spring Core API가 같은 Supabase PostgreSQL을 각각 사용하는 방식입니다.

## 서비스 개요

보들은 병원 동행이 필요한 환자와 보호자, 현장에서 동행을 수행하는 매니저, 서비스 품질을 관리하는 운영자를 하나의 흐름으로 연결합니다.

- 동행 요청, 예약, 매칭, 진행 상태 확인을 하나의 서비스 흐름으로 관리합니다.
- 실시간 위치 공유, 채팅, 첨부파일, 알림으로 동행 중 소통을 지원합니다.
- 매니저 서류 심사, 일정 관리, 동행 기록과 최종 리포트를 제공합니다.
- 관리자 웹과 운영 도구로 심사, 문의, 병원 가이드, 데이터 점검을 처리합니다.
- Firebase의 실시간 기능을 유지하면서 관계형 조회와 운영 이력이 필요한 영역부터 PostgreSQL로 옮깁니다.

## 목차

- [핵심 기능](#핵심-기능)
- [기술 스택](#기술-스택)
- [저장소 구성](#저장소-구성)
- [아키텍처](#아키텍처)
- [API](#api)
- [개발 환경](#개발-환경)
- [실행과 검증](#실행과-검증)
- [설정과 보안](#설정과-보안)
- [배포와 운영](#배포와-운영)
- [주요 문서](#주요-문서)
- [로드맵](#로드맵)

## 핵심 기능

| 영역 | 제공 기능 | 현재 상태 |
| --- | --- | --- |
| 계정과 권한 | 이메일 회원가입·로그인·재설정, 이메일 인증, Google·Kakao 로그인, 역할별 진입 | 구현. Naver 로그인은 코드 경로만 있고 기본 비활성화 |
| 환자·보호자 | 동행 요청 CRUD, 보호자 연결, 진행 상태, 건강 정보 조회, 리포트·리뷰·정산·SOS 후속 흐름 | 구현 |
| 매니저 | 서류 제출·재제출, 심사 상태, 일정, 병원 가이드, 동행 기록, 최종 리포트 | 구현 |
| 실시간 동행 | 위치 공유와 이력, 채팅, 이미지·PDF 첨부, 읽음 처리, FCM 알림 | 구현 |
| 관리자 | 매칭, 진행 요청, 매니저 서류 심사, 병원 가이드, 문의·답변, 운영 액션 확인 | Android 관리자 화면과 관리자 웹에 구현 |
| 운영 자동화 | 예약 알림, Firebase seed·점검·백업·복원·리포트·preflight | 도구 구현. 운영 리허설은 단계별 진행 |
| PostgreSQL 전환 | Firebase ID token 검증, PostgreSQL 관리자 인가, 병원 가이드 조회 API | 1차 실연동 검증 완료, 운영 전환 전 |
| 후속 기능 | OCR 기반 복약 정보, AI 음성 기록 정리, 건강 프로필 영속화 | 기획·로드맵 단계 |

세부 완료 범위와 보류 항목은 [현재 구현 상태](docs/status/implementation-status.md)를 기준으로 확인합니다.

## 기술 스택

| 영역 | 기술 | 역할 |
| --- | --- | --- |
| Android | Java 17, XML, Android SDK 37, Gradle 9.6.1 | 환자·보호자·매니저·관리자 앱 |
| 관리자 웹 | 현재 React 19 + Vite 8, 목표 Next.js + Vercel | 서류 심사와 운영 백오피스, 관리자 서버 |
| API | 현재 Node.js 22 prototype, 목표 Java + Spring Boot + OCI | 사용자 서비스와 Kakao 서버 API, PostgreSQL 접근 경계 |
| Firebase | Authentication, Firestore, Storage, Functions v2, FCM, App Check | 인증, 실시간 데이터, 파일, 푸시와 운영 보조 |
| 관계형 데이터 | Supabase PostgreSQL | 관리자 처리 이력과 관계형 조회의 단계적 이전 대상 |
| 외부 연동 | Kakao Login, Kakao Maps, Kakao Local REST API | 소셜 로그인, 지도와 장소 검색 |
| 배포·자동화 | GitHub Actions, Vercel, Oracle VM preview | CI, 관리자 웹 공유 배포, API 실연동 검증 |
| 운영 도구 | Firebase CLI, Node.js 스크립트, GitHub Issues/Project | 규칙 검증, seed, 점검, 백업·복원, 작업 추적 |

App Check는 클라이언트에 연결돼 있으나 운영 강제 기준은 아직 확정 전입니다.

## 저장소 구성

| 경로 | 책임 |
| --- | --- |
| `app/` | Java + XML 기반 Android 앱과 역할별 사용자 흐름 |
| `api/` | 기존 Node `bodeul-api` prototype. Firebase token, PostgreSQL, 관리자 API 계약 검증 |
| `admin-web/` | 관리자 웹 전환 기간용 소스. 최종 기준 저장소 전환 전까지 유지 |
| `functions/` | Firebase Functions v2, FCM과 운영 보조 함수 |
| `tools/firebase/` | Firebase seed, 점검, 백업·복원, 리포트, rules 테스트와 preflight |
| `docs/` | 아키텍처, 설계 판단, 운영 절차, 상태와 검증 기록 |
| `.github/workflows/` | Android, API, 관리자 웹, Firebase Rules, CodeQL CI |
| `firestore.rules`, `storage.rules`, `firebase.json` | Firebase 권한과 배포 기준 |

관리자 웹은 [bodeul110/bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web)으로 히스토리를 보존해 분리 준비를 마쳤습니다. 다만 production 기준과 source of truth 전환이 끝날 때까지 이 저장소의 `admin-web/`도 유지합니다. 자세한 기준은 [관리자 웹 저장소 분리 계획](docs/operations/admin-web-repository-split.md)을 따릅니다.

## 아키텍처

```mermaid
flowchart LR
  subgraph Clients["클라이언트"]
    Android["Android 앱<br/>환자·보호자·매니저"]
    AdminWeb["관리자 웹<br/>React + Vite"]
  end

  subgraph Firebase["Firebase: 현재 기본 경로"]
    Auth["Authentication<br/>로그인과 세션"]
    Firestore["Cloud Firestore<br/>앱·웹 주요 데이터"]
    Storage["Cloud Storage<br/>서류와 채팅 첨부"]
    Functions["Cloud Functions v2<br/>운영 보조와 알림"]
    FCM["FCM<br/>푸시 알림"]
  end

  subgraph Backend["단계적 서버 경계"]
    Api["bodeul-api<br/>Node.js 22 + TypeScript"]
    FirebaseAdmin["Firebase Admin SDK<br/>ID token 검증"]
    Postgres[("Supabase PostgreSQL<br/>관계형 데이터")]
  end

  Kakao["Kakao API<br/>로그인·지도·장소"]

  Android --> Auth
  Android --> Firestore
  Android --> Storage
  Android --> Kakao
  AdminWeb --> Auth
  AdminWeb --> Firestore
  AdminWeb --> Storage
  Firestore --> Functions
  Functions --> FCM
  FCM --> Android

  AdminWeb -. "API 검증 모드" .-> Api
  Api --> FirebaseAdmin
  FirebaseAdmin --> Auth
  Api --> Postgres
```

실선은 현재 기본 경로, 점선은 PostgreSQL 전환을 검증하는 선택 경로입니다. 전체 구성과 전환 근거는 [현재 인프라 구성도](docs/architecture/infra-overview.md)와 [PostgreSQL API 경계](docs/architecture/postgres-api-boundary.md)에 정리돼 있습니다.

운영 목표는 [목표 인프라 구조](docs/architecture/target-infrastructure.md)를 따릅니다. 관리자 브라우저는 Vercel Next.js 관리자 서버를 사용하고, 환자·보호자·매니저 웹과 Android 앱은 OCI Spring Core API를 사용합니다. 두 서버는 서로를 경유하지 않고 공용 Supabase PostgreSQL에 별도 role로 접근합니다.

### 현재 데이터 경계

| 영역 | 현재 source of truth | 전환 판단 |
| --- | --- | --- |
| 인증과 세션 | Firebase Authentication | 유지. API도 Firebase ID token을 검증 |
| 앱 주요 데이터 | Cloud Firestore | 실시간성이 큰 예약·세션·위치는 후순위로 판단 |
| 병원 가이드 | Firestore 기본 경로 + PostgreSQL API 검증 경로 | 응답 비교 후 도메인 단위 전환 |
| 관리자 권한 | Firebase 사용자 역할 + PostgreSQL `app_users.role` | API에서는 두 검증을 모두 통과한 `ADMIN`만 허용 |
| 첨부파일 원본 | Firebase Storage | 원본은 유지하고 메타데이터 이전 여부만 검토 |
| 푸시 알림 | Cloud Functions + FCM | DB 전환과 분리해 유지 |

Firestore와 PostgreSQL이 병행 중이므로 하나의 ERD를 최종 데이터 모델처럼 제시하지 않습니다. PostgreSQL 스키마와 전환 대상은 [PostgreSQL 전환 문서](docs/architecture/postgres-operational-transition.md)에서 관리합니다.

## API

현재 `bodeul-api`는 클라이언트가 PostgreSQL에 직접 접속하지 않도록 인증, 인가와 데이터 접근 계약을 검증한 Node prototype입니다. 아래 endpoint는 Spring Core API와 Next.js 관리자 서버로 이식할 기준입니다.

| Method | Endpoint | 인증 | 용도 |
| --- | --- | --- | --- |
| `GET` | `/healthz` | 없음 | 배포·모니터링용 헬스체크 |
| `GET` | `/admin/api-contract` | Firebase ID token | 관리자 웹 초기 API 계약과 설정 상태 확인 |
| `GET` | `/admin/hospital-guides?limit=50` | Firebase ID token + PostgreSQL `ADMIN` | PostgreSQL 병원 가이드 목록 조회 |

요청·응답 예시, 환경변수와 오류 계약은 [API 문서](api/README.md)와 [관리자 API 계약](docs/architecture/admin-api-contract.md)을 기준으로 합니다.

## 개발 환경

| 대상 | 기준 환경 |
| --- | --- |
| Android | Android Studio, JDK 17, Android SDK 37 |
| Gradle | Wrapper 9.6.1 사용 |
| API·Functions | Node.js 22.x, npm |
| 관리자 웹 | 분리 저장소의 lockfile과 CI 기준, npm |
| Firebase 도구 | `tools/firebase`의 lockfile과 로컬 preflight 기준 |
| 형상·협업 | Git, GitHub CLI, GitHub Issues/Project |

의존성 버전은 각 모듈의 lockfile과 Gradle version catalog를 기준으로 하며, 별도 검토 없이 일괄 업그레이드하지 않습니다.

## 실행과 검증

### Android

```powershell
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
```

### API

```powershell
npm --prefix api ci
npm --prefix api run check
npm --prefix api run build
npm --prefix api start
```

기본 주소는 `http://127.0.0.1:8080`이며, 데이터베이스와 인증 설정은 [API 문서](api/README.md)의 환경변수 표를 따릅니다.

### 관리자 웹

```powershell
npm --prefix admin-web ci
npm --prefix admin-web run lint
npm --prefix admin-web run build
npm --prefix admin-web run dev
```

새 관리자 웹 작업은 [분리 저장소](https://github.com/bodeul110/bodeul-admin-web)의 PR과 현재 source of truth 전환 상태를 먼저 확인합니다.

### Firebase 운영 도구

```powershell
npm --prefix tools/firebase ci
npm --prefix tools/firebase run preflight:local
```

운영 데이터에 쓰는 명령은 dry-run과 대상 프로젝트 확인을 먼저 수행합니다. 명령별 사용 시점은 [Firebase 운영 문서](docs/operations/firebase/setup.md)를 확인합니다.

## 설정과 보안

| 대상 | 로컬 설정 | 원칙 |
| --- | --- | --- |
| Android | `local.properties`, `google-services.json` | 로컬에서만 관리하고 커밋하지 않음 |
| API | `DATABASE_URL`, Firebase Admin 설정, CORS allow-list | 서버 또는 GitHub Environment secret으로만 주입 |
| 관리자 웹 | `admin-web/.env.example` 기준 Vite 환경변수 | 실제 환경값을 소스에 직접 작성하지 않음 |
| Firebase | 서비스 계정 키, CLI token, App Check debug token | 공개 Issue·PR·로그에 남기지 않음 |

테스트 계정과 seed 데이터는 공개 README에 비밀번호를 적지 않고 [내부 테스트 가이드](docs/operations/internal-test-guide.md)에서 환경별로 관리합니다.

## 배포와 운영

| 대상 | 현재 방식 | 상태 |
| --- | --- | --- |
| Android | GitHub Actions `Android Preflight`, 로컬 debug build | 배포 전 컴파일·테스트 검증 |
| 관리자 웹 | [Vercel 팀 공유 배포](https://bodeul-admin-web-iota.vercel.app/) + 별도 저장소 | 화면 검증 가능. production Firebase·도메인·App Check 기준 확정 전 |
| API | Oracle preview 환경 + Supabase 개발 DB + Firebase Admin | 병원 가이드 API 실연동 1차 검증 완료, production 전환 전 |
| Firebase | `bodeul-dev`, Rules·Functions·FCM·Storage 운영 도구 | 개발 기준. dev/prod 분리와 App Check 강제는 후속 |

관리자 웹 링크는 팀 공유와 화면 검증을 위한 배포입니다. 별도 production 환경으로 간주하지 않으며, 운영 전환 조건은 [관리자 웹 환경 기준](docs/operations/admin-api-environments.md)과 관련 GitHub Issue에서 결정합니다.

## 주요 문서

| 문서 | 내용 |
| --- | --- |
| [문서 홈](docs/README.md) | 전체 문서 구조와 기준 |
| [현재 구현 상태](docs/status/implementation-status.md) | 역할별 완료 기능과 남은 범위 |
| [현재 인프라 구성도](docs/architecture/infra-overview.md) | Firebase, API, PostgreSQL, 배포 흐름 |
| [Android 앱 구조](docs/architecture/app-architecture.md) | Activity, Coordinator, Binder, Repository 역할 |
| [관리자 웹 구조](docs/architecture/admin-web-architecture.md) | 백오피스 역할, 권한과 데이터 접근 |
| [PostgreSQL API 경계](docs/architecture/postgres-api-boundary.md) | Firebase와 PostgreSQL 혼용·전환 기준 |
| [설계 판단 기록](docs/architecture/decision-log.md) | 목적, 대안, 선택 이유와 리스크 기록 규칙 |
| [내부 테스트 가이드](docs/operations/internal-test-guide.md) | 역할별 테스트와 seed 사용 방법 |
| [협업 규칙](docs/operations/collaboration-rules.md) | 브랜치, PR, 검증과 문서화 절차 |

## 로드맵

- 병원 가이드에서 검증한 API 경계를 관리자 조회 도메인부터 단계적으로 확장합니다.
- 메인 저장소의 `core-api/` Spring 프로젝트와 OCI preview 배포 기반을 먼저 구축합니다.
- 관리자 웹은 기능을 유지하면서 Next.js로 단계 이전합니다.
- 관리자 웹 별도 저장소의 source of truth, production 환경과 배포 책임을 확정합니다.
- Firebase dev/prod 분리, App Check 강제, 백업·복원 리허설과 비용 모니터링을 완료합니다.
- 전환 도메인마다 Firestore·PostgreSQL 응답 비교, rollback 기준과 write 소유권을 문서화합니다.
- 데이터와 서버 경계가 안정된 뒤 OCR 복약 정보와 AI 음성 기록 정리를 구현합니다.

작업 우선순위와 진행 상태는 [GitHub Issues](https://github.com/bodeul110/Bodeul/issues)와 `BoDeul 작업 백로그`를 기준으로 관리합니다.
