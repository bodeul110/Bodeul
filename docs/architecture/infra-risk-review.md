# 인프라 리스크와 보완 계획

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

현재 가장 큰 리스크는 Firebase 자체가 아니라, Rules, App Check, 백업/복원, API Key 운영, 비용 모니터링을 얼마나 명확히 검증하고 기록하느냐다. MVP 단계에서는 Firebase 중심 구조가 맞지만, 운영 전환 전에는 아래 항목을 단계적으로 보완해야 한다.

## 리스크 요약

| 리스크 | 현재 상태 | 보완 계획 |
| --- | --- | --- |
| Firestore Rules | `users/{uid}.role` 기반으로 환자/보호자/매니저/관리자 권한을 나눈다. | emulator 테스트와 실계정 QA 증적을 추가한다. |
| Storage Rules | 매니저 서류와 채팅 첨부를 역할/참여자 기준으로 제한한다. | 관리자 심사, 매니저 본인 접근, 세션 참여자 접근 테스트를 반복한다. |
| 관리자 권한 | custom claims가 아니라 Firestore `users.role == ADMIN` 기준이다. | 관리자 수가 늘면 custom claims와 MFA를 검토한다. |
| App Check | 코드 경로는 있으나 enforcement는 보류 상태다. | debug token, site key, release provider를 확인한 뒤 Functions, Storage, Firestore 순서로 강제 전환한다. |
| 백업/복원 | `bodeul-dev` 기준 읽기 전용 리허설은 완료했지만, 실제 write 복원은 격리 대상이 필요하다. | 별도 dev 프로젝트 또는 emulator에서 `restore:state:apply` 리허설을 기록한다. |
| 비용 | Firestore read/write, Storage, Functions, Kakao API 호출량 추정이 필요하다. | 비용 리스크 표와 예산 알림 설정을 유지한다. |
| Kakao REST API Key | Spring Core API proxy를 구현했고 Android 직접 호출과 REST 키 리소스를 제거했다. | Secret Manager 주입, 429 관측과 실기기 fallback 검증을 완료한다. |
| Hosting 배포 | preview/live 수동 배포는 검증했다. | GitHub Actions 자동화를 검토한다. |

## Firestore/Storage Rules

현재 권한 기준은 `users/{uid}.role`이다.

- `ADMIN`: 관리자 웹과 관리자 앱 운영 기능 접근
- `MANAGER`: 본인 서류, 본인 동행 세션 중심 접근
- `PATIENT/GUARDIAN`: 본인 예약, 연결 보호자/환자, 동행 세션 접근

현재 구조는 단순하고 설명하기 쉽지만, Rules가 보안의 핵심이므로 emulator 테스트와 실계정 검증 증적이 필요하다.

## App Check

App Check는 abuse 방어를 위한 후속 단계다. 현재는 앱과 관리자 웹에 초기화 경로를 준비했지만, 강제 적용은 하지 않았다. 초기에는 개발/시연/preview 환경을 자주 바꾸기 때문에 enforcement를 바로 켜면 정상 테스트가 막힐 수 있다.

전환 조건:

- 관리자 웹 custom domain 또는 최종 live 도메인이 확정된다.
- Android 디버그/릴리스 앱의 App Check provider 설정이 정리된다.
- Functions, Firestore, Storage별 차단 영향이 preview에서 검증된다.

전환 순서는 [App Check 적용 로드맵](../operations/app-check-enforcement-roadmap.md)을 따른다. 현재 판단은 Functions callable을 먼저 켜고, 파일 업로드/미리보기 검증 후 Storage, 마지막으로 직접 DB 접근 범위가 가장 넓은 Firestore를 켜는 것이다.

## 백업/복원

`tools/firebase`에는 백업/복원 도구 경로가 있다. 2026-06-25에는 `bodeul-dev`에서 `backup -> validate -> restore dry-run -> diff` 순서의 읽기 전용 리허설을 수행했고, 백업 구조 오류 0건과 diff 추가/삭제/변경 0건을 확인했다.

다만 도구가 있다는 것과 실제 write 복원이 가능하다는 것은 다르다. 현재 접근 가능한 Firebase 프로젝트는 `bodeul-dev`뿐이므로 운영 기준 프로젝트에 `restore:state:apply`를 바로 실행하지 않았다. 다음 단계는 별도 dev 프로젝트 또는 emulator에 백업을 복원하고, 앱/관리자 웹에서 복구 상태를 확인하는 것이다.

상세 증적은 [Firestore 백업/복원 리허설 기록](../reports/firestore-backup-restore-rehearsal-2026-06-25.md)을 기준으로 한다.

## API Key 운영

- Firebase Web API Key는 일반적으로 프로젝트 식별자 성격이 강하지만, Auth/Rules/App Check와 함께 통제해야 한다.
- Kakao Local REST API Key는 Core API의 Google Secret Manager에 저장하고 호출량과 429를 확인한다.
- 서버에서 숨겨야 하는 키는 앱이나 Git에 넣지 않고 Functions secret 또는 Core API Secret Manager처럼 소유 서버의 비밀값 저장소에 둔다.

## 비용 리스크

현재 비용 리스크는 대규모 트래픽보다 관리자 화면의 반복 조회, 실시간 리스너, 파일 업로드, Functions scheduled/callable 호출에서 발생할 가능성이 높다.

보완 기준:

- 관리자 대시보드에 페이지네이션과 필터를 우선 적용한다.
- 실시간 리스너는 필요한 화면에만 둔다.
- Storage 원본 파일 크기와 업로드 제한을 유지한다.
- Firebase 예산 알림과 월별 운영 리포트를 연결한다.
