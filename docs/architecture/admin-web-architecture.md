# 관리자 웹 역할과 서버 경계

기준일: 2026-07-17

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

관리자 웹은 [bodeul110/bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web) 저장소의 Next.js 애플리케이션이 source of truth다. Vercel 서버가 Firebase ID token과 PostgreSQL `ADMIN` role을 확인한 뒤 관리자 전용 DB role로 직접 조회한다. Spring Core API나 기존 Node API를 중간 proxy로 두지 않는다.

## 현재 검증

| 시나리오 | 결과 |
| --- | --- |
| Preview 루트 | 200 |
| Authorization 없음 | 401 `missing_authorization` |
| 비관리자 token | 403 `admin_role_required` |
| 관리자 token | 200, 병원 가이드 조회 |
| 임시 검증 데이터 | 검증 후 Firebase 사용자와 DB row 삭제 확인 |
| DB TLS | Supabase Root CA를 명시하고 인증서 검증 유지 |
| DB 권한 | 관리자 runtime role은 SELECT만 허용, 연결 상한 5 |

Preview에만 `ADMIN_DATABASE_URL`을 두고 production에는 등록하지 않았다. 따라서 위 결과는 개발 인프라 경계 검증 완료이며 production 전환 완료가 아니다.

## 역할

- 매니저 서류 심사와 보완 메모
- 신고·문의·운영 상태 확인
- 병원 가이드와 운영 데이터 조회
- 민감정보 마스킹과 관리자 유휴 세션 종료
- 관리자 권한과 감사 이력 관리

## 선택 이유

- 운영 화면은 데스크톱에서 반복 조회·비교하는 작업에 적합하다.
- same-origin Route Handler를 사용하면 별도 CORS와 서버 간 hop을 만들지 않는다.
- 브라우저에는 DB 자격 증명을 노출하지 않고 서버 환경변수로 제한할 수 있다.
- 사용자 서비스와 관리자 배포·권한 범위를 분리할 수 있다.
- 별도 저장소에서 웹 담당자의 PR, Dependabot, Vercel Preview를 독립 운영할 수 있다.

## 대안

| 대안 | 판단 |
| --- | --- |
| Android 관리자 화면만 사용 | 현장 보조에는 쓸 수 있지만 대량 심사와 비교 작업에는 비효율적이다. |
| Spring Core API가 관리자 요청까지 처리 | 사용자와 관리자 권한·배포 경계가 결합된다. |
| Next.js → Spring → DB | 불필요한 hop과 장애 지점이 생긴다. |
| Firebase Console 직접 운영 | 비개발자 사용성과 실수 방지, 마스킹, 감사 이력을 제공하기 어렵다. |

## 남은 범위

- production Firebase·Supabase·Vercel 환경과 자격 증명 분리
- custom domain과 Firebase Auth authorized domain 확정
- reCAPTCHA Enterprise 기반 App Check와 enforcement 기준 검증
- Firestore 직접 접근 화면을 도메인별 서버 계약으로 이전
- 관리자 쓰기 API에 감사 로그와 더 세분화한 DB 권한 적용
- 운영 인원이 늘기 전 MFA와 긴급 권한 회수 절차 마련

Vite 빌드는 별도 저장소에 rollback 자산으로 남아 있다. 메인 저장소의 중복 `admin-web/`은 제거했으므로 웹 변경과 배포는 별도 저장소에서만 진행한다.

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [관리자 웹 환경 기준](../operations/admin-web-environments.md)
- [관리자 웹 저장소 분리 기록](../operations/admin-web-repository-split.md)
- [관리자 웹 데이터 계약](admin-web-data-contract.md)
