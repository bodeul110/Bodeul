# Issue 113 병원 가이드 read API 기록

기준일: 2026-07-01

## 작업 목적

관리자 웹 전환 검증을 위해 낮은 위험 도메인 하나를 `bodeul-api` 실제 read API로 연결한다.

## 선택한 방식

첫 read API는 PostgreSQL `hospital_guides` 조회로 제한한다.

```sql
select id, hospital_name, department_name, steps, created_at, updated_at
from hospital_guides
order by updated_at desc, hospital_name asc, department_name asc
limit $1
```

- endpoint는 `GET /admin/hospital-guides`다.
- `limit`은 기본 50, 최대 100이다.
- Firebase ID token 인증과 PostgreSQL `ADMIN` role 인가를 통과해야 한다.
- Supabase schema, seed, migration은 변경하지 않는다.
- `admin-web/`는 관리자 웹 레포 분리 작업과 충돌하지 않도록 수정하지 않는다.

## 대안

- 매니저 서류 심사 메타데이터를 먼저 연결한다.
- 문의 조회를 먼저 연결한다.
- 관리자 웹 연결까지 같은 PR에 포함한다.

## 선택 이유

Supabase 스키마 조회 결과 `hospital_guides`는 `hospital_name`, `department_name`, `steps`, timestamps 중심이고 개인정보 노출 위험이 낮다. `steps`는 `jsonb` 배열로 확인되어 API DTO를 단순하게 고정할 수 있다. 관리자 웹 레포 분리 작업이 진행 중이므로 이번 범위는 API 서버와 문서로 제한한다.

## 리스크

- Firestore 기준 병원 가이드 데이터와 PostgreSQL 데이터가 다르면 관리자 웹 전환 QA에서 차이가 발생할 수 있다.
- `steps` 내부 구조는 아직 상세 스키마를 강제하지 않으므로 관리자 웹 연결 시 화면 모델과 재검증해야 한다.
- 실제 배포 환경에서는 `DATABASE_URL`, Firebase Admin SDK 설정, 관리자 `app_users.role` 데이터가 모두 필요하다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `npm --prefix api run check` | 통과, 테스트 47개 성공 |
| `git diff --check` | 통과 |
| `GET /admin/hospital-guides` | 관리자 권한으로 병원 가이드 목록 반환 |
| `limit` query | 기본 50, 최대 100 검증 |
| 비관리자 접근 | #112 공통 인가로 403 처리 |
| 조회기 미설정 | 503 `hospital_guides_not_configured` |
| 조회 실패 | 503 `hospital_guides_lookup_failed` |
| `npm --prefix api audit --json` | moderate 6건, #110의 `firebase-admin` 하위 의존성 경고와 동일 |
| 민감값 패턴 검색 | 실제 secret 없음, GitHub Actions 변수 참조만 확인 |
| Supabase 변경 | 없음 |
| `admin-web/` 변경 | 없음 |

## 남은 범위

- 관리자 웹 레포 분리 이후 `VITE_BODEUL_DATA_BACKEND=api` 연결
- 기존 Firestore 병원 가이드 응답과 PostgreSQL/API 응답 비교 검증
- 관리자 웹 화면 모델에 맞춘 `steps` 세부 계약 검증
