# Core API 작업 규칙

## 기본 원칙

- 사용자 메시지, 오류 응답 설명, 운영 문서는 한국어로 작성한다.
- Java 21과 현재 Spring Boot 3.5.x 기준을 사용자 승인 없이 올리지 않는다.
- DB 접속 문자열, Firebase 서비스 계정, Kakao key, OCI key를 커밋하지 않는다.
- API 응답과 로그에 token, 비밀번호, connection string 원문을 남기지 않는다.

## 구조

- Controller는 HTTP 입력과 응답 변환만 담당한다.
- 인증과 인가는 filter 또는 service 경계에서 처리한다.
- DB 접근은 repository에 둔다.
- 외부 API 호출은 client에 둔다.
- migration은 runtime 코드와 분리하고 `core-api/`에서만 소유한다.
- 서버 전용 테이블은 Data API에 노출하지 않는 `bodeul` schema에 둔다.
- Flyway migration은 `bodeul_migration` role로 객체를 만들고, runtime role의 권한은 migration에서 명시적으로 부여한다.
- DB role 비밀번호와 connection string은 bootstrap 또는 migration 파일에 넣지 않는다.
- 원격 migration은 `migrateDatabase` task와 승인된 migration Environment로만 실행한다.

## 검증

- 변경 후 `./gradlew check --console=plain`을 실행한다.
- 설정 변경은 `local`과 `preview` profile의 차이를 확인한다.
- DB migration은 별도 개발 DB에서 적용과 rollback을 검증하기 전 production에 적용하지 않는다.

## GitHub

- 메인 저장소의 PR과 `Core API CI`를 거쳐 반영한다.
- PR 제목에는 `[codex]`, `[AI]` 같은 도구 표기를 넣지 않는다.
- 본문은 배경, 변경 내용, 확인한 결과, 필요한 후속 작업을 짧게 적는다.
- 설계나 보안 판단이 있을 때만 대안과 리스크를 덧붙인다.
- 실행하지 않은 검증을 수행한 것처럼 적지 않는다.
