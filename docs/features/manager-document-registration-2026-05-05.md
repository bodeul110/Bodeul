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
- `manager-document-registration-2026-05-05.md`

## 남은 범위

- 이번 작업 범위에는 파일 미리보기, 업로드한 문서 재열기, 삭제 기능은 포함하지 않았다.

## 2026-05-22 추가 업데이트

### 구현한 내용

- 매니저 서류 등록 화면에서 업로드된 파일은 `파일 열기`로 바로 다시 열 수 있게 했다.
- 관리자 서류 검토 카드에 `제출 파일 보기`를 추가해 실제 제출 파일 목록을 보고 선택한 파일을 바로 열 수 있게 했다.
- 목업 모드에서는 SAF `content://` URI를 저장해 같은 기기에서 다시 열 수 있게 했고, Firebase 모드에서는 Storage 경로를 다운로드 URI로 해석해 미리보기를 열도록 연결했다.

### 변경된 범위

- `app/src/main/java/com/example/bodeul/domain/model/ManagerDocumentFileMetadata.java`
- `app/src/main/java/com/example/bodeul/data/ManagerDocumentPreviewResolver.java`
- `app/src/main/java/com/example/bodeul/data/ServiceLocator.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentPreviewResolver.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentPreviewResolver.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemModel.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardModel.java`
- `app/src/main/java/com/example/bodeul/util/DocumentPreviewLauncher.java`
- `app/src/main/res/layout/activity_manager_document_registration.xml`
- `app/src/main/res/layout/item_manager_document_registration.xml`
- `app/src/main/res/layout/item_admin_manager_document.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java`
- `manager-document-registration-2026-05-05.md`

### 남은 범위

- 업로드한 파일 삭제와 Storage 정리 정책은 아직 없다.
- 과거 목업 데이터처럼 `previewUri`가 저장되지 않은 기존 샘플 파일은 목업 모드에서 다시 열 수 없다.
