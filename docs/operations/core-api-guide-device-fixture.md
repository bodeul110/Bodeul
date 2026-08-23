# Core API 가이드 실기기 fixture

## 작업 목적

개발 DB에서 13단계 가이드의 `PHARMACY_ROUTE` 화면을 실기기로 반복 검증하고 검증 후 테스트 데이터를 빠짐없이 정리한다.

## 선택한 방식

- `Core API DB Migration` workflow의 `guide_device_fixture_action`으로 `setup`, `status`, `cleanup`을 실행한다.
- 대상은 `master`의 `preview`와 Firebase 개발 프로젝트 `bodeul-dev`로 고정한다.
- Supabase 개발 프로젝트 ref와 migration DB 사용자명도 실행기에서 다시 확인한다.
- 기존 `manager@bodeul.app`과 `admin@bodeul.app` 행은 조회만 하고 수정하거나 삭제하지 않는다.
- fixture 전용 환자, 병원 가이드, 예약, 세션과 배정 감사 기록만 단일 트랜잭션으로 생성한다.
- 세션은 13단계 snapshot의 9번째 `PHARMACY_ROUTE`에서 시작한다.
- setup과 cleanup 동안 동행 세션 쓰기를 잠그고, cleanup 전에 모든 fixture 표식을 다시 대조한다.

## 실행

### 준비

1. `Core API DB Migration`에서 V14 이상이 개발 DB에 적용됐는지 확인한다.
2. `Core API Preview Deploy`에서 같은 `master` 커밋이 배포됐는지 확인한다.
3. 기준선 매니저에 진행 중인 다른 세션이 없는지 확인한다. 진행 중인 세션이 있으면 `setup`이 중단된다.

### fixture 생성

```powershell
gh workflow run core-api-migration.yml `
  --repo bodeul110/Bodeul `
  --ref master `
  -f target=preview `
  -f confirm_target=preview `
  -f guide_device_fixture_action=setup `
  -f confirm_guide_device_fixture_project=bodeul-dev
```

### 상태 확인

```powershell
gh workflow run core-api-migration.yml `
  --repo bodeul110/Bodeul `
  --ref master `
  -f target=preview `
  -f confirm_target=preview `
  -f guide_device_fixture_action=status `
  -f confirm_guide_device_fixture_project=bodeul-dev
```

성공 기준은 `fixtureRows=5`, `currentStepOrder=9`, `currentStepCode=PHARMACY_ROUTE`, `stepCount=13`, `ready=true`다.

### 정리

```powershell
gh workflow run core-api-migration.yml `
  --repo bodeul110/Bodeul `
  --ref master `
  -f target=preview `
  -f confirm_target=preview `
  -f guide_device_fixture_action=cleanup `
  -f confirm_guide_device_fixture_project=bodeul-dev
```

정리 후 `status`의 `fixtureRows=0`, `ready=false`를 확인한다. fixture 세션에 Storage 첨부가 생겼으면 파일 orphan 방지를 위해 cleanup이 중단되므로 첨부 파일과 DB 행을 별도 운영 절차로 함께 정리해야 한다.

## 대안

- 공개 예약 API, 관리자 배정 route와 단계 진행 API를 연결할 수 있지만 현재 삭제 API가 없어 테스트 행을 완전히 정리할 수 없다.
- 기존 세션 snapshot을 수정하는 방식은 V14 불변 계약을 훼손하므로 사용하지 않는다.
- Mock 모드는 코드 없는 7단계 가이드이므로 `PHARMACY_ROUTE` 조건을 증명하지 못한다.

## 선택 이유

현재 MVP 규모에서는 별도 범용 seed 시스템보다 preview에만 고정된 최소 fixture가 운영 부담과 오염 위험이 작다. setup과 cleanup을 같은 실행기와 고정 ID로 묶어 반복 실행과 실패 복구가 가능하다.

## 리스크

- 기준선 계정 이메일이나 역할이 바뀌면 setup이 안전하게 중단된다.
- setup이 실행되는 짧은 시간에는 개발 DB의 다른 동행 세션 쓰기가 대기한다.
- 검증 중 채팅 첨부를 추가하면 Storage까지 함께 정리해야 하므로 자동 cleanup을 허용하지 않는다.
- 이 fixture는 UI와 외부 이동 회귀 검증용이며 실제 예약 생성부터 관리자 배정까지의 전체 운영 흐름 검증을 대체하지 않는다.
