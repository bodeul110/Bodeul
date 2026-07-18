# 2026년 Production 운영 전환 계획

기준일: 2026-07-18
목표 전환일: 2026-12-15 10:00 KST

## 목표

2026년 말까지 Supabase PostgreSQL을 업무 데이터의 단일 source of truth로 전환한다. Android와 사용자 웹은 Spring Core API를, 관리자 웹은 Next.js 관리자 서버를 거쳐 같은 PostgreSQL을 사용한다. Firebase는 Auth, FCM, App Check와 파일 저장소 역할만 유지한다.

운영 전환은 연말에 한 번에 구현하는 작업이 아니다. 2026년 11월까지 개발 환경의 도메인 전환과 보안 검증을 끝내고, 12월에는 production migration, rollback, smoke test와 트래픽 전환만 수행한다.

## 운영 비용 승인 기준

월 반복 비용의 권장 승인 한도는 세금과 환율 변동을 포함해 **150,000 KRW**다. 정상 운영 목표는 **월 100,000~130,000 KRW**이며, 150,000 KRW는 정상 목표가 아니라 추가 승인 없이 대응할 수 있는 상한이다.

| 항목 | 권장 등급과 수량 | 예상 비용 | 적용 시점 |
| --- | --- | ---: | --- |
| Supabase | Pro 조직, Tokyo Micro 2개(개발·production) | USD 35/월 예상 | 2026-11-16까지 |
| Vercel | Pro 개발자 좌석 2개 | USD 40/월 | 2026-11-16까지 |
| Google Cloud/Firebase | production budget 30,000 KRW | 사용량 기준, 30,000 KRW 알림 | 이미 적용 |
| Google Cloud/Firebase | 개발 budget 10,000 KRW | 사용량 기준, 10,000 KRW 알림 | 이미 적용 |
| 기준 도메인 | 도메인 1개와 `admin`, `api` 서브도메인 | 연 50,000 KRW 이내 권장 | 2026-10-16까지 |

- Supabase Pro는 월 USD 25와 USD 10 compute credit을 제공한다. 현재처럼 Micro 프로젝트 2개를 유지하면 월 USD 35를 기준으로 잡는다.
- Supabase spend cap은 켠 상태로 시작하며 PITR, custom domain, Log Drain은 초기 운영 범위에 포함하지 않는다.
- Vercel Pro는 저장소 소유자와 웹 담당자 두 명을 개발자 좌석으로 계산한다. 열람만 필요한 인원은 무료 Viewer를 사용한다.
- Vercel Hobby는 비상업적 개인 용도로 제한되므로 실제 운영 전환 전에 Pro로 바꾼다.
- Google Cloud budget은 지출을 자동 차단하지 않는다. 50%, 80%, 100% 알림과 Cloud Run 최대 인스턴스 2, 인스턴스당 DB pool 2를 함께 유지한다.
- 월 예상액이 2개월 연속 130,000 KRW를 넘거나 단일 서비스가 자체 예산의 80%를 넘으면 용량 증설 전에 원인을 검토한다.

## 목표 데이터 경계

| 범위 | source of truth | 접근 경로 |
| --- | --- | --- |
| 사용자·역할·예약·매칭·동행·채팅·위치·리포트·운영 메타데이터 | Supabase PostgreSQL `bodeul` schema | Spring Core API 또는 Next.js 관리자 서버 |
| 실시간 채팅·위치·상태 알림 | PostgreSQL 커밋 후 Supabase Realtime private Broadcast | Supabase Third-Party Auth가 검증한 Firebase JWT로 구독, 클라이언트 DB 쓰기 금지 |
| 사용자 인증 | Firebase Auth | Firebase ID token을 서버에서 검증 |
| 백그라운드 알림 | Firebase FCM | 서버에서 발송 |
| 앱·웹 요청 출처 검증 | Firebase App Check | Core API와 관리자 서버에서 검증 |
| 파일 원본 | Firebase Storage | 서버 인가 후 제한된 업로드·다운로드 경로 사용 |
| Firestore | 전환 기간의 읽기 전용 rollback 자료 | 신규 업무 쓰기 금지, 안정화 후 제거 |

Supabase Data API를 업무 데이터 쓰기 경로로 사용하지 않는다. Realtime은 커밋된 사건을 전달하는 채널이며, 권위 있는 조회와 명령은 계속 서버를 거친다.

Realtime 전환 전에 개발·production Supabase에 각각 Firebase Third-Party Auth integration을 등록한다. Firebase 사용자의 ID token에는 `role: authenticated` custom claim을 부여하고, `realtime.messages` RLS에서 Firebase 프로젝트 ID, 채널 주제와 예약·동행 참여 관계를 함께 검증한다. 기존 사용자 claim 백필과 token 강제 갱신도 전환 범위에 포함한다.

## 일정

| 기간 | 완료 목표 | 종료 조건 |
| --- | --- | --- |
| 2026-07-18~08-31 | 예약 도메인 전환 | Spring CRUD·인가·테스트, Android API repository, 개발 DB backfill·비교 완료 |
| 2026-09-01~09-30 | 예약 source of truth 전환 | 개발 환경 PostgreSQL 단일 쓰기, Firestore 쓰기 차단과 rollback 리허설 |
| 2026-10-01~10-31 | 매칭·동행·리포트·관리자 쓰기 전환 | 공용 migration, 서버별 최소 권한, 감사 이력과 계약 테스트 완료 |
| 2026-11-01~11-15 | 채팅·위치 Realtime 전환 | Firebase Third-Party Auth, private Broadcast RLS, 재연결, FCM fallback, 보관·파기 작업 검증 |
| 2026-11-16~11-30 | 운영 등급·production 사전 검증 | Supabase/Vercel 유료 전환, production secret, full rehearsal 완료 |
| 2026-12-01~12-11 | 출시 후보 동결 | release 빌드, App Check, backup/restore, rollback, 부하·권한 smoke 통과 |
| 2026-12-14 | Go/No-Go | 차단 항목 0건, 운영자 확인, 전환·복구 명령 재확인 |
| 2026-12-15 | production 전환 | 10:00 KST migration과 배포, 핵심 사용자 흐름 smoke 통과 |
| 2026-12-15~2027-01-14 | 안정화 기간 | Firestore 읽기 전용 rollback 자료 유지, 일일 오류·비용·정합성 확인 |
| 2027-01-15 이후 | legacy 제거 | 보존 예외를 제외한 Firestore 업무 데이터와 관련 Functions 제거 |

## 도메인 전환 공통 게이트

각 도메인은 다음 조건을 모두 충족해야 source of truth를 바꾼다.

1. Flyway migration과 역방향 보정 절차가 있다.
2. Core와 Admin runtime role에 필요한 DML만 부여한다.
3. 개발 DB backfill 후 row 수, 필수 필드와 핵심 API 응답을 비교한다.
4. Firebase ID token과 PostgreSQL role 기준의 정상·401·403 테스트가 있다.
5. Android 또는 관리자 웹이 PostgreSQL 경로만 사용하는 것을 확인한다.
6. cutover 시점부터 한 도메인에 쓰기 주체를 하나만 둔다.
7. rollback 시 손실될 수 있는 데이터와 허용 시간을 기록한다.
8. 보관 기간과 자동 파기 방식이 적용된다.

## 12월 Go/No-Go 기준

다음 항목 중 하나라도 충족하지 못하면 12월 15일 전환을 연기한다.

- production Supabase가 Pro이고 일일 7일 백업과 외부 주간 dump가 모두 정상이다.
- Cloud Run과 Vercel의 직전 정상 배포 rollback을 실제로 재현했다.
- 예약, 매칭, 동행, 채팅, 위치와 관리자 심사 핵심 흐름이 production 격리 데이터로 통과했다.
- release Android의 Firebase Auth, App Check Play Integrity와 Kakao 플랫폼 설정이 통과했다.
- production Supabase가 production Firebase만 신뢰하고, `role: authenticated` claim이 없거나 다른 프로젝트가 발급한 token은 Realtime 구독을 거부한다.
- DB 공개 role 권한 0건, Supabase Security Advisor 오류 0건이다.
- 실명 운영자 2명과 rollback 승인자, 장애 연락 경로가 지정되어 있다.
- 개인정보 처리방침과 위치기반서비스 이용약관에 실제 보관 기간과 파기 절차가 반영되어 있다.

## Rollback 기준

- 배포 오류는 Cloud Run revision 또는 Vercel deployment를 직전 정상 버전으로 돌린다.
- DB migration 오류는 호환 가능한 이전 애플리케이션을 먼저 복구하고, 파괴적 migration은 별도 정비 시간에만 수행한다.
- source of truth 전환 후에는 Firestore 이중 쓰기로 복구하지 않는다. PostgreSQL 백업 복원 또는 검증된 역방향 보정 스크립트를 사용한다.
- Firestore 읽기 전용 자료는 2027-01-14까지만 rollback 비교용으로 유지한다.

## 근거

- [Supabase 요금](https://supabase.com/pricing)
- [Supabase DB 연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase 백업](https://supabase.com/docs/guides/platform/backups)
- [Supabase Firebase Auth 연동](https://supabase.com/docs/guides/auth/third-party/firebase-auth)
- [Supabase Realtime 시작과 private channel](https://supabase.com/docs/guides/realtime/getting_started)
- [Vercel 요금](https://vercel.com/pricing)
- [Vercel Hobby 제한](https://vercel.com/docs/plans/hobby)
- [Google Cloud budget](https://cloud.google.com/billing/docs/how-to/budgets)
- [데이터 보관 및 파기 정책](data-retention-policy.md)
