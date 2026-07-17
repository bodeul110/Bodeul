# Firebase 선택 근거

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

현재 BoDeul MVP에서는 Firebase 중심 구조를 유지하는 것이 맞다. 이유는 Android 앱, 관리자 웹, 인증, 데이터, 파일, 알림, 운영 도구를 한 프로젝트 안에서 빠르게 연결할 수 있고, 현재 팀 규모에서는 별도 백엔드 서버 운영 부담을 줄이는 것이 더 중요하기 때문이다.

## 작업 목적

Firebase를 왜 쓰는지, 왜 별도 서버를 아직 두지 않는지, 나중에 어떤 조건에서 바꿀지 설명할 수 있게 한다.

## 선택한 방식

- Firebase Auth로 사용자 로그인과 세션을 관리한다.
- Cloud Firestore를 주요 서비스 데이터 저장소로 사용한다.
- Firebase Storage에 매니저 서류와 채팅 첨부 원본을 저장한다.
- Cloud Functions는 Kakao 로그인 보조, 알림 큐, 관리자 수동 실행처럼 서버 검증이 필요한 작업만 맡긴다.
- FCM은 앱 푸시 알림에 사용한다.
- 관리자 웹 배포는 Vercel로 분리했으며 Firebase Hosting은 현재 메인 저장소의 운영 대상이 아니다.

## 대안

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| Spring/Node API 서버 + PostgreSQL | 도메인 로직, 권한, 정산 쿼리를 서버에 집중할 수 있다. | 현재 규모에서는 서버 운영과 배포 파이프라인을 직접 가져가는 비용이 크다. |
| Supabase | PostgreSQL, Auth, Storage, Realtime을 제공한다. | Firebase Auth, FCM, Functions, Android SDK 결합을 다시 설계해야 한다. |
| AWS Amplify | 인증, API, Storage, Hosting을 묶어 제공한다. | 현재 Android/Firebase 구현과 운영 도구를 갈아엎는 비용이 이익보다 크다. |
| 자체 VM/DB 운영 | 인프라 제어권이 높다. | 보안 패치, 장애 대응, 백업, 모니터링까지 팀이 직접 떠안아야 한다. |

## 선택 이유

- 현재 MVP는 기능 검증과 운영 흐름 확인이 중요하다.
- 앱, 관리자 웹, 운영 도구가 같은 Firebase project와 같은 데이터 계약을 공유하면 구현 속도가 빠르다.
- Auth, Rules, Firestore, Storage, Functions, FCM을 조합하면 별도 서버 없이도 역할 기반 MVP를 만들 수 있다.
- 관리자 웹이 Firebase Auth와 Firestore/Storage를 직접 사용하므로, 백오피스 초기 구축 비용이 낮다.
- GitHub Actions preflight와 `tools/firebase` 운영 스크립트가 이미 Firebase 구조를 기준으로 맞춰져 있다.

## 리스크

- 클라이언트가 Firestore/Storage를 직접 접근하므로 Rules 품질이 보안의 핵심이다.
- 복잡한 권한, 정산, 통계가 커지면 클라이언트 직접 접근 구조가 한계가 될 수 있다.
- App Check가 아직 강제 상태가 아니라 운영 전 abuse 방어를 더 정리해야 한다.
- Firebase read/write, Storage, Functions 호출량이 늘면 비용 추적이 필요하다.

## 전환 조건

- 대부분의 쓰기가 Functions를 거쳐야 할 정도로 서버 검증 요구가 늘어난다.
- 정산/통계/검색이 핵심 기능이 되어 Firestore 쿼리와 집계 문서만으로 설명하기 어려워진다.
- 외부 API 연동과 권한 정책이 많아져 API gateway 성격의 서버가 필요해진다.
- 운영자가 늘고 관리자 권한 전파 정책을 더 엄격히 관리해야 한다.
