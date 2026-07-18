# Issue 220 Firestore 업무 쓰기 경계 전환

기준일: 2026-07-18

## 작업 목적

예약, 매칭, 동행 상태, 리포트와 후속 처리의 운영 원본을 PostgreSQL로 전환한 뒤에도 Android 클라이언트가 같은 업무 데이터를 Firestore에 다시 쓸 수 있는 경로가 남아 있었다. Rules에서 이 경로를 닫아 도메인별 쓰기 주체를 서버 하나로 고정한다.

## 선택한 방식

- `appointmentRequests`, `sessionReports`, `appointmentFollowUps`는 참여자·관리자의 기존 읽기를 유지하고 클라이언트 create, update, delete를 모두 거부한다.
- `companionSessions`는 신규 문서 생성과 삭제, 진행 상태·현장 메모·리포트 관련 변경을 거부한다.
- #221 전환 전까지 필요한 기존 세션의 채팅, 읽음 시각, 위치 좌표·이력·공유 상태만 역할별로 갱신할 수 있다.
- Firebase Admin SDK는 Rules를 우회하므로 백업·복구 같은 승인된 서버 운영 작업은 이 변경의 영향을 받지 않는다.

## 대안

- Android의 과거 Firebase 저장소를 rollback 경로로 남기기 위해 기존 쓰기 Rules를 유지할 수 있다.
- #221 채팅·위치 전환까지 모든 `companionSessions` 쓰기 제한을 미룰 수 있다.
- 업무 필드와 실시간 필드를 별도 Firestore 문서로 먼저 분리할 수 있다.

## 선택 이유

현재 개발 환경에서는 예약·취소, 관리자 배정, 세션 진행, 리포트와 후속 처리가 이미 Spring Core API 또는 별도 관리자 서버를 통해 같은 PostgreSQL에 기록된다. 여기서 Firestore 클라이언트 쓰기를 계속 허용하면 앱 버전이나 레거시 관리자 화면에 따라 두 저장소가 다시 갈라질 수 있다.

반면 채팅과 위치는 #221의 Supabase Realtime 전환 전까지 기존 Firestore listener가 필요하다. 그래서 업무 데이터 쓰기는 즉시 닫고, 기존 세션의 실시간 필드만 최소 허용하는 단계적 전환을 선택했다.

## 적용 경계

| 컬렉션 | 클라이언트 읽기 | 클라이언트 쓰기 |
| --- | --- | --- |
| `appointmentRequests` | 기존 참여자·배정 매니저·관리자 | 모두 거부 |
| `companionSessions` | 기존 참여자·배정 매니저·관리자 | 배정 매니저의 채팅·위치·읽음, 환자·보호자의 채팅·읽음만 허용 |
| `sessionReports` | 기존 세션 참여자·관리자 | 모두 거부 |
| `appointmentFollowUps` | 기존 예약 참여자·관리자 | 모두 거부 |

허용되는 매니저 실시간 필드는 `locationSummary`, 최신 좌표와 위치 이력, 위치 공유 상태, 채팅 메시지, 매니저 읽음 시각, 위치 경보 단계와 갱신 시각이다. `currentStatus`, `currentStepOrder`, 보호자 공유 메모, 현장·복약·약국 메모와 완료 상태는 PostgreSQL 원본이므로 Firestore에서 수정할 수 없다.

## 검증

`npm --prefix tools/firebase run test:rules`로 Firestore·Storage emulator 시나리오 7건을 실행했다.

- 환자와 관리자 모두 예약 생성·수정·삭제가 거부된다.
- 매니저와 관리자 모두 세션 생성·진행 상태 수정·삭제가 거부된다.
- 배정 매니저의 위치 좌표·이력 갱신과 환자의 채팅·읽음 갱신은 허용된다.
- 매니저와 관리자의 리포트 쓰기, 환자와 관리자의 후속 처리 쓰기는 거부된다.
- 참여자 읽기와 관리자 전용 컬렉션, Storage 역할 경계는 기존대로 통과한다.
- PR #239 병합 commit `e7edcd35`의 `firestore.rules`를 Firebase CLI로 `bodeul-dev`에 배포했다. 원격 컴파일과 Rules release가 모두 성공했다.

## 영향과 리스크

- Android `ServiceLocator`가 선택하는 Core API 저장소의 예약·취소·세션·리포트·후속 처리 경로는 영향을 받지 않는다.
- `FirebaseAdminRequestStore`의 Android 관리자 배정과 `FirebaseBookingRepository`의 과거 예약·후속 처리 쓰기는 더 이상 허용되지 않는다. 운영 관리자 배정은 별도 관리자 웹 서버 API를 사용해야 한다.
- Firestore legacy ID가 없는 Core-only 세션은 기존 채팅·위치 문서도 없으므로 #221 완료 전에는 해당 실시간 기능을 사용할 수 없다. 이는 이번 Rules 변경으로 새로 생긴 제약이 아니라 Core-only 전환에서 이미 확인된 잔여 범위다.
- production에는 이 Rules를 아직 배포하지 않았다. production PostgreSQL migration과 역할별 검증이 끝난 뒤 같은 쓰기 경계를 적용한다.
