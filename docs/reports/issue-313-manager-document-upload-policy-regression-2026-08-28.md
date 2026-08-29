# Issue 313 매니저 서류 업로드 기술 하한선 회귀 검증

기준일: 2026-08-28

> 2026-08-29 후속 계약: 관리자 검토 경로의 안전한 미리보기 범위에 맞춰 매니저 증빙 신규 업로드를 `image/jpeg`, `image/png`, `image/webp`로 제한했다. 아래 내용은 변경 전 기술 하한선을 검증한 당시 기록이다.

관련 이슈: [#313 매니저 자격 인증 서류와 업로드 파일 규격 확정](https://github.com/bodeul110/Bodeul/issues/313)

## 작업 목적

기획과 법률 검토가 필요한 서류 정책을 미리 확정하지 않으면서, 현재 앱이 이미 적용하는 최소 파일 형식·크기 거부 동작의 회귀를 막는다.

## 선택한 방식

- `ManagerDocumentUploadPolicy`의 기존 형식 판정과 크기 결과 판정을 패키지 단위 테스트가 호출할 수 있는 작은 경계로 분리했다.
- PDF와 이미지가 형식 검사에서 통과하고, 그 밖의 형식이 거부되는 현재 동작을 검증한다.
- 10MB 초과와 크기 미확인 결과가 각각 기존 한국어 오류로 거부되는지 검증한다.
- 실제 Firebase Storage 업로드 경로와 허용값은 변경하지 않는다.

## 대안

- Android `ContentResolver` 전체를 계측 테스트로 검증할 수 있지만, 이번 범위에는 실제 파일 선택기와 Storage 연동이 필요하지 않고 실행 비용도 더 크다.
- 업로드 정책을 새 설정 객체로 일반화할 수 있지만, 아직 서류 종류와 파일 규격이 확정되지 않아 추후 정책을 현재 값에 고정할 위험이 있다.

## 선택 이유

현재 MVP에서는 되돌릴 수 없는 운영 정책보다 명확한 기술상 거부 하한선을 자동 검증하는 것이 우선이다. 기존 크기 검사 단위 테스트는 바이트 판정만 확인하므로, 매니저 서류 정책이 해당 결과를 실제 사용자 오류로 매핑하는 경계와 MIME 형식 판정은 별도 회귀 검증이 필요하다.

## 리스크

- 단위 테스트는 Android 파일 선택기 공급자가 반환하는 실제 MIME·메타데이터 조합을 모두 재현하지 않는다.
- `image/*` 전체를 허용하는 현행 동작을 검증할 뿐 HEIC 허용 여부를 새로 결정하지 않는다.
- 이 결과만으로 서류 종류, 파일 개수, 신청당 총용량, 보관기간 또는 Storage Rules가 승인됐다고 볼 수 없다.

## 변경된 범위

- `ManagerDocumentUploadPolicy`의 기존 판정 순서와 오류 문구를 유지한 채 테스트 가능한 내부 경계로 분리했다.
- `ManagerDocumentUploadPolicyTest`에 형식·크기 거부 회귀 테스트를 추가했다.

## 제외한 범위

- 필수·선택 서류 종류와 법적 수집 근거
- JPEG·PNG·HEIC 등 이미지 세부 형식
- 파일 개수, 파일당·신청당 용량의 신규 정책
- 보관기간, 자동 파기, 재제출과 원본 교체
- Firebase Storage Rules, 실제 업로드와 관리자 웹 심사 흐름

## 검증

아래 검증을 실행했다.

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.bodeul.data.ManagerDocumentUploadPolicyTest" --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

- 새 회귀 테스트 4건이 통과했다.
- 전체 Android 단위 테스트와 debug APK 빌드가 통과했다.

## 남은 범위

#313의 정책 질문은 계속 `Blocked`로 유지한다. 기획과 법률 검토가 완료되면 Android, Storage Rules, 관리자 웹, 보관·파기 작업을 각각 분리해 반영한다.
