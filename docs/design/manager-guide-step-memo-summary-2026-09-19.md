# 매니저 단계 메모 모아보기 설계

## 목적

동행 종료 직전 화면에서 매니저가 앞 단계에서 직접 저장한 메모 원문을 다시 읽을 수 있게 한다. 원문은 AI로 요약하거나 의료적으로 재해석하지 않으며, 이후 작성하는 최대 300자의 `managerJournal` 입력값으로 복사하거나 덮어쓰지 않는다.

## 현재 필드와 단계 대응

현재 서버 snapshot은 13단계이며, 내부 피드백의 step 2~7 번호를 그대로 사용하지 않는다.

| 화면 표시 | 서버 단계 | 현재 저장 필드 | 현재 제약 |
| --- | --- | --- | --- |
| Step 02 병원 이동 메모 | `HOSPITAL_ROUTE` | `locationSummary` | legacy 위치 기능이 꺼지면 응답에서 마스킹된다. |
| Step 03·06·12 보호자 공유 메모 최신본 | `RECEPTION_QUEUE`, `CONSULTATION_SUPPORT`, `CARE_COMPLETION` | `guardianUpdate` | 한 필드를 함께 써 이전 값은 새 저장값으로 교체된다. |
| Step 03~10 현장 확인 메모 공통 최신본 | `RECEPTION_QUEUE`, `VITALS_CHECK`, `PRE_CONSULTATION`, `CONSULTATION_SUPPORT`, `CONSULTATION_SUMMARY`, `PAYMENT_EVIDENCE`, `PRESCRIPTION_DOCUMENTS` | `fieldPhotoNote` | 한 필드를 함께 써 단계별 과거 원문은 현재 구조에 남지 않는다. |
| Step 11 복약 확인 메모 | `MEDICATION_CONFIRMATION` | `medicationNote` | 복약 관련 민감정보로 취급한다. |
| Step 11 약국 처리 메모 | `MEDICATION_CONFIRMATION` | `pharmacySummary` | 복약 관련 민감정보로 취급한다. |

`managerJournal`과 리포트의 진료·복약·다음 방문 입력은 마지막 화면에서 새로 작성하는 별도 값이므로 위 목록에 합치지 않는다. 첨부, 채팅, 환자 건강정보도 목록에 포함하지 않는다.

## 프론트엔드 표시 규칙

- `CARE_COMPLETION` 단계에서 `END_CARE` 요청을 보내기 전에만 읽기 전용 목록을 보여준다.
- `CARE_ENDED` 이후의 마지막 일지·리포트 재시도 화면에서는 서버가 원문을 마스킹하므로 목록을 숨긴다.
- 원문의 줄바꿈과 길이를 보존하고, 같은 원문이 여러 필드에 있으면 단계 제목을 합쳐 본문은 한 번만 표시한다.
- 비어 있는 필드는 `작성된 메모 없음`으로 표시한다.
- 목록은 화면 모델을 새로 만들 때마다 현재 세션 값으로 다시 조합하고 이전 세션의 로컬 값을 보관하지 않는다.

## 남은 서버 계약

Core API는 현재 `CARE_ENDED` 이후 매니저 응답에서 `guardianUpdate`, `locationSummary`, `fieldPhotoNote`, `medicationNote`, `pharmacySummary`를 모두 마스킹한다. 프론트엔드는 이 접근 제한을 캐시로 우회하지 않고 종료 직전에만 원문을 보여준다. 향후 종료 뒤 재진입에서도 원문을 보여주려면 다음 조건을 만족하는 별도 서버 계약이 필요하다.

- 배정된 매니저 본인이 작성한 필드만 허용한다.
- 세션·역할 검증 뒤 필요한 원문만 반환하고 채팅·첨부·환자 원문 접근은 다시 열지 않는다.
- 동의 철회, 보관 기한과 `CARE_ENDED` 이후 접근 회수 정책을 보안 검토로 확정한다.
- 단일 공통 필드의 과거 단계별 값을 복원할 수는 없으므로, 단계별 이력이 필요하면 `stepCode`별 append-only 기록 구조를 새로 설계한다.
