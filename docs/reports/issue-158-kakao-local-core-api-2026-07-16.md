# Issue 158 Kakao Local Core API preview 실검증

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다. 현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Android에 Kakao Local REST 키를 두지 않고 Spring Core API가 장소 검색을 대행하는 구조가 실제 개발 인프라에서도 동작하는지 확인한다.

## 선택한 방식

- Kakao Local REST 키는 Google Secret Manager의 숫자 버전으로 관리한다.
- Cloud Run 런타임 서비스 계정에 해당 Secret의 `roles/secretmanager.secretAccessor`만 부여한다.
- Android는 Firebase ID token을 포함해 `GET /api/places/search`를 호출한다.
- PostgreSQL의 `app_users.role`로 인가된 사용자만 Kakao Local 호출 경계에 도달한다.

## 대안

- Android가 Kakao Local REST를 직접 호출하는 방식
- Firebase Functions가 장소 검색을 대행하는 방식
- Cloud Run에 키 원문을 일반 환경 변수로 저장하는 방식

## 선택 이유

현재 MVP 규모에서는 이미 구축한 Spring 인증·인가 경계와 6시간 캐시, 사용자별 호출 제한을 함께 사용하는 편이 운영 경로가 단순하다. 키 원문을 APK와 GitHub에서 제거할 수 있고, 숫자 Secret 버전으로 배포 리비전과 키 회전을 연결할 수 있다.

## 검증 대상

| 항목 | 값 |
| --- | --- |
| Google Cloud 프로젝트 | `bodeul-dev` |
| 리전 | `asia-northeast1` |
| Cloud Run 서비스 | `bodeul-core-api-preview` |
| 리비전 | `bodeul-core-api-preview-00006-hdk` |
| 배포 commit | `c7dce86788e302c3db947a8222d04235d03203c4` |
| GitHub Actions | [Core API Preview Deploy 29483942245](https://github.com/bodeul110/Bodeul/actions/runs/29483942245) |
| Secret ID | `bodeul-core-api-preview-kakao-local-rest-api-key` |
| Secret 버전 | `1` |
| 서비스 URL | `https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app` |

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| Secret 저장 형식 | 활성 버전 `1`을 읽어 32자리 REST 키 형식만 확인하고 임시 파일을 삭제했다. |
| Secret IAM | Secret 리소스 IAM에서 `bodeul-core-preview-runtime@bodeul-dev.iam.gserviceaccount.com`의 accessor 바인딩을 확인했다. |
| Cloud Run 참조 | 리비전 `00006-hdk`가 Secret 버전 `1`을 참조하고 트래픽 100%를 처리한다. |
| 배포 workflow | Core API test, WIF 인증, 이미지 게시, Cloud Run 배포, smoke test가 모두 통과했다. |
| 무인증 경계 | workflow에서 `/health` 200, 무인증 `/api/auth/me`와 `/api/places/search` 401을 확인했다. |
| 인증된 장소 검색 | 임시 Firebase 사용자와 `PATIENT` 역할로 `query=서울대학교병원`, `category=HOSPITAL`을 호출해 HTTP 200과 장소 15건을 확인했다. |
| Kakao 쿼터 반영 | Kakao Developers 콘솔에서 Local 키워드 검색 일일 사용량 1건, 제공량 100,000건을 확인했다. |
| Cloud Run 로그 | 리비전 로그 32건에서 오류 0건, REST 키·Authorization·Bearer·JWT 형태·임시 UID·임시 이메일 접두어 노출 0건을 확인했다. |
| 정리 | Firebase 사용자 삭제 API가 성공했고 임시 인증 파일을 제거했다. PostgreSQL 임시 역할 행은 삭제 후 부재, 임시 DB role membership은 비지속 상태를 확인했다. |

Kakao Developers의 REST 키 호출 허용 IP는 비어 있다. 현재 개발 Cloud Run은 고정 outbound IP가 없으므로 이 상태가 실호출에는 맞다. production에서 IP 제한을 적용하려면 먼저 Serverless VPC Access와 Cloud NAT로 고정 egress를 구성해야 한다.

## 리스크

- Firebase 사용자 삭제 후 별도 관리자 조회는 현재 로컬 Google Cloud 계정의 Firebase 사용자 조회 권한 부족으로 403이어서 수행하지 못했다. 삭제 API 성공과 임시 인증 파일 제거를 정리 근거로 사용했다.
- 캐시와 분당 제한은 인스턴스 메모리 기준이다. 개발 환경은 최대 인스턴스 1개라 일관되지만 수평 확장 시 공용 저장소나 게이트웨이 제한이 필요하다.
- Kakao 쿼터와 정책은 변경될 수 있으므로 production 전 콘솔에서 제공량과 호출 허용 IP를 다시 확인한다.
- 최소 인스턴스 0은 비용을 줄이지만 첫 장소 검색에서 cold start 지연이 생길 수 있다.

## 결론

Issue #158의 개발 인프라 범위는 완료했다. Android 직접 REST 호출과 키 리소스가 제거됐고, Core API Secret 주입, 인증·인가, 실제 Kakao 응답, 쿼터 반영, 비밀값 로그 비노출까지 확인했다. 다음 인프라 우선순위는 관리자 Next.js 전환과 production 환경 분리다.
