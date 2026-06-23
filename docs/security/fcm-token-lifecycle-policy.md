# FCM 토큰 수명주기 정책

기준일: 2026-06-19

이 문서는 이용자 문의 답변 푸시와 관련된 FCM 토큰 저장, 정리, 재알림 기준을 고정한다.

## 목적

- 무효 토큰이 계속 남아 반복 발송되는 상황을 줄인다.
- 장기 미사용 기기에 남은 오래된 토큰을 자동으로 정리한다.
- 앱, Firebase Functions, 운영 문서가 같은 기준을 보도록 맞춘다.

## 현재 저장 구조

사용자 문서 `users/{uid}`에는 아래 필드를 사용한다.

- `notificationTokens`
  - 실제 발송에 사용하는 토큰 배열
- `notificationTokenUpdatedAt`
  - 마지막 토큰 동기화 시각
- `notificationTokenPlatform`
  - 현재 플랫폼 기록
- `notificationTokenEntries`
  - 토큰별 메타데이터 맵
  - 각 엔트리는 아래 구조를 사용한다.
    - `token`
    - `platform`
    - `updatedAtMillis`

앱 쪽 구현:
- [FirebaseNotificationTokenRegistrar.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseNotificationTokenRegistrar.java)

서버 쪽 구현:
- [functions/index.js](../../functions/index.js)

## 동기화 기준

- 로그인 후 현재 기기 토큰을 동기화한다.
- 앱 시작 시 기존 세션이 있으면 현재 기기 토큰을 다시 동기화한다.
- 로그아웃 시 현재 기기 토큰을 `notificationTokens`와 `notificationTokenEntries`에서 함께 제거한다.

## 무효 토큰 정리 기준

아래 오류 코드는 무효 토큰으로 판단한다.

- `messaging/invalid-registration-token`
- `messaging/registration-token-not-registered`

정리 시 동작:
- `notificationTokens`에서 제거
- 대응하는 `notificationTokenEntries.*` 메타데이터도 함께 제거

적용 함수:
- `notifyClientSupportAnswered`
- `sendClientSupportAnswerReminders`

## 장기 미사용 토큰 정리 기준

스케줄 함수:
- `cleanupStaleNotificationTokens`

실행 주기:
- 매일 `04:30` KST

정리 기준:
- 마지막 갱신 시각이 `60일` 이상 지난 토큰 제거
- `notificationTokens`에는 없는데 `notificationTokenEntries`에만 남은 고아 메타데이터 제거

판단 기준 우선순위:
1. `notificationTokenEntries.{token}.updatedAtMillis`
2. 없으면 `notificationTokenUpdatedAt`

토큰이 전부 제거되면 같이 정리하는 필드:
- `notificationTokenUpdatedAt`
- `notificationTokenPlatform`

## 문의 답변 재알림 기준

스케줄 함수:
- `sendClientSupportAnswerReminders`

기준:
- 답변 후 `24시간`이 지나도 이용자가 읽지 않은 문의만 대상
- `24시간` 간격
- 최대 `3회`

관련 상태 필드:
- `responseReadByUser`
- `responseReadAt`
- `responseReminderCount`
- `responseReminderSentAt`

## 운영 기준

- 토큰 배열 구조는 유지한다.
  - 이유: 현재 발송 함수와 앱 동기화 경로를 크게 흔들지 않기 위해서다.
- 새 정책은 메타데이터를 덧붙이는 방식으로만 확장한다.
  - 이유: 기존 사용자 문서와 호환성을 유지하기 위해서다.
- 장기 미사용 기준 `60일`은 운영 기준값이다.
  - 더 짧게 바꾸면 재설치/장기 미접속 사용자의 복귀 시 푸시 누락 가능성이 커진다.
  - 더 길게 바꾸면 무효 토큰 누적이 커진다.

## 점검 포인트

- Functions 배포 후 아래 함수를 같이 확인한다.
  - `notifyClientSupportAnswered`
  - `sendClientSupportAnswerReminders`
  - `cleanupStaleNotificationTokens`
- 장기 미사용 토큰 정리 기준을 바꾸면 이 문서와 구현 기록을 같이 갱신한다.
- 향후 기기 단위 식별자나 사용자별 토큰 상한을 도입하면 이 문서를 먼저 갱신한다.
