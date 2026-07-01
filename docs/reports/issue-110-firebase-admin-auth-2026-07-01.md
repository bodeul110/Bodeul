# Issue 110 Firebase Admin SDK 인증 연결 기록

기준일: 2026-07-01

## 작업 목적

#88에서 만든 Firebase ID token 검증 유틸을 실제 Firebase Admin SDK 초기화 경로와 연결한다.

## 선택한 방식

`api/`에 `firebase-admin` 14.1.0을 정확 버전으로 추가하고, 서버 시작 시 환경변수에 따라 Firebase verifier를 만든다.

- `FIREBASE_SERVICE_ACCOUNT_JSON`이 있으면 서비스 계정 JSON으로 초기화한다.
- `FIREBASE_SERVICE_ACCOUNT_JSON`이 없고 `FIREBASE_PROJECT_ID`가 있으면 Application Default Credentials 기준으로 초기화한다.
- 둘 다 없으면 verifier를 만들지 않고 기존처럼 관리자 API 인증 요청에 503을 반환한다.
- 서비스 계정 JSON 형식이 잘못되면 서버 시작 단계에서 실패한다.

## 대안

- Firebase Admin SDK를 환경변수와 무관하게 무조건 초기화한다.
- 서비스 계정 JSON만 허용하고 Application Default Credentials는 허용하지 않는다.
- 인증 유틸은 mock으로 유지하고 실제 SDK 연결을 더 미룬다.

## 선택 이유

현재 MVP 규모에서는 secret 없는 CI와 로컬 실행을 깨지 않는 것이 중요하다. 운영 환경에서는 서비스 계정 JSON 또는 Application Default Credentials로 실제 검증을 수행하고, 설정이 없을 때는 명확한 503 응답을 유지하는 방식이 가장 안전하다.

## 리스크

- `firebase-admin` 계열 전이 의존성에서 `uuid` moderate 경고가 남는다.
- 서비스 계정 JSON을 잘못 주입하면 서버 시작이 실패한다.
- `FIREBASE_PROJECT_ID`만 사용하는 환경은 Application Default Credentials가 별도로 준비되어 있어야 실제 검증이 성공한다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `npm --prefix api run check` | 통과, 테스트 24개 성공 |
| Firebase 설정 없음 | verifier 없음, 관리자 API 503 유지 |
| `FIREBASE_PROJECT_ID` 설정 | Admin SDK 초기화 경로 테스트 통과 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` 설정 | cert 초기화 경로 테스트 통과 |
| 잘못된 서비스 계정 JSON | 시작 단계 실패 테스트 통과 |
| `npm --prefix api audit --json` | moderate 6건, `firebase-admin` 전이 의존성 경고 확인 |

## 남은 범위

- 실제 개발/운영 환경 secret 주입
- PostgreSQL role 기반 관리자 인가
- 실제 관리자 read API 연결
