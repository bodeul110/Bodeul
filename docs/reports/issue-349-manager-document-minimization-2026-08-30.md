# Issue 349 매니저 자격 증빙 최소수집 전환

기준일: 2026-08-30

## 작업 목적

관리자 3역할 RBAC를 적용하기 전에 매니저 심사 원본을 실제 목적에 필요한 자격 증빙 1종으로 줄이고, Android·Firebase Rules·Functions·운영 도구·관리자 웹이 같은 문서 키와 보존 계약을 사용하게 한다.

## 선택한 방식

- 신규 canonical key는 `license`, `nursingLicense`다.
- 한 제출에는 두 키 중 정확히 1종만 허용한다.
- `idCard`, `criminalRecord`, 신규 `healthCertificate` 쓰기를 차단한다.
- 기존 `healthCertificate`는 실제 간호사 면허 데이터이므로 `nursingLicense`로 이관한다.
- 기존 신분증·범죄경력 원본은 신규 심사에 사용하지 않고 심사 종료 기준 보존 작업에서만 파기한다.
- 자격 증빙은 JPEG·PNG·WebP, 파일당 10 MiB 이하로 제한하고 심사 종료 후 30일 안에 원본을 삭제한다.
- 자격 종류를 교체하면 서버가 이전 canonical·legacy 원본을 안전 조건 아래 정리한다. 활성 또는 불완전한 legal hold가 있으면 교체와 삭제를 모두 차단한다.

## 대안

| 대안 | 판단 |
| --- | --- |
| 기존 3종 필수 제출 유지 | 신분증·범죄경력 원본을 MVP에서 보관할 필요와 법적 절차가 확정되지 않아 제외했다. |
| `healthCertificate` 이름만 유지 | 실제 데이터가 건강진단서가 아니라 간호사 면허이므로 목적과 키 이름이 어긋나 제외했다. |
| 메타데이터 키만 변경 | Storage 경로의 documentKey 검증과 불일치해 서버 심사·파기에서 거부되므로 객체 경로까지 이관한다. |
| 두 자격 증빙 동시 허용 | 필요한 원본을 최소화하고 자격 종류 교체를 명확히 하기 위해 정확히 1종만 유지한다. |

## 선택 이유

현재 MVP 규모에서는 신분 확인·범죄경력 조회 체계를 별도로 운영하지 않으며, 매니저의 직무 자격 확인에는 관련 자격 증빙 1종이면 충분하다. 원본 종류를 줄이면 유출 영향, 관리자 검수 범위, Storage 비용과 파기 실패 표면을 함께 줄일 수 있다.

## 이관 계약

1. dry-run에서 UID, nested metadata, path map, legacy key와 실제 Storage 객체를 대조한다.
2. `manager-documents/{uid}/healthCertificate/...` 객체를 같은 파일명의 `manager-documents/{uid}/nursingLicense/...`로 복사한다.
3. 조회한 원본 generation을 복사 조건으로 고정하고, 복사본의 크기·MIME·MD5/CRC32C 등 제공되는 무결성 메타데이터를 원본과 대조한다.
4. Firestore 문서를 현재 update time 조건으로 갱신해 `nursingLicense` metadata/path를 canonical로 만들고 legacy 필드를 제거한다.
5. Firestore 갱신이 성공한 뒤에만 legacy Storage 객체를 삭제한다.
6. 이미 같은 canonical 상태인 항목은 no-op 처리하고, canonical 충돌·경로 불일치·부분 상태는 자동 덮어쓰기 없이 차단 보고한다.
7. 서버 전용 `managerDocumentEvidenceMigration` 표식으로 순수 키 이관과 실제 사용자 재제출을 구분한다. 클라이언트는 이 표식을 생성·변경·삭제할 수 없다.

apply는 명시적 옵션이 있어야 실행하며, 재실행해도 완료된 항목을 다시 복사하거나 삭제하지 않는 멱등 경계를 유지한다.

## 배포 순서

1. V20 migration과 rollback 계약 검증
2. legacy 이관 dry-run과 대상 수 대조
3. 승인된 개발 환경 apply와 사후 점검
4. Firestore/Storage Rules와 Functions 배포
5. Android 앱과 관리자 웹을 canonical 계약으로 배포
6. 보호된 Preview에서 역할별 심사·거부·파기 흐름 검증

관리자 웹 PR #44는 메인 PR #383이 확정한 계약을 후행 적용한다. 두 배포가 모두 끝나기 전에는 새 심사 UI를 운영 활성화하지 않는다.

## 리스크

- 객체 복사 후 Firestore 갱신 전 실패하면 canonical 객체가 고아로 남을 수 있다. 사후 점검이 해당 부분 상태를 찾아야 한다.
- Firestore 갱신 뒤 legacy 객체 삭제가 실패하면 두 객체가 잠시 남을 수 있다. 재실행은 canonical 메타데이터를 신뢰하되 legacy 객체 존재를 삭제 후보로 보고한다.
- 사용자 문서의 자격 종류 교체·보존 파기·legacy 이관은 Firestore transaction에서 서버 전용 `managerDocumentDeletionClaim`을 먼저 확보한다. claim이 존재하는 동안 클라이언트의 참조·심사 상태 변경과 Storage 신규 업로드를 차단하고, 같은 claim만 객체 generation을 고정해 삭제한 뒤 참조와 claim을 transaction으로 정리한다. 보존 필드나 claim이 불완전하거나 다른 작업과 충돌하면 삭제하지 않는 fail-closed 기준을 사용한다.
- 구버전 앱이 legacy key를 계속 쓰면 이관 뒤 다시 불일치가 생길 수 있다. 신규 Rules와 앱 배포 순서를 같은 출시 게이트에서 관리한다.
- 실제 운영 데이터에 대한 apply와 Rules·Functions 배포는 별도 명시적 승인 전에는 실행하지 않는다.

## 검증 기준

- Android 단위 테스트와 debug APK 빌드
- Firestore/Storage Rules emulator
- Functions 단위 테스트와 retention emulator
- Firebase 운영 도구 단위 테스트 및 migration dry-run fixture
- Core API 전체 check와 PostgreSQL V19/V20 migration-contract
- 현재 HEAD CI와 사람 재승인
