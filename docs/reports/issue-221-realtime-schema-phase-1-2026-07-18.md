# Issue 221 채팅·위치 PostgreSQL 스키마 1단계

기준일: 2026-07-18

## 작업 목적

Firestore 세션 문서의 배열과 좌표 필드에 결합된 채팅·위치를 PostgreSQL 단일 원본과 Supabase Realtime 알림 구조로 옮길 수 있도록 서버 전용 저장 계약을 먼저 확정한다.

## 선택한 방식

- `companion_chat_messages`, `companion_chat_attachments`, `companion_chat_read_receipts`, `companion_session_locations`를 정규화한다.
- 채팅 재시도는 세션과 `client_message_id` 조합으로 중복을 제거한다.
- 위치 쓰기는 table INSERT가 아니라 `record_companion_location` 함수만 허용한다.
- 세션별 위치는 최근 10건만 유지하고, 15분보다 오래됐거나 5분보다 미래인 수집 시각은 거부한다.
- 세션 완료·취소 trigger가 채팅 180일, 첨부 30일, 위치 24시간의 `expires_at`을 예약한다.
- Firebase Storage는 첨부 원본을 계속 담당하고 PostgreSQL은 서버 인가와 파기를 위한 메타데이터를 소유한다.

## 대안과 선택 이유

Firestore 배열을 계속 사용하면 메시지 단위 중복 제거, 읽음 위치 참조 무결성, 보관기한별 파기와 관계 조회를 안정적으로 적용하기 어렵다. 클라이언트의 Supabase Data API 직접 쓰기는 서버 인증·참여 관계 검증 경계를 우회하므로 채택하지 않았다.

현재 규모에서는 별도 WebSocket 서버와 Redis를 운영하기보다 PostgreSQL 커밋을 권위 있는 결과로 두고 Supabase Realtime private Broadcast를 화면 갱신 신호로 사용하는 편이 운영 부담이 낮다. V8은 영속 계층만 준비하며 Broadcast 누락이나 재연결 뒤에는 Core API를 다시 조회한다.

## 권한과 보관 경계

| 주체 | 허용 범위 |
| --- | --- |
| `bodeul_core_runtime` | 실시간 테이블 조회, 채팅·첨부·읽음 지정 컬럼 쓰기, 위치 기록 함수 실행 |
| `bodeul_admin_runtime` | 운영 확인을 위한 조회 |
| `anon`, `authenticated`, `service_role`, `public` | 업무 테이블과 서버 전용 함수 접근 없음 |
| `bodeul_migration` | DDL, 보관 trigger와 함수 소유 |

legal hold가 유효한 위치 행은 최근 10건 정리에서 제외한다. V8은 만료 시각만 예약하며 실제 DB·Storage 삭제와 결과 감사는 #222에서 구현한다.

## 검증

1. `CompanionRealtimeMigrationContractTests`로 테이블, 제약, 최소 권한, 보관 trigger와 rollback 순서를 확인했다.
2. PostgreSQL 17 `postgres:17` 임시 인스턴스에서 권한 bootstrap과 V1~V8을 연속 적용했다.
3. Core runtime의 채팅·읽음·위치 쓰기는 성공했고, `authenticated`의 채팅 조회와 Admin runtime의 채팅 쓰기는 거부됐다.
4. 위치 11건 기록 뒤 10건만 남고 같은 클라이언트 위치 UUID 재전송 시 행 수가 늘지 않음을 확인했다.
5. 세션 완료 뒤 채팅 만료가 180일, 위치 만료가 24시간으로 예약됨을 확인했다.
6. V8 rollback 뒤 추가 테이블 4개가 모두 제거됨을 확인하고 임시 컨테이너를 삭제했다.
7. bootstrap·rollback의 `SET LOCAL ROLE`은 파일 자체의 명시적 트랜잭션 안에서 실행하도록 보완했다.

## 남은 범위

- 개발 Supabase에 V8을 적용하고 owner, ACL, RLS, trigger와 함수 실행 권한을 다시 확인한다.
- Core API 채팅·위치 endpoint와 PostgreSQL 커밋 후 Broadcast 발행을 구현한다.
- Firebase JWT의 발급 project와 세션 참여 관계를 확인하는 Realtime private channel RLS를 적용한다.
- Android를 Core API 조회·쓰기와 Realtime 구독으로 전환한 뒤 Firestore 채팅·위치 쓰기를 중지한다.
- #222에서 일일 파기 job과 Firebase Storage 첨부 삭제를 구현한다.

## 리스크

- schema가 적용돼도 API와 앱이 전환되기 전에는 Firestore가 채팅·위치의 실제 경로다.
- Broadcast는 전달 보장이 있는 업무 저장소가 아니므로 클라이언트가 이벤트만으로 상태를 확정하면 안 된다.
- 첨부 원본 삭제와 DB 상태 갱신이 분리되므로 재시도 가능한 파기 job이 필요하다.
- production에는 개발 DB와 Realtime 인가 검증이 끝난 뒤 별도 승인으로 적용한다.
