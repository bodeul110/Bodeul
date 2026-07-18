# Issue 220 매칭·동행·리포트 스키마 1단계 기록

기준일: 2026-07-18

## 구현한 내용

- Flyway V5에 `companion_sessions`, `session_reports`, `appointment_follow_ups`, `companion_session_assignment_audits`를 추가했다.
- 관리자 배정은 role·상태·예약 버전을 검증하는 `assign_companion_session` 함수로 제한했다.
- V5 DDL rollback을 추가했다.
- Firestore 백업의 FK, 상태 코드와 시각을 검증하는 세션 전용 seed SQL 생성기와 rollback 경로를 추가했다.
- `nextVisitAt`의 날짜·자유 텍스트 혼용을 확인하고 정규화 시각과 원문을 함께 보존했다.
- 일회성 GitHub Environment secret과 SHA-256을 검증하는 보호된 백필 workflow를 추가했다.
- Core API에 역할 기반 세션 조회, 배정 매니저의 부분 갱신·단계 전환·리포트 완료 endpoint를 구현했다.
- 매칭 후 취소와 리포트 완료가 예약·세션을 같은 Spring 트랜잭션으로 갱신하도록 했다.
- V6에서 Core runtime에 필요한 세션·리포트 컬럼 쓰기만 허용하고 DELETE와 관리자 광범위 쓰기는 계속 차단했다.
- Android 예약·매니저·보호자 저장소가 Core API의 세션 진행과 리포트를 화면 원본으로 사용하도록 전환했다.
- 인증 HTTP 처리를 공통 클라이언트로 분리하고 예약 상세에 10초 Core API 갱신을 추가했다.
- 보호자 세션 보조 조회에 보호자 UID 조건을 추가해 Firestore Rules와 쿼리 계약을 맞췄다.
- 별도 관리자 Next.js 서버에 Firebase ADMIN 인가와 `assign_companion_session` 전용 배정 API를 연결했다.

## 변경된 범위

- Android의 세션 진행·현장 메모·약국 상태·리포트 읽기와 매니저 쓰기는 Core API를 사용한다.
- `chatMessages`, 첨부, 위치 좌표·이력·읽음 시각과 실시간 위치 상태는 #221 범위로 Firestore에 유지한다.
- 관리자 서버의 배정은 PostgreSQL 함수만 사용한다. PostgreSQL 후속 처리 API는 아직 연결하지 않았다.

## 검증

| 항목 | 결과 |
| --- | --- |
| 최신 백업 seed dry-run | 세션 2, 리포트 2, 후속 처리 1, 오류 0 |
| Firebase 도구 테스트 | 29건 통과 |
| V5 migration 계약 테스트 | 통과 |
| PostgreSQL 17 V1~V5 적용 | 통과 |
| 잘못된 예약 버전의 배정 | 거부 확인 |
| 관리자 배정 함수 | 예약 `MATCHED`, version 1, 세션 `READY`, 감사 1건 확인 |
| Core runtime의 배정 함수 실행 | 권한 거부 확인 |
| V5 rollback | 신규 테이블 4개 제거 확인 |
| 보호된 개발 DB 백필 | run `29638905550` attempt 2 성공, 일회성 secret 삭제 |
| 개발 DB 백필 결과 | 세션 2, 리포트 2, 후속 처리 1 |
| FK·`imported_at` 누락 | 모두 0건 |
| 예약·세션 상태 정합성 | `COMPLETED/COMPLETED` 1, `IN_PROGRESS/IN_TREATMENT` 1 |
| Supabase Security Advisor | lint 0건 |
| V6 PostgreSQL 17 적용 | 세션 UPDATE·리포트 INSERT 허용, 두 테이블 DELETE 거부, RLS 쓰기 정책 3개 확인 |
| Core runtime DML 리허설 | 예약·세션 `COMPLETED/COMPLETED`, `CANCELED/CANCELED`, 리포트 1건, 가이드 단계 5건 확인 |
| V6 rollback | 쓰기 권한·정책·추가 인덱스 0건 복구 확인 |
| Core API 전체 검사 | 통과 |
| Core API Preview 배포 | run `29639915209` 성공, commit `9d08c1be` 이미지와 리비전 `00010-pd9` 확인 |
| Preview 세션 API 무인증 경계 | `/health` 200 `UP`, `/api/companion-sessions` 401 `missing_authorization` |
| Preview 실제 token 읽기 | 환자·보호자·매니저 200과 각 세션 2건, 관리자 403 |
| Preview 쓰기 경계 | 환자 세션 수정 403, 매니저 잘못된 version 수정 409, 데이터 변경 없음 |
| Android 빌드·테스트 | `testDebugUnitTest`, `assembleDebug`, `connectedDebugAndroidTest` 1건 통과 |
| Android 실기기 | 매니저 홈 `4/7`, 과거 이력 완료 1건, 보호자 리포트 4건, 예약 상세 `4/7` 표시 |
| 예약 상세 갱신 | 25초 유지 후 Core API 10초 갱신 중 동일 상태 유지, 관련 오류 로그 없음 |
| Android App Check | 계측 테스트 후 재발급된 debug token을 비공개 등록하고 예약 목록·상세·세션 요청 3건 `valid` 확인 |
| 관리자 배정 API 계약 | 테스트 19건, lint, Next.js·Vite build, CodeQL 통과 |
| 관리자 Vercel Preview | 무인증 401, 환자 403, 관리자 입력 오류 400, 취소 예약 409, 임시 요청 예약 성공 201 |
| 관리자 배정 DB 결과 | 예약 `MATCHED`·version 1, 세션 `READY`, 감사 1건 확인 후 임시 데이터 잔여 0건 |
| `git diff --check` | 통과 |

PR #228 병합 후 개발 DB migration run `29638503856`에서 Flyway V5 적용을 완료했다. V5 이력 성공, 신규 테이블 4개의 owner `bodeul_migration`, RLS 활성화, Core/Admin SELECT 정책 7개를 확인했다. 배정 함수는 `security definer`이고 Admin runtime만 실행 가능하며 Core·Supabase client role은 실행할 수 없다.

PR #230 병합 후 개발 DB migration run `29639792606`에서 Flyway V6 적용을 완료했다. 백필 2/2/1건은 그대로 유지됐고 Core runtime은 세션 진행 컬럼 UPDATE와 리포트 지정 컬럼 INSERT가 가능하지만 두 테이블 DELETE는 불가능하다. Admin runtime의 세션 UPDATE·리포트 INSERT, Supabase client role의 쓰기 grant는 모두 0건이다. 쓰기 RLS 정책 3개와 외래키 covering index 7개를 확인했다.

로컬 PostgreSQL 리허설에는 임시 데이터만 사용했고 컨테이너는 종료 후 삭제했다. 최신 Firestore 백업과 생성 SQL은 Git 제외 경로에 있으며 커밋하지 않는다.

첫 백필 시도는 Windows PowerShell 파이프가 Base64를 UTF-16으로 전달해 파일 준비 단계에서 중단됐다. DB 쓰기 전 실패했고 임시 파일과 secret을 삭제했다. secret 본문을 출력하지 않는 인자 전달 방식으로 다시 등록해 attempt 2를 성공시킨 뒤 즉시 삭제했다.

V6 적용 후 Security Advisor lint는 0건이고 외래키 미인덱스 INFO 7건은 해소됐다. Performance Advisor에는 기존·신규 인덱스의 미사용 INFO만 남았다. 표본이 5건뿐이고 실제 API 트래픽이 없으므로 삭제 근거로 사용하지 않는다.

PR #231 병합 후 Preview deploy run `29639915209`에서 commit `9d08c1be5c84c28c6c34094e0b5c5511ce02de46` 이미지를 배포했다. Cloud Run 리비전 `bodeul-core-api-preview-00010-pd9`가 트래픽 100%를 처리하고 새 세션 endpoint가 무인증 요청을 401로 차단하는 것을 외부 호출로 확인했다. 이는 라우팅과 인증 필터 적용 확인이며 역할별 데이터 접근 성공을 의미하지는 않는다.

이후 개발 기준선 계정으로 실제 Firebase ID token 역할 검증을 수행했다. 환자·보호자·매니저는 각자 세션 목록 2건을 읽었고 관리자는 역할 거부 403을 받았다. 환자 수정은 403, 매니저의 실제 version보다 1 큰 수정 요청은 409를 반환해 DB 변경 없이 쓰기 경계와 낙관적 잠금을 확인했다.

Android debug 앱은 세션 상태와 리포트를 Core API에서 합성하도록 전환했다. SM-S921N 실기기에서 활성 세션은 서버와 화면 모두 `IN_TREATMENT`, `4/7`이었고 완료 세션은 서버 `7/7`, 매니저 이력 완료 1건으로 일치했다. 계측 테스트 뒤 앱이 제거되어 최종 APK를 재설치했으며, 예약 상세를 25초 유지해 주기 갱신 후에도 같은 상태와 무오류 로그를 확인했다. 앱 데이터 제거로 새 App Check debug token이 발급돼 token 원문을 노출하지 않고 allowlist에 다시 등록했다. 이후 예약 목록·상세·세션 요청이 모두 `app_check_verdict=valid`로 기록됐다.

관리자 웹 PR [#23](https://github.com/bodeul110/bodeul-admin-web/pull/23)을 병합해 `POST /admin/companion-assignments`를 연결했다. Firebase ID token과 PostgreSQL `ADMIN` 역할을 확인한 뒤 `bodeul_admin_runtime`만 실행 가능한 배정 함수를 호출한다. Vercel Preview에서 거부 경계와 임시 `REQUESTED` 예약의 성공 배정을 확인했고, 예약·세션·감사 결과를 검증한 뒤 임시 데이터는 모두 삭제했다. 배정 함수는 `search_path=bodeul, pg_temp`로 고정돼 있고 Core·Supabase client·PUBLIC role은 실행할 수 없으며 Security Advisor 경고는 0건이었다.

## 남은 범위

- 후속 처리 API를 연결하고 Core API만으로 생성된 예약·배정의 Firebase 보조 데이터 의존을 제거한다.
- 관리자 Preview에서 생성한 Core-only 예약·배정을 Android 목록이 Firestore 보조 문서 없이 조회하도록 전환하고 실기기에서 검증한다.
- 검증 완료 후 해당 Firestore 쓰기를 중지한다.
