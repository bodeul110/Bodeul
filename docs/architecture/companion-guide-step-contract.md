# 동행 가이드 13개 화면과 단계 계약

기준일: 2026-08-22

상태: 구현 전 계약 초안. `stepCode`, 이벤트, 필수 입력과 신규 필드는 아직 PostgreSQL·Core API·Android에 적용되지 않았다.

## 검증 기준

- Figma `보들 가이드`의 `Page 2(460:2)`와 가이드 1~13 node는 PR #295 당시 마지막으로 실조회한 결과를 사용한다.
- Notion 화면별 상세 명세 v2.2는 2026-08-22 14:41 KST 편집본을 다시 확인한 저장소 정합성 기록을 사용한다.
- 이번 문서 작성에서는 Figma MCP Starter 호출 한도로 원본을 다시 열지 못했다. 따라서 node와 화면 내용은 마지막 검증 스냅샷이며 이후 변경이 없다고 단정하지 않는다.
- 구현 상태는 PostgreSQL Flyway V2·V5·V8·V10~V12, Spring Core API와 Android `master` 코드를 기준으로 다시 대조했다.

## 판단 기록

### 작업 목적

Figma의 13개 화면, PostgreSQL 병원 가이드의 데이터 기반 단계 수, Android의 7단계 공통 화면이 서로 다른 기준으로 진행되는 문제를 막는다.

### 선택한 방식

화면 번호와 표시 순서에서 분리된 안정적인 `stepCode`를 두고, 단계 완료 이벤트와 사용자 입력 계약을 별도 항목으로 관리한다. 진행 가능 여부는 Core API가 판단하고 Android는 서버가 준 단계 계약을 표시한다.

### 대안

- Figma의 화면 번호 1~13을 `current_step_order`에 고정할 수 있다.
- 기존 7단계 공통 가이드를 유지하고 13개 화면은 시안으로만 둘 수 있다.
- Android가 단계 제목을 해석해 화면과 필수 입력을 자체 결정할 수 있다.

### 선택 이유

현재 MVP 규모에서도 병원별 단계 수와 순서가 달라질 수 있다. 순번을 의미로 사용하면 중간 단계 추가만으로 진행 중 세션의 의미가 바뀌고, 앱과 서버가 서로 다른 완료 조건을 적용할 수 있다. 안정적인 코드와 서버 판정 경계를 두면 화면 순서를 바꾸거나 병원별 단계를 추가해도 같은 업무 의미를 유지할 수 있다.

### 리스크

- 실행 중인 세션에 가이드 버전이나 단계 스냅샷이 없어서 관리자 가이드 수정이 과거 세션의 총 단계 수를 바꿀 수 있다.
- 결제 증빙, 처방 이미지와 매니저 일지는 필수 여부·파일 제한·보관 정책이 미정이다.
- `CARE_ENDED`와 최종 `COMPLETED`를 나누려면 DB, API, Android 상태 전이를 함께 변경해야 한다.

## 계약 원칙

| 항목 | 의미 | 규칙 |
| --- | --- | --- |
| `stepCode` | 단계의 업무 의미 | 대문자 스네이크 케이스를 사용하고 한번 배포한 의미를 재사용하지 않는다. 화면 번호나 배열 위치를 코드에 넣지 않는다. |
| `order` | 가이드 안의 표시·진행 순서 | 같은 가이드 버전 안에서 1부터 시작하는 고유 양수다. 업무 의미 식별자로 사용하지 않는다. |
| `eventCode` | 서버가 확정한 상태 변화 | 상태 변경과 멱등 이벤트 레코드는 같은 DB 트랜잭션에서 기록한다. Realtime·FCM 같은 외부 발행만 커밋 성공 뒤 처리한다. 알림 범위는 #299가 확정하기 전 임의로 확대하지 않는다. |
| `inputContract` | 단계에서 받는 값과 검증 | 형식·최대값은 기술 계약에 둘 수 있지만 필수·건너뛰기 여부는 #307 결정을 따른다. |
| `guideVersion` | 세션에 고정된 가이드 개정 | 새 세션 생성 시 고정한다. 진행 중 세션은 최신 관리자 가이드로 자동 치환하지 않는다. 현재는 미구현이다. |

`MATCHED`는 가이드 1의 완료 이벤트가 아니라 동행 가이드 진입 전 예약 배정 이벤트다.

## 13개 화면 목표 계약

아래 `stepCode`와 이벤트는 후속 migration·Core API·Android 구현을 나누기 위한 이름 초안이다. 표에 `후보`로 표시된 이벤트는 현재 서버에서 생성되지 않는다.

| # | Figma node | `stepCode` 초안 | 화면 목적 | 사용자 입력과 CTA | 완료 이벤트 후보 |
| ---: | --- | --- | --- | --- | --- |
| 1 | `846:1056` | `MEETING_CONFIRMATION` | 매니저와 환자의 상봉을 확인하고 동행 시작 시점을 남긴다. | 별도 입력 없음. `상봉 완료` 시각을 서버가 기록한다. | `MEETING_CONFIRMED` |
| 2 | `941:549` | `HOSPITAL_ROUTE` | `혜화역 3번 출구 → 병원 로비 → 신경과` 이동을 한 화면에서 안내한다. | Route 1의 `로비 도착`, Route 2의 `진료과 도착`. 로비 도착만으로 다음 단계로 진행하지 않는다. | `ARRIVED_LOBBY`, `ARRIVED_DEPARTMENT` |
| 3 | `846:1309` | `RECEPTION_QUEUE` | 접수 상태와 대기번호를 기록해 보호자 진행 화면에 공유한다. | 영문·숫자 혼합 대기번호 입력, `대기번호 저장` 또는 다음 단계 CTA. | `QUEUE_UPDATED` |
| 4 | `846:1384` | `VITALS_CHECK` | 병원에서 확인한 기초 측정값을 기록한다. | 측정값 입력과 다음 단계 CTA. 측정값이 없어도 진행 가능한 목표이며 필수·건너뛰기 표현은 #307에서 확정한다. | `VITALS_RECORDED` 후보 |
| 5 | `846:1521` | `PRE_CONSULTATION` | 증상, 질문과 전달 사항을 진료 전에 다시 확인한다. | 확인 또는 메모 보완 후 `진료 준비 완료`. 전용 서버 입력 계약은 미정이다. | 별도 이벤트 없음 |
| 6 | `846:2386` | `CONSULTATION_SUPPORT` | 진료 중 핵심 안내와 결과를 현장 기록으로 남긴다. | 진료 메모 입력 후 `진료 완료`. 녹음·STT·AI 요약은 현재 범위가 아니다. | `CONSULT_COMPLETED` |
| 7 | `846:1653` | `CONSULTATION_SUMMARY` | 진료 요약을 검토하고 수납 전 공유 내용을 확정한다. | 진료 요약 확인·수정 후 `요약 저장`. 최종 리포트 완료와는 구분한다. | `CONSULT_SUMMARY_READY` |
| 8 | `846:1737` | `PAYMENT_EVIDENCE` | 수납 완료와 결제 증빙을 기록한다. | 결제 증빙 업로드와 `수납 완료`. 최소 장수·파일 형식·용량·교체·보관은 #307에서 확정한다. | `PAYMENT_COMPLETED` |
| 9 | `846:1819` | `PHARMACY_ROUTE` | 처방전 기준 약국 이동을 돕는다. | `카카오맵에서 약국 찾기` 외부 이동. 서버 검색과 딥링크의 최종 조합은 #314에서 정리한다. | 별도 이벤트 없음 |
| 10 | `846:2507` | `PRESCRIPTION_DOCUMENTS` | 처방 관련 이미지 자료를 등록한다. | 최대 3장 업로드 후 `저장`. 최소 1장 여부와 파일 정책은 #307에서 확정한다. | 별도 이벤트 없음 |
| 11 | `846:1916` | `MEDICATION_CONFIRMATION` | 처방전, 약 수령과 복약 안내 완료 여부를 확인한다. | 처방전 수령·약국 완료·복약 안내 상태와 메모를 저장한다. | 별도 이벤트 없음 |
| 12 | `857:7252` | `CARE_COMPLETION` | 환자 상태와 인계 내용을 최종 확인하고 실제 동행을 종료한다. | `동행 종료` 시 `careEndedAt` 기록. 최종 일지 작성 전 재진입 규칙은 #307에서 확정한다. | `CARE_ENDED` |
| 13 | `846:2576` | `MANAGER_JOURNAL` | 최대 300자 매니저 일지를 작성하고 최종 완료·후기 진입으로 연결한다. | 일지 입력 후 `작성 완료`. 빈 값 허용 여부는 #307에서 확정한다. | `REPORT_READY`와 세션 `COMPLETED`를 같은 커밋에서 확정 |

## 현재 구현 차이

공통으로 PostgreSQL `companion_sessions`에는 `current_step_order`, `current_status`, `completed_at`이 있고, Core API는 `hospital_guides.steps` 배열 길이로만 `totalStepCount`를 계산한다. 단계 상태는 현재 1=`MEETING`, 2=`WAITING`, 3~4=`IN_TREATMENT`, 5 이상=`PAYMENT`로 압축된다. Android Core 경로는 상세 단계 배열을 받지 않고 모든 세션에 7단계 `HospitalGuideFallbackFactory`를 생성한다. 공통 ScrollView에는 위치·보호자·현장·복약·리포트 입력이 단계와 관계없이 함께 노출된다.

| # | PostgreSQL 현재 값 | Core API 현재 동작 | Android 현재 동작 | 필요한 후속 계약 |
| ---: | --- | --- | --- | --- |
| 1 | 첫 advance 때 기록하는 `started_at`은 있으나 상봉 완료 전용 시각·이벤트 없음 | 범용 `/advance`만 제공 | 7단계의 `환자 접촉`으로 표시 | 상봉 커밋 시각과 `MEETING_CONFIRMED` 정의 |
| 2 | `location_summary`와 위치 이력은 있으나 Route·checkpoint 없음 | 위치 기록과 범용 메모만 제공 | 지도·외부 카카오맵은 있으나 2개 Route 진행 상태 없음 | 로비·진료과 checkpoint와 다음 단계 제한 |
| 3 | 전용 대기번호 필드 없음 | `guardian_update`에 자유 메모만 가능 | 대기번호 형식·저장 UI 없음 | 문자열 대기번호와 보호자 표시 계약 |
| 4 | 구조화된 기초 측정 필드 없음 | 범용 메모 외 검증 없음 | 단계별 측정 폼 없음 | 측정 항목·단위와 선택 입력 계약 |
| 5 | 전용 문진 준비 필드 없음 | 범용 메모만 가능 | 공통 가이드 설명만 표시 | 확인 상태와 선택 메모 범위 |
| 6 | `guardian_update`, `field_photo_note`만 있음 | PATCH 메모는 가능하나 진료 완료 이벤트 없음 | 공통 메모 입력을 제공 | 진료 완료와 요약 작성 시작 경계 |
| 7 | `session_reports.summary`, `treatment_notes`가 있음 | 리포트 PUT이 세션 최종 완료까지 함께 처리 | 요약 입력은 있으나 중간 확정 단계와 분리되지 않음 | 중간 요약 저장과 최종 완료 분리 |
| 8 | `PAYMENT` 상태 외 결제 증빙 전용 행·경로 없음 | 전용 upload·metadata API 없음 | 전용 증빙 업로드 화면 없음 | 용도별 Storage 경로·인가·파기와 metadata |
| 9 | `pharmacy_summary`, `pharmacy_completed`가 있음 | Kakao Local 검색은 다른 검색 흐름에도 사용 | 외부 카카오맵 fallback과 내장 검색 경로가 공존 | #314의 딥링크·서버 검색 책임 분리 |
| 10 | 처방 이미지 전용 행·경로 없음. 채팅 첨부는 단계와 연결되지 않음 | 전용 다건 업로드·장수 검증 없음 | 전용 최대 3장 등록 화면 없음 | 처방 자료 Storage·metadata·교체·파기 |
| 11 | `prescription_collected`, `pharmacy_completed`, `medication_guidance_completed`, 메모 필드가 있음 | PATCH로 상태와 메모 저장 가능 | 공통 가이드 화면에서 각 상태를 수정 가능 | stepCode에 따른 노출과 완료 판정 연결 |
| 12 | `completed_at`만 있고 `care_ended_at`, `CARE_ENDED` 없음 | 리포트 PUT 성공 때 바로 `COMPLETED` 처리 | 동행 종료와 최종 일지 완료를 구분하지 않음 | 종료 시각·상태·재진입의 원자적 전이 |
| 13 | 리포트 필드는 있으나 `manager_journal`, `journal_written_at` 없음 | 300자 검증과 일지 전용 저장 없음 | 전용 일지 화면·길이 검증 없음 | 일지 저장 뒤 최종 완료와 후기 진입 |

채팅 첨부의 이미지·PDF 최대 10 MiB, 메시지당 3개 정책을 결제 증빙이나 처방 이미지 정책으로 자동 재사용하지 않는다. 용도, 열람자와 보관 기간이 다르므로 #307 결정 뒤 별도 계약으로 연결한다.

현재 PostgreSQL에는 제품 단계 이벤트 테이블이 없다. Realtime은 `chat.changed`, `read-receipt.changed`, `location.changed` 갱신 신호만 발행하고, FCM은 직접 채팅과 병원·약국 근접 위치 알림만 처리한다. `MATCHED`, `MEETING_CONFIRMED`, `ARRIVED_*`, `QUEUE_UPDATED`, `CARE_ENDED`, `REPORT_READY`를 구현된 이벤트로 간주하지 않는다.

현재 리포트 제출은 마지막 단계 도달 여부를 확인하지 않고 유효한 매니저·버전·요약이면 세션과 예약을 `COMPLETED`로 바꿀 수 있다. 단계 계약을 추가할 때 최종 완료의 서버 전제 조건도 함께 고정해야 한다.

## 목표 API 최소 응답

Core API는 Android가 제목을 해석해 화면을 선택하지 않도록 최소한 아래 의미를 제공해야 한다. 실제 JSON 이름은 후속 Core API 이슈에서 확정한다.

| 값 | 목적 |
| --- | --- |
| `guideId`, `guideVersion` | 세션에 고정된 병원 가이드 식별과 개정 |
| `steps[].code`, `steps[].order` | 안정적인 업무 의미와 표시 순서 분리 |
| `steps[].title`, `steps[].description` | 병원별 안내 표시 |
| `steps[].inputContract` | 입력 종류, 형식, 최대값과 정책 확정 뒤 필수 여부 |
| `currentStepCode`, `currentStepOrder` | 현재 의미와 순서의 일치 검증 |
| `canAdvance`, `blockedReason` | 서버가 판정한 진행 가능 여부와 사용자 안내 |
| `completedEvents[]` | checkpoint와 중복 요청 복구를 위한 커밋 완료 상태 |

Android는 `canAdvance=false`를 우회해 순서를 올리지 않고, 서버에 없는 이벤트를 로컬 완료로 간주하지 않는다.

## 단계 수와 알 수 없는 코드 처리

| 입력 상태 | 현재 동작 | 목표 처리 |
| --- | --- | --- |
| 가이드 없음 또는 0단계 | Core API `totalStepCount=0`이라 advance를 거부하지만 Android는 활성 `다음`이 있는 7단계 공통 가이드를 표시해 서버 오류가 난다. | 신규 세션은 진행을 막고 `가이드 준비 중` 상태를 표시한다. 기존 legacy 세션만 고정된 7단계 snapshot을 사용한다. |
| 1단계 | 서버는 1을 마지막 단계로 보지만 Android는 7단계를 표시하고 `다음`을 보내 서버 오류가 난다. | 한 단계의 전용 CTA와 종료·리포트 전이를 서버 응답으로 제공한다. |
| 7단계 | Core API 경로는 DB 상세를 무시하고 Android 공통 fallback을 표시한다. Firebase·과거 PostgreSQL seed의 제목·설명은 이 fallback과 서로 다르며 안정적인 코드는 없다. | 진행 중 legacy 세션은 생성 당시 클라이언트가 실제 표시한 7단계를 우선 보존한다. 원본을 판별할 수 없으면 Core API 세션은 Android fallback을 `LEGACY_CORE_7_V1`, Firebase 세션은 저장된 가이드 배열을 `LEGACY_FIREBASE_7_V1`로 고정하고 새 13단계로 자동 변환하지 않는다. |
| 13단계 | 서버는 배열 길이를 진행 한계로 쓰지만 Android는 7에서 버튼을 비활성화해 8~13으로 정상 진행할 수 없다. | 코드가 포함된 13개 상세 단계를 내려주고 화면 registry로 매핑한다. |
| 13단계 초과 | 서버는 배열 길이만큼 진행할 수 있으나 Android는 7 이후 단계를 표시하지 않는다. | 목록을 자르지 않는다. 알려진 코드는 전용 화면, 추가 코드는 일반 제목·설명 화면으로 표시하며 `canAdvance`를 따른다. |
| 알 수 없는 `stepCode` 또는 순서 | 현재 코드 필드가 없고 현재 순번에 맞는 Android 단계가 없으면 마지막 항목을 포커스로 사용한다. | 일반 단계 화면으로 제목·설명을 보존하고 코드 전용 입력은 숨긴다. 서버가 `canAdvance=true`로 응답한 경우에만 다음 단계로 진행한다. |

## 진행 중 세션 호환 원칙

1. 세션 생성 시 `guideId`, `guideVersion`과 단계 snapshot을 고정한다.
2. 관리자 가이드 수정은 새 세션부터 적용하고 `IN_PROGRESS` 세션의 순서·의미·총 단계를 바꾸지 않는다.
3. 기존 7단계 세션은 완료될 때까지 생성 당시 실제 표시 기준을 legacy snapshot으로 유지한다. Core API 경로는 Android fallback, Firebase 경로는 세션에서 참조하던 저장 가이드 배열을 우선하며, 출처를 판별할 수 없는 경우 운영 확인 대상으로 분리한다. 순번이나 제목 유사도만 보고 13개 코드로 추정 변환하지 않는다.
4. `currentStepOrder`와 `currentStepCode`가 불일치하면 자동 보정하지 않고 진행을 막아 운영 확인 대상으로 보낸다.
5. 동일 이벤트 재요청은 세션·단계·이벤트 기준으로 멱등 처리한다.
6. `CARE_ENDED` 이후 최종 작성 화면 재진입, 수정 허용과 타임아웃은 #307 결정 전 구현값으로 고정하지 않는다.

## 미결 정책

| 이슈 | 결정 전 확정하지 않을 값 |
| --- | --- |
| #307 | 가이드 4 측정값, 가이드 8 결제 증빙, 가이드 10 처방 이미지, 가이드 13 일지의 필수·건너뛰기·파일 제한·완료 시점 |
| #299 | `MATCHED`, 상봉, 단계 진행, 동행 종료와 최종 완료 중 어떤 이벤트를 누구에게 FCM·앱 내 알림으로 보낼지 |
| #314 | 가이드 9의 Kakao 앱 딥링크와 Core API Kakao Local 검색을 어떤 화면에서 각각 사용할지 |

## 후속 구현 분리 기준

Parent #301 아래에서 다음 세 범위로 나누면 같은 계약을 여러 이슈에서 다시 정의하지 않는다.

| 범위 | 포함할 변경 | 완료 증거 |
| --- | --- | --- |
| PostgreSQL migration | 코드 포함 가이드 schema 검증, 세션 guide version·snapshot, 이벤트 멱등성, `care_ended_at`, 정책 확정 뒤 전용 증빙·일지 필드 | migration 연속 적용·rollback, 기존 7단계 row 보존, 권한 테스트 |
| Core API | 상세 가이드 응답, `currentStepCode`, `canAdvance`, checkpoint·완료 전이, version 충돌과 멱등 요청 | 서비스·repository·HTTP 계약 테스트, 역할별 200·403·409 검증 |
| Android | stepCode 화면 registry, 일반 fallback 화면, 13개 전용 UI와 입력, process death·재연결 복구 | 단위·UI 테스트, 0·1·7·13·13초과·unknown fixture, 실기기 진행·재진입 검증 |

## 이번 문서에서 하지 않은 일

- PostgreSQL migration, API DTO, Android 화면과 상태 전이는 변경하지 않았다.
- `stepCode` 초안을 운영 데이터에 seed하지 않았다.
- 미확정 입력을 필수로 표시하거나 새로운 보호자 알림을 발송하지 않았다.
- Figma 원본을 이번 작업에서 최신으로 재검증했다고 표시하지 않았다.

## 관련 문서와 이슈

- [Figma 현행 화면 지도](../design/figma-current-screen-map.md)
- [Notion 제품 기준 정합성](../planning/notion-product-alignment.md)
- [매칭·동행·리포트 PostgreSQL 전환 계약](companion-session-core-api.md)
- [화면 재구성 목표](../planning/screen-restructure-target.md)
- #301 동행 가이드 1~13 화면과 DB 단계 이벤트 정규화
- #306 Figma 동행 가이드 13개 화면과 단계 계약표 작성
- #307 동행 단계 필수 입력·건너뛰기·완료 시점 확정
- #299 알림 이벤트 정책
- #314 가이드 9 Kakao 지도 경로 정렬
