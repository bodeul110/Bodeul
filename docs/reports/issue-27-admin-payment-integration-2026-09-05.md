# Issue 27 관리자 결제 개발 DB 적용·연동 검증

기준일: 2026-09-05

## 작업 판단

- 작업 목적: 병합된 관리자 결제 조회 계약을 개발 DB에 적용하고 실제 인증·인가 경계를 확인한다.
- 선택한 방식: `master`의 기존 Preview migration workflow를 사용하고, 성공 여부와 함수 권한을 DB에서 다시 조회한다. seed와 fixture 변경은 실행하지 않는다.
- 대안: 로컬 DDL 직접 실행, 관리자 권한 자동 부여, Production 동시 적용은 선택하지 않았다.
- 선택 이유: 현재 MVP에서는 기존 승인·migration 경계를 유지하고 환경 적용과 업무 권한 부여를 분리하는 편이 대상 혼동과 권한 확대를 줄인다.
- 리스크: migration·무인증 차단 성공만으로 실제 운영 관리자 조회·입금 대조 성공을 증명할 수 없다. 조회 성공도 감사 INSERT가 발생하므로 이후 테스트는 개발용 합성 예약으로 제한한다.

## 구현한 내용

- 선행 [메인 PR #406](https://github.com/bodeul110/Bodeul/pull/406), [관리자 웹 PR #51](https://github.com/bodeul110/bodeul-admin-web/pull/51)은 최신 커밋 리뷰와 CI 확인 후 병합됐다.
- 적용 커밋: `54a6948314b5330e4a10d018463cfae49dba9eac`.
- [Preview migration #33958588324](https://github.com/bodeul110/Bodeul/actions/runs/33958588324)에서 V22 → V23 적용과 계정 삭제 영향도 DB 계약 검증이 성공했다.
- 입력은 `target=preview`, `confirm_target=preview`, `apply_companion_session_seed=false`, 두 fixture action 모두 `none`이다. 기존 Preview Environment 승인으로 실행했다.
- 원격 변경은 관리자 전용 조회 함수 V23과 사용자 승인을 받은 개발 최초 `SUPER_ADMIN` 1건, 해당 권한 변경 감사 1건, 실제 결제 조회 감사 1건이다. 원장·예약 수정, 쓰기 플래그 변경, Production DB 적용은 실행하지 않았다.

### 개발 최초 관리자 권한

- 개발 Firebase의 기존 관리자 테스트 계정으로 정상 로그인하고, 반환된 UID의 SHA-256과 DB의 `ADMIN` 행을 대조해 대상이 정확히 1건인지 확인했다. 이메일, UID, 암호, 토큰은 이 문서에 기록하지 않는다.
- 사용자가 개발 계정의 최초 `SUPER_ADMIN` 부여를 명시적으로 승인한 뒤 적용했다. 운영 관리자 계정을 발급하거나 Production 역할을 복제한 작업은 아니다.
- 연결된 DB 관리 도구에서는 `SET ROLE bodeul_migration`이 거부됐다. 역할 전환 권한을 추가하지 않고, 이미 INSERT와 감사 함수 실행 권한을 가진 관리 연결의 `postgres` 역할로 이 개발 환경의 최초 배정을 수행했다. [기본 bootstrap 절차](../architecture/admin-rbac.md#초기-역할-bootstrap)의 migration role 실행과 구분되는 실제 실행 경로다.
- 기존 역할 변경 함수와 같은 advisory lock, 역할 테이블 잠금, V23 적용 여부, 기존 배정 0건, 승인 대상 1건 검사를 포함한 트랜잭션을 사용했다. 역할 INSERT와 `record_admin_access_audit` 호출을 함께 검사한 뒤 먼저 `ROLLBACK`하여 배정 0건 유지를 확인했다.
- 같은 검사를 다시 수행하고 `COMMIT`했다. 2026-09-05 18:57 KST에 승인 대상의 활성 `SUPER_ADMIN` 1건과 `ROLE_CHANGE / ALLOWED` 감사 1건을 재조회했다. 감사에는 최초 설정 여부, 개발 환경, 실제 DB 실행 역할과 이전·새 업무 역할을 남겼다.
- 이후 권한 변경은 일반 역할 관리 함수 경계를 사용한다. 공용 테스트 계정의 운영 재사용과 실제 개인정보 검증은 이번 승인 범위가 아니다.

## 검증

| 항목 | 결과 |
| --- | --- |
| 적용 전 | `bodeul.flyway_schema_history` V22 성공, V23 함수 없음 |
| 적용 후 | V23 `success=true`, 2026-09-05 18:41 KST 적용 확인 |
| Core API 검증 | migration workflow의 `check` 통과 |
| 함수 소유자·검색 경로 | `bodeul_migration`, `search_path=bodeul, pg_temp` |
| 함수 실행 권한 | 검사한 애플리케이션 역할 중 `bodeul_admin_runtime`만 허용(함수 소유자·관리 역할 제외). `PUBLIC`, `anon`, `authenticated`, `service_role`, `bodeul_core_runtime`은 거부 |
| 원장·이벤트 직접 권한 | 관리자 runtime의 SELECT/INSERT/UPDATE/DELETE 없음, Core runtime SELECT 없음 |
| 원장·이벤트 RLS | 활성 상태 유지 |
| 데이터 건수 | 전후 동일: 사용자 6, 예약 6, 동행 세션 2, 결제 원장 1, 결제 이벤트 4 |
| 최초 권한 부여 전 | `app_users.ADMIN` 1건, 세부 역할 배정 0건 |
| 최초 권한 부여 후 | 승인 대상 활성 `SUPER_ADMIN` 1건, 권한 변경 감사 1건 |
| 권한 부여 전 실제 인증 | 환자·보호자 GET/PATCH 4건은 `403 / admin_role_required`, 개발 관리자 GET/PATCH 2건은 `403 / admin_detail_role_required`; 모두 JSON, `no-store` |
| 권한 부여 후 일반 사용자 | 환자·보호자 GET/PATCH 4건 모두 `403 / admin_role_required` 유지 |
| 권한 부여 후 관리자 조회 | 실제 Preview GET `200`, JSON, `no-store`, 상세·이력 응답 계약 일치 |
| 관리자 쓰기 잠금 | 같은 계정의 PATCH `{}`는 `423 / payment_writes_disabled`; 실제 전이 입력 검증은 아님 |
| 조회 데이터 | 기존 합성 예약·결제 `CANCELED`, 버전 3, 이력 4건, `transitionsEnabled=false` |
| 조회 감사·불변 확인 | `RAW_VIEW / APPOINTMENT_PAYMENT / ALLOWED` 1건 추가. 기존 결제 버전·이력 건수 유지, 수취 금액·확인 시각·환불 시각 모두 null |

HTTP 검사는 웹 PR #51의 최신 커밋 `ddea74ccb5c8fefd7f3298e728e19e5ecd2ec56f`에 해당하는 불변 Preview 배포 `dpl_FEZScwmcCmLZhrGmsYYUAD1HP7xt`를 대상으로 실행했다. Vercel Deployment Protection은 유지하고 공식 CLI로 접근했다. Firebase 정상 로그인의 ID token을 실제 관리자 서버가 검증한 결과이며, API 응답을 합성하거나 인증 의존성을 대체하지 않았다. 전체 인증 요청 12건이 기대 결과와 일치했다.

MFA·App Check는 기존 Preview의 `observe` 설정을 유지했다. 이번 요청은 MFA second-factor claim과 유효한 App Check 토큰을 검증한 증적이 아니며, 브라우저 로그인·추가 인증·화면 조작까지 확인한 결과도 아니다. Firebase ID token은 메모리와 HTTP 헤더 전달에만 사용하고 보고서·저장소·공개 댓글에 남기지 않았다.

Supabase security advisor에는 RLS가 켜져 있지만 직접 접근 정책이 없는 테이블 6개의 정보성 알림만 있었다. 제한 함수를 통해 접근하는 현재 계약을 유지하며 알림 제거를 위한 공개 정책은 추가하지 않았다. [공식 알림 설명](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)을 참고한다.

## 변경된 범위

- 개발 DB의 V23 함수와 Flyway 적용 이력, 승인된 최초 관리자 배정과 권한 변경·결제 조회 감사.
- 이 결과 보고서와 관리자 결제 계약의 검증 링크.
- 애플리케이션 코드, 패키지 버전, Firebase Rules, Production 환경변수는 변경하지 않았다.

## 남은 범위

1. 개발 DB·합성 예약임을 확인한 후 검증용 Preview에서만 상태 변경을 일시 허용하고 정상 전이, 감사, 버전 충돌, 동일 작업 재시도를 검증한다. 기존 취소 예약을 정상 입금 확인 대상으로 재사용하지 않고 새 합성 예약으로 분리한다. Production 쓰기는 계속 차단한다.
2. 실제 관리자 브라우저의 MFA와 App Check 토큰 발급·검증은 별도 확인한다. 이번 HTTP 검사에서는 기존 Preview의 관찰 모드를 변경하지 않았으며, 정상 Firebase 인증·DB 역할 검증을 MFA/App Check 강제 검증 완료로 해석하지 않는다.

이번 실행에서 별도 DB dump나 원격 rollback을 수행하지 않았다. V23의 격리 DB 적용·rollback 검사는 선행 PR의 CI 증적이며 실제 복원 리허설로 대체하지 않는다. 필요 시 웹을 이전 배포로 복구한 뒤 V23 조회 함수만 제거하는 기존 rollback 절차를 사용하고 원장·이력·감사는 삭제하지 않는다. #27은 통합 검증이 남아 있어 진행 중으로 유지한다.
