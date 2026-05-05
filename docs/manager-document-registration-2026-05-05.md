# 2026-05-05 매니저 서류등록 인증 화면

## 구현한 내용

- 매니저 홈의 `서류 등록` 카드가 `매니저 자격 인증` 화면으로 이동하도록 연결했다.
- 인증 화면에서 `신분증 업로드`, `요양보호사 자격증`, `간호사 자격증`, `범죄경력회보서 제출` 파일을 실제 문서 선택기로 업로드하고 초안으로 저장할 수 있게 만들었다.
- 하단 `인증 요청하기` 버튼은 필수 서류 4종 업로드가 끝난 뒤에만 활성화되도록 분리했고, 요청 시점에만 검토 상태와 이력이 갱신되도록 저장 흐름을 정리했다.
- 화면 객체는 액티비티, 코디네이터, 바인더, 아이템 모델로 분리해 액티비티에는 흐름 제어만 남겼다.
- 2026-05-05 추가 수정으로 `심사 이력` 영역을 제거했고, 화면 배경과 상단 텍스트 톤을 다른 앱 화면과 동일한 기본 배경 스타일에 맞췄다.

## 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/manager/ManagerActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationScreenModel.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/res/layout/activity_manager_document_registration.xml`
- `app/src/main/res/layout/item_manager_document_registration.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `docs/manager-document-registration-2026-05-05.md`

## 남은 범위

- 관리자 웹 검토 화면은 아직 기존 필수 서류 3종 중심 구조라서, 이번 모바일 화면에 노출한 추가 자격증 슬롯까지 같은 수준으로 다루려면 별도 확장이 필요하다.
- 이번 작업 범위에는 파일 미리보기, 업로드한 문서 재열기, 삭제 기능은 포함하지 않았다.
