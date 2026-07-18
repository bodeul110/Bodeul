# Issue 221 채팅·위치 Core API와 Broadcast 2단계

기준일: 2026-07-18

## 작업 목적

Firestore 배열과 좌표에 직접 쓰던 채팅·위치 경로를 PostgreSQL 단일 원본으로 옮길 수 있도록 인증된 Core API 명령·조회 계약과 커밋 결과 알림을 구현한다.

## 선택한 방식

- 참여자는 `GET /api/companion-sessions/{id}/realtime`에서 최근 메시지 100건, 읽음 위치와 진행 중 위치 10건을 다시 조회한다.
- 환자·보호자·배정 매니저는 `POST /messages`로 메시지를 저장하고, `PUT /read-receipt`로 마지막 읽은 메시지를 갱신한다.
- 위치는 배정 매니저만 `POST /locations`로 기록한다.
- 메시지와 위치는 클라이언트 UUID를 받아 네트워크 재시도의 중복 저장을 막는다.
- PostgreSQL V10 trigger가 메시지·읽음·위치 변경을 `companion-session:{sessionId}` private Broadcast 주제로 보낸다.
- Broadcast payload에는 `sessionId`, `resource`, `recordId`만 넣고 채팅 본문, 좌표, Storage 경로는 넣지 않는다.

## 대안과 선택 이유

Spring이 Supabase Realtime REST API를 호출하는 방식은 Cloud Run에 별도 서비스 키와 외부 네트워크 실패 처리를 추가해야 한다. 현재 규모에서는 DB trigger가 같은 트랜잭션에서 `realtime.send`를 기록하도록 하는 편이 비밀값과 운영 지점이 적다. Realtime 메시지는 커밋 뒤 구독자에게 보이며, 클라이언트는 알림을 상태 원본으로 사용하지 않고 Core API snapshot을 다시 조회한다.

Realtime 확장이 없는 PostgreSQL이나 일시적인 전송 오류 때문에 업무 저장이 실패해서는 안 된다. V10은 함수 존재를 확인하고, 전송 예외는 민감정보 없는 경고만 남긴 뒤 본 트랜잭션을 유지한다.

## API와 검증 경계

| API | 권한 | 서버 검증 |
| --- | --- | --- |
| `GET /api/companion-sessions/{id}/realtime` | 환자·보호자·배정 매니저 | 참여 관계, 완료·취소 세션 좌표 비노출 |
| `POST /api/companion-sessions/{id}/messages` | 환자·보호자·배정 매니저 | 진행 상태, 본문 2,000자, 첨부 최대 3개, 경로·MIME·10 MiB, 재시도 UUID |
| `PUT /api/companion-sessions/{id}/read-receipt` | 환자·보호자·배정 매니저 | 같은 세션 메시지, 읽음 위치 역행 방지 |
| `POST /api/companion-sessions/{id}/locations` | 배정 매니저 | 진행 상태, 좌표 범위, 수집 시각, 재시도 UUID |

첨부 원본은 기존 Firebase Storage의 `companion-chat-attachments/{sessionId}/...`에 두며 Core API는 해당 세션 prefix와 메타데이터를 다시 확인한다. 완료·취소 세션에서는 새 메시지와 위치를 받지 않고 snapshot에서도 정밀 위치를 반환하지 않는다.

## 검증

1. `DefaultCompanionRealtimeServiceTests`에서 참여 관계, 종료 세션 위치 비노출, 첨부 경로, 메시지 재시도 충돌과 매니저 위치 권한을 검증했다.
2. `CompanionRealtimeApiIntegrationTests`에서 Firebase 인증, `no-store`, JSON 요청 매핑과 오류 계약을 검증했다.
3. `CompanionRealtimeBroadcastMigrationContractTests`에서 private 주제, 최소 payload, 세 trigger와 rollback을 검증했다.
4. `core-api check` 전체 검증을 통과했다.
5. PostgreSQL 17 임시 인스턴스에서 bootstrap과 V1~V10을 연속 적용했다.
6. 같은 메시지 UUID 재전송은 0건 추가, 읽음 위치의 이전 메시지 갱신은 0건, 위치 12건 기록 뒤 최근 10건 유지를 확인했다.
7. `anon`·`authenticated` 조회, Admin 메시지 쓰기와 Core의 위치 table 직접 INSERT가 모두 거부됨을 확인했다.
8. Supabase Realtime 확장이 없는 PostgreSQL에서도 본 쓰기가 성공했고 V10 rollback 뒤 trigger와 함수가 모두 제거됨을 확인했다.

## 남은 범위

- 개발 Supabase에 privileged publisher와 V11을 적용하고 실제 `realtime.messages` private Broadcast 행을 확인한다.
- Firebase Third-Party Auth와 세션 참여 관계를 확인하는 private channel RLS를 적용한다.
- Android를 새 API와 private Broadcast 구독으로 전환하고 재연결 시 snapshot 재조회, 백그라운드 FCM을 검증한다.
- 통합 검증 뒤 Firestore 채팅·위치·읽음 쓰기를 차단한다.
- #222에서 만료 행과 Firebase Storage 첨부를 정리하는 일일 파기 job을 구현한다.

## 리스크

- V10·V11을 적용해도 private 채널 RLS를 적용하지 않으면 클라이언트 구독 경로는 아직 완성되지 않는다.
- Broadcast 실패는 본 저장을 막지 않으므로 클라이언트의 재연결·주기적 snapshot 복구가 필수다.
- Android 전환 전에는 기존 Firestore 경로가 실제 화면에 남아 있으므로 두 경로를 동시에 쓰지 않는다.
- production 적용은 개발 환경의 Firebase JWT·RLS·실기기 통합 검증 뒤 별도 승인으로 진행한다.

## 개발 DB V10 적용 중 발견 사항

- migration run `29646403916`에서 V10과 세 trigger 적용은 성공했다.
- 첫 실전 발행에서는 본 PostgreSQL 쓰기는 유지됐지만 Broadcast 행이 0건이었다.
- 첫 확인에서는 함수 owner인 `bodeul_migration`에 managed `realtime` schema `USAGE`가 없었다. 이를 보완해도 `realtime.send`가 security invoker라 managed table INSERT와 RLS 우회가 추가로 필요해 발행은 계속 0건이었다.
- migration 역할에 `realtime.messages` 권한이나 `BYPASSRLS`를 주지 않는다. privileged bootstrap이 `postgres` 소유의 검증 wrapper를 만들고 `bodeul_migration`에는 그 함수 실행만 허용한다.
- wrapper는 세 가지 이벤트·리소스 조합, UUID topic과 `sessionId` 일치, `sessionId`·`resource`·`recordId` 외 payload 키 금지를 강제한다.
- V11은 기존 trigger가 `realtime.send`를 직접 부르지 않고 wrapper를 사용하도록 교체한다.
- wrapper와 V11 적용 뒤 실제 private Broadcast 3종과 검증 데이터 잔여 0건을 다시 확인하기 전까지 운영 검증을 완료로 표시하지 않는다.
