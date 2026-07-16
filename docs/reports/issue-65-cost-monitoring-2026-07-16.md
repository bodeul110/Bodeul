# Issue 65 비용 모니터링 설정 기록

기준일: 2026-07-16

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

`bodeul-dev`의 Firebase/Google Cloud 비용을 조기에 감지하고, Firestore·Storage·Functions·Cloud Run과 Kakao Local 쿼터를 같은 운영 주기로 확인할 기준을 만든다.

## 선택한 방식

- `billingbudgets.googleapis.com` API를 `bodeul-dev`에 활성화했다.
- `bodeul-dev`만 포함하는 월 10,000 KRW budget을 생성했다.
- 현재 지출 50%, 80%, 100%에서 기본 IAM 수신자에게 알림을 보낸다.
- Cloud Monitoring의 실제 metric descriptor를 조회해 월간 점검 metric을 고정했다.
- budget 대응 절차와 점검 주기는 [비용과 쿼터 모니터링](../operations/cost-monitoring.md)에 둔다.

## 대안

- budget을 결제 계정 전체에 적용한다.
- 첫 budget을 50,000 KRW 이상으로 설정한다.
- 알림 없이 Firebase 무료 할당량만 수동 확인한다.
- 100% 도달 시 서비스를 자동 중단한다.

## 선택 이유

현재 MVP 개발 규모에서는 프로젝트별 낮은 경보가 전체 결제 계정 budget보다 원인 추적이 쉽다. 10,000 KRW는 지출 상한이 아니라 초기 이상 감지선이며, Cloud Run 최대 인스턴스 1과 Functions/Firestore metric을 함께 확인해야 실제 원인을 판단할 수 있다. 자동 중단은 인증·알림·API까지 동시에 끊을 수 있어 적용하지 않았다.

## 설정 결과

| 항목 | 결과 |
| --- | --- |
| 프로젝트 결제 연결 | 활성 |
| 결제 통화 | KRW |
| Billing Budget API | 활성 |
| 기존 budget | 생성 전 0개 |
| 생성 budget | `BoDeul dev monthly budget` |
| 프로젝트 필터 | `bodeul-dev`만 포함 |
| 금액/기간 | 월 10,000 KRW |
| 임계값 | 현재 지출 50%, 80%, 100% |
| 기본 IAM 메일 | 활성 |
| 생성 후 동일 이름 budget | 1개 |

결제 계정 ID와 개인 메일 주소는 공개 문서에 기록하지 않았다.

## 30일 사용량 기준선

Cloud Monitoring API를 2026-07-16에 조회한 결과다. 비용 청구서가 아니라 사용량 추세 기준이며, 제품별 집계 지연이 있을 수 있다.

| 영역 | 최근 30일 |
| --- | ---: |
| Firestore 문서 read | 8,980 |
| Firestore 문서 write | 262 |
| Firestore 문서 delete | 1 |
| Cloud Run 요청 | 838 |
| Cloud Functions 실행 | 780 |
| Cloud Storage API 요청 | 27 |
| Firestore data/index 저장량 최대 | 약 0.43 MiB |
| Cloud Storage 저장량 최대 | 약 1.30 MiB |

Kakao Local은 같은 날 [Issue 158 검증](issue-158-kakao-local-core-api-2026-07-16.md)에서 키워드 검색 1건, 일일 제공량 100,000건을 확인했다.

## 검증

- `gcloud billing projects describe bodeul-dev`: 결제 활성 확인
- `gcloud billing budgets list`: 동일 이름 budget 1개 확인
- budget 금액, 월간 기간, project number 필터, 임계값 3개 재조회
- Cloud Monitoring metric descriptor와 최근 30일 time series 조회
- 결제 계정 ID, access token, 개인 이메일을 저장소에 기록하지 않음

## 리스크와 남은 범위

- budget은 비용을 차단하지 않으므로 알림 수신 후 사람이 대응해야 한다.
- 기본 알림은 결제 계정 IAM 역할 보유자에게만 전송된다. 팀 공용 채널이 필요하면 별도 Monitoring notification channel 또는 Pub/Sub 연동을 검토한다.
- GCP budget에는 Supabase와 Vercel 비용이 포함되지 않는다. 각 서비스가 유료 전환되면 별도 한도와 알림이 필요하다.
- 실제 월 청구액은 Cloud Billing Report에서 확인하며, 이 문서의 metric 합계와 같다고 보지 않는다.

## 판단

Issue #65의 개발 환경 범위인 GCP budget, 주요 사용량 기준선, Kakao 쿼터 점검 주기와 알림 대응 절차를 구성했다. production 프로젝트를 만들 때는 같은 구조를 복사하지 말고 production 예상 트래픽과 승인자를 기준으로 별도 budget을 설정한다.
