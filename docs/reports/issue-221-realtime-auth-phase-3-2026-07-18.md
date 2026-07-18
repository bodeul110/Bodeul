# Issue 221 Firebase JWT와 private Realtime 인가 3단계

기준일: 2026-07-18

## 작업 목적

Core API가 PostgreSQL에 저장한 채팅·위치 변경 신호를 세션 참여자만 받을 수 있도록 개발 Supabase의 private Broadcast 인가와 Firebase 사용자 token 계약을 완성한다.

## 선택한 방식

- 개발 Supabase Third-Party Auth에는 Firebase 프로젝트 `bodeul-dev`만 등록한다.
- Firebase ID token에는 Supabase가 PostgreSQL `authenticated` 역할로 해석할 `role: authenticated` custom claim을 넣는다.
- 신규 사용자는 Firebase Auth `onCreate` 함수가 기존 claim을 보존하면서 역할을 추가한다.
- 기존 사용자는 dry-run이 기본인 운영 스크립트로 역할을 백필한다.
- `realtime.messages`의 SELECT 정책은 `companion-session:{UUID}` topic, Firebase `aud`·`iss`, 환자·보호자·배정 매니저 관계를 함께 확인한다.
- Supabase Realtime은 `private_only=true`로 설정해 public channel 우회를 허용하지 않는다.

## 권한 경계

인가 helper는 `postgres` 소유의 `SECURITY DEFINER`, `STABLE`, 고정 `search_path` 함수다. `authenticated`에는 helper schema `USAGE`와 함수 `EXECUTE`만 주고, 허용 Firebase 프로젝트 table 조회와 `bodeul` 업무 schema 사용 권한은 주지 않는다. Broadcast 수신용 SELECT 정책만 두며 클라이언트 Broadcast INSERT 정책은 만들지 않는다.

개발 환경 허용 프로젝트는 `bodeul-dev` 한 개다. production bootstrap은 `bodeul-prod-110`만 허용하도록 별도 파일로 두었지만 production Supabase에는 아직 적용하지 않았다.

## 적용 결과

- 개발 Supabase에 Realtime 인가 bootstrap과 `bodeul-dev` 환경 설정 migration을 적용했다.
- Third-Party Auth에 Firebase issuer와 Google Secure Token JWKS가 `firebase` 유형으로 등록되고 JWKS 해석이 완료됐다.
- Realtime 설정은 서비스 중단 없이 `private_only=true`, authorization connection pool 2를 유지한다.
- Firebase 함수 `assignSupabaseAuthenticatedRole`을 `asia-northeast3`, Node.js 22 1세대로 배포했다.
- 기존 Firebase 사용자 9명에게 역할을 적용했고 재실행 dry-run의 변경 대상은 0명이었다.

## 검증

1. PostgreSQL 17 임시 인스턴스에서 환자·보호자·배정 매니저 허용과 비참여 사용자·다른 Firebase 프로젝트·잘못된 역할·잘못된 topic 거부를 확인했다.
2. 개발 Supabase에서도 같은 7개 허용·거부 시나리오가 모두 통과했다.
3. 검증 transaction rollback 뒤 임시 사용자·예약·세션 잔여가 0건임을 확인했다.
4. `authenticated`의 helper 실행은 허용되고 허용 프로젝트 table 조회와 업무 schema 사용은 거부됨을 확인했다.
5. Supabase Security Advisor 결과는 0건이다. Performance Advisor의 신규 table 미사용 index는 실제 트래픽 전 INFO이므로 삭제하지 않는다.
6. Functions claim 병합 테스트 2건과 Firebase 운영 도구 테스트 32건이 통과했다.
7. 임시 신규 Firebase 사용자에게 실제 `role: authenticated` token이 발급됐다.
8. 해당 사용자의 참여 세션 private WebSocket join은 `ok`, 비참여 세션 join은 `error`였다.
9. 종단 검증 뒤 임시 Firebase 사용자를 삭제했고 PostgreSQL 검증 데이터도 0건으로 정리했다. Firebase 사용자는 다시 9명, 백필 대상은 0명이다.

## 대안과 선택 이유

Supabase Auth로 사용자를 이중 관리하는 대신 기존 Firebase Auth를 Third-Party Auth로 연결했다. 현재 앱 인증과 FCM, App Check가 Firebase에 연결되어 있어 인증 원본까지 동시에 바꾸면 장애 범위가 커진다. Firebase JWT는 인증에만 사용하고 업무 권한은 PostgreSQL 참여 관계로 다시 확인해 데이터 권한을 token claim 하나에 맡기지 않는다.

클라이언트가 Realtime payload를 상태 원본으로 처리하지 않는다. payload에는 식별자만 있고 Android는 이벤트 수신과 재연결 때 Core API snapshot을 다시 조회한다. 이 방식은 중복·유실·순서 변경이 있어도 최종 상태를 PostgreSQL 응답에 맞출 수 있다.

## 남은 범위

- Android 채팅·위치 Core API 쓰기, Firebase token private channel 구독과 Firestore client 쓰기 차단 코드는 4단계에서 완료했다.
- 재연결과 token 갱신 시 snapshot 재조회, Core API FCM fallback과 Firestore Rules를 개발 환경에 적용하고 실기기에서 검증한다.
- production에는 Android 통합 검증 뒤 production Firebase Third-Party Auth와 production RLS를 별도로 적용한다.
- #222에서 만료 행과 Firebase Storage 첨부를 정리하는 일일 파기 job을 구현한다.

## 리스크

- Auth `onCreate`는 비동기이므로 신규 가입 직후 첫 token에는 역할이 없을 수 있다. Android는 첫 private 구독 전에 ID token을 강제 갱신해야 한다.
- Realtime 인가는 연결 동안 cache되므로 참여 관계가 바뀌면 새 token 전송 또는 재연결이 필요하다.
- Broadcast는 업무 저장 성공 여부와 분리되어 있으므로 Android의 snapshot 재조회와 FCM fallback이 필수다.
- Firebase Functions 패키지의 최신 버전 경고는 확인했지만 승인 없는 의존성 업그레이드는 이번 범위에 포함하지 않았다.
