# 비용과 쿼터 모니터링

기준일: 2026-07-16

이 문서는 개발 인프라의 Google Cloud/Firebase 비용과 Kakao Local 쿼터를 정기 점검하는 기준이다. budget은 지출을 자동 차단하지 않으며, 알림을 받은 운영자가 원인을 확인하고 대응해야 한다.

## Google Cloud budget

| 항목 | 현재 값 |
| --- | --- |
| 대상 프로젝트 | `bodeul-dev` |
| budget 이름 | `BoDeul dev monthly budget` |
| 기간 | 매월 |
| 금액 | 10,000 KRW |
| 알림 | 현재 지출 50%, 80%, 100% |
| 수신자 | 결제 계정의 Billing Account Administrator, Billing Account User |

현재 규모에서는 낮은 개발 예산으로 오설정, 무한 호출, scheduled job 반복을 조기에 발견하는 것이 목적이다. 정상적인 preview 검증이 반복적으로 10,000 KRW를 넘기면 금액을 바로 높이지 않고 서비스별 사용량과 무료 할당량을 먼저 확인한다.

확인 명령은 결제 계정 ID를 로컬에서 조회한 뒤 실행한다. 실제 ID는 문서나 공개 이슈에 남기지 않는다.

```powershell
gcloud billing projects describe bodeul-dev
gcloud billing budgets list `
  --billing-account=<BILLING_ACCOUNT_ID> `
  --filter='displayName="BoDeul dev monthly budget"'
```

## 점검 metric

Cloud Monitoring에서 다음 metric을 기준으로 본다.

| 영역 | metric | 확인 내용 |
| --- | --- | --- |
| Firestore | `firestore.googleapis.com/document/read_count` | 전체 조회, 실시간 listener 증가 |
| Firestore | `firestore.googleapis.com/document/write_count` | 위치, 채팅, 상태 변경 증가 |
| Firestore | `firestore.googleapis.com/document/delete_count` | 예상하지 않은 정리 작업 |
| Firestore | `firestore.googleapis.com/storage/data_and_index_storage_bytes` | 데이터와 index 저장량 증가 |
| Cloud Run | `run.googleapis.com/request_count` | Core API 요청 급증과 반복 호출 |
| Cloud Run | `run.googleapis.com/container/instance_count` | 최대 인스턴스 1 기준 이탈 여부 |
| Cloud Functions | `cloudfunctions.googleapis.com/function/execution_count` | scheduled/callable/trigger 반복 실행 |
| Cloud Storage | `storage.googleapis.com/api/request_count` | 업로드와 관리자 미리보기 반복 |
| Cloud Storage | `storage.googleapis.com/storage/total_bytes` | 매니저 서류와 첨부 파일 누적 |

## 점검 주기

| 시점 | 확인 항목 | 기록 위치 |
| --- | --- | --- |
| 매주 월요일 | 최근 7일 Firestore, Functions, Cloud Run, Storage 추세 | 이상이 있을 때 GitHub Issue |
| 배포·부하 검증 직후 | Cloud Run 요청/5xx, Functions 실행 증가, Kakao Local 쿼터 | 관련 PR 또는 검증 보고서 |
| 매월 첫 영업일 | 최근 30일 metric, budget 도달률, 저장량 | `docs/reports/` 월간 기록 |
| 50% budget 알림 | 서비스별 일간 증가와 반복 job 확인 | 운영 이슈 |
| 80% budget 알림 | 비필수 부하 검증 중단, listener/job/API 호출 점검 | 운영 이슈와 담당자 공유 |
| 100% budget 알림 | 추가 배포·부하 검증 보류, 원인과 다음 한도 결정 | 저장소 소유자 판단 기록 |

## Kakao Local

- Kakao Developers에서 Local 키워드 검색의 당일 사용량과 제공량을 확인한다.
- Core API 배포 또는 장소 검색 부하 검증 직후에는 쿼터와 429 오류를 함께 확인한다.
- `kakao_local_quota_exceeded`와 Core API 자체 `place_search_rate_limit_exceeded`를 구분한다.
- 일일 제공량이나 정책은 바뀔 수 있으므로 production 전 다시 확인한다.

## 대응 원칙

- budget 알림만으로 Cloud Run, Functions, Firestore를 자동 중단하지 않는다.
- Cloud Run 개발 서비스는 최소 인스턴스 0, 최대 인스턴스 1을 유지한다.
- scheduled Functions 급증 시 중복 배포, 재시도 조건, 대상 문서 상태를 먼저 확인한다.
- Firestore read 급증 시 관리자 전체 조회와 실시간 listener 해제 여부를 우선 확인한다.
- Storage 증가 시 10MB 업로드 제한, 고아 파일, 보존 기간을 확인한다.
- 알림 금액 변경에는 최근 2개월 사용량과 다음 테스트 계획을 함께 기록한다.

## 관련 기록

- [Issue 65 비용 모니터링 설정 기록](../reports/issue-65-cost-monitoring-2026-07-16.md)
- [인프라 운영 기준](infrastructure-operations-baseline.md)
- [Kakao Local Core API 경계](../architecture/kakao-local-core-api.md)
