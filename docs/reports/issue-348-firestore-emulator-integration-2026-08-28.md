# Issue 348 Firestore Emulator 통합 검증

기준일: 2026-08-28

관련 이슈: [#348 탈퇴·삭제와 법정 보존 분리 구현](https://github.com/bodeul110/Bodeul/issues/348)

## 작업 목적

모의 Firestore SDK 테스트만으로 확인하던 계정 삭제 영향도 aggregation 계약을 실제 Firestore Emulator와 합성 UID·문서로 검증한다. 운영·개발 Firebase 데이터와 자격 증명에는 접근하지 않는다.

## 선택한 방식

- 일반 Core API 단위 테스트와 `firestore-emulator` tag의 통합 테스트를 분리한다.
- Firebase CLI의 `emulators:exec`가 설정한 `FIRESTORE_EMULATOR_HOST`가 `localhost` 또는 `127.0.0.1`일 때만 전용 Gradle 작업을 허용한다.
- `demo-bodeul-account-deletion` project에서 합성 사용자 2명의 사용자·지원·예약·동행 세션 문서를 한 번에 준비한다.
- 대상 UID와 다른 UID를 각각 조회해 아홉 equality aggregation이 필드별 예상 건수를 반환하고 서로 격리되는지 확인한다.
- 별도 emulator client를 닫아 Firestore source 장애를 만들고 서비스 결과가 `ERROR`, 빈 counts와 `SOURCE_UNAVAILABLE`로 닫히는지 확인한다.
- Core API CI에 독립 `firestore-emulator` job을 추가해 PR과 `master` 변경에서 같은 검증을 반복한다.

## 대안

개발 Firebase 프로젝트에 비식별 fixture를 넣고 Cloud Run 서비스 계정으로 호출하는 방식은 실제 IAM까지 검증할 수 있다. 하지만 공유 개발 데이터 변경, 자격 증명과 비용이 수반되므로 이번 안전한 slice에는 포함하지 않았다.

모의 SDK 테스트만 유지하는 방식은 빠르지만 Firestore client와 Emulator가 실제 equality aggregation을 처리하는 경로를 증명하지 못한다. Testcontainers로 Firebase Emulator를 감싸는 방식도 검토할 수 있으나 현재 저장소가 이미 Firebase CLI와 `firebase.json`을 기준으로 emulator를 운영하므로 별도 컨테이너 의존성을 추가하지 않았다.

## 선택 이유

현재 MVP 규모에서는 기존 Firebase CLI 실행 경로를 재사용하면 새 런타임 의존성 없이 실제 aggregation 동작을 검증할 수 있다. `demo-` project와 loopback host를 강제하면 운영·개발 Firebase 오접속 위험도 줄일 수 있다. 단위 테스트는 개별 query 실패와 timeout을, Emulator 통합 테스트는 SDK 실제 동작과 UID 격리를 담당하도록 검증 책임을 나눴다.

## 리스크

- Firestore Emulator는 Cloud IAM과 실제 Cloud Run 서비스 계정 권한을 재현하지 않는다.
- Emulator에서 특정 aggregation 하나만 선택적으로 실패시키는 경로는 제공하지 않는다. 해당 부분 실패 계약은 기존 모의 SDK 단위 테스트가 계속 검증한다.
- 닫힌 client는 source 전체 장애만 재현하며 실제 quota, 인덱스와 네트워크 장애의 세부 원인을 구분하지 않는다.
- 합성 fixture는 현재 확인된 UID 필드만 포함하므로 UID가 없는 legacy와 간접 연관 문서의 완전성을 증명하지 않는다.

## 구현 결과

- `firestoreEmulatorTest` Gradle 작업은 일반 `check`에서 제외되고 전용 tag만 실행한다.
- 로컬이 아닌 `FIRESTORE_EMULATOR_HOST`는 테스트 실행 전에 거부한다.
- 대상 UID에서 지원 요청 2건, 문의 1건, 예약 직접 참조 `1/2/1/2`, 세션 직접 참조 `2/2/1`을 확인한다.
- 다른 UID에서 지원 요청 1건, 문의 2건, 예약 직접 참조 `3/2/3/2`, 세션 직접 참조 `2/2/3`을 별도로 확인한다.
- Firestore source 장애 시 성공한 다른 출처와 관계없이 Firestore counts를 비워 부분 성공 건수를 노출하지 않는 계약을 확인한다.

## 검증

- `npm --prefix tools/firebase run test:core-api-firestore-emulator`: 3건 통과, 실패·오류·건너뜀 0건
- `.\\core-api\\gradlew.bat -p core-api check --console=plain`: 257건 통과, 실패·오류·건너뜀 0건
- `yq e '.' .github/workflows/core-api.yml`: 통과
- `git diff --check`: 통과

실행 중 Firebase CLI는 demo project와 로컬 Firestore Emulator만 사용했다. 운영·개발 Firebase, Firebase Auth, Storage와 Cloud IAM은 호출하지 않았다.

## 남은 범위

- 개발 Cloud Run 서비스 계정 IAM으로 비식별 fixture aggregation을 읽는 검증
- UID 필드가 없는 legacy와 간접 연관 Firestore 문서 영향도
- Firebase Storage, Firebase Auth, 백업과 삭제 실행기
- 부분 실패 재시도, 삭제 ledger와 복원 뒤 삭제 재적용

이번 변경 뒤에도 계정 삭제 준비도는 읽기 전용·미판정·미완료이며 #348의 프로젝트 상태는 `Blocked`를 유지한다.
