package com.example.bodeul.ui.manager;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;

/**
 * 자격 증빙의 업로드·미리보기·최종 제출을 화면 수명주기 밖에서 보존한다.
 */
public final class ManagerDocumentRegistrationViewModel extends ViewModel {
    private static final String STATE_OPERATION = "manager_document_registration.operation";
    private static final String STATE_COMPLETION_PENDING =
            "manager_document_registration.completion_pending";
    private static final String STATE_PENDING_FILE_TYPE =
            "manager_document_registration.pending_file_type";
    private static final String STATE_PENDING_MANAGER_USER_ID =
            "manager_document_registration.pending_manager_user_id";

    private enum Operation {
        IDLE,
        UPLOAD,
        PREVIEW,
        SUBMISSION,
        SUBMISSION_RECOVERY
    }

    public enum EventType {
        UPLOAD_SAVED,
        PREVIEW_READY,
        SUBMISSION_SUCCEEDED,
        ERROR
    }

    public static final class PendingDocumentSelection {
        private final String managerUserId;
        private final ManagerDocumentFileType fileType;

        private PendingDocumentSelection(
                String managerUserId,
                ManagerDocumentFileType fileType
        ) {
            this.managerUserId = managerUserId;
            this.fileType = fileType;
        }

        public String getManagerUserId() {
            return managerUserId;
        }

        public ManagerDocumentFileType getFileType() {
            return fileType;
        }
    }

    /**
     * 화면 회전 전후에 한 번만 소비되는 작업 결과다.
     */
    public static final class UiEvent {
        private final EventType type;
        @Nullable
        private final ManagerDocumentFileType fileType;
        @Nullable
        private final Uri previewUri;
        private final String contentType;
        private final String message;
        private boolean handled;

        private UiEvent(
                EventType type,
                @Nullable ManagerDocumentFileType fileType,
                @Nullable Uri previewUri,
                String contentType,
                String message
        ) {
            this.type = type;
            this.fileType = fileType;
            this.previewUri = previewUri;
            this.contentType = contentType == null ? "" : contentType;
            this.message = message == null ? "" : message;
        }

        @Nullable
        public synchronized UiEvent consume() {
            if (handled) {
                return null;
            }
            handled = true;
            return this;
        }

        public EventType getType() {
            return type;
        }

        @Nullable
        public ManagerDocumentFileType getFileType() {
            return fileType;
        }

        @Nullable
        public Uri getPreviewUri() {
            return previewUri;
        }

        public String getContentType() {
            return contentType;
        }

        public String getMessage() {
            return message;
        }

        private static UiEvent uploadSaved(ManagerDocumentFileType fileType) {
            return new UiEvent(EventType.UPLOAD_SAVED, fileType, null, "", "");
        }

        private static UiEvent previewReady(Uri uri, String contentType) {
            return new UiEvent(EventType.PREVIEW_READY, null, uri, contentType, "");
        }

        private static UiEvent submissionSucceeded() {
            return new UiEvent(EventType.SUBMISSION_SUCCEEDED, null, null, "", "");
        }

        private static UiEvent error(String message) {
            return new UiEvent(EventType.ERROR, null, null, "", message);
        }
    }

    private final ManagerRepository managerRepository;
    private final ManagerDocumentStorageUploader storageUploader;
    private final ManagerDocumentPreviewResolver previewResolver;
    private final SavedStateHandle savedStateHandle;
    private final MutableLiveData<Boolean> operationInFlight = new MutableLiveData<>(false);
    private final MutableLiveData<UiEvent> uiEvent = new MutableLiveData<>();

    private Operation operation = Operation.IDLE;
    private boolean recoveringSubmission;

    public ManagerDocumentRegistrationViewModel(
            ManagerRepository managerRepository,
            ManagerDocumentStorageUploader storageUploader,
            ManagerDocumentPreviewResolver previewResolver,
            SavedStateHandle savedStateHandle
    ) {
        this.managerRepository = managerRepository;
        this.storageUploader = storageUploader;
        this.previewResolver = previewResolver;
        this.savedStateHandle = savedStateHandle;

        Operation restoredOperation = parseOperation(savedStateHandle.get(STATE_OPERATION));
        if (restoredOperation == Operation.SUBMISSION) {
            operation = Operation.SUBMISSION_RECOVERY;
            recoveringSubmission = true;
            operationInFlight.setValue(true);
            return;
        }

        // 파일 선택기·업로드·미리보기는 프로세스가 사라지면 다시 시작해야 한다.
        savedStateHandle.set(STATE_OPERATION, Operation.IDLE.name());
    }

    public LiveData<Boolean> getOperationInFlight() {
        return operationInFlight;
    }

    public LiveData<UiEvent> getUiEvent() {
        return uiEvent;
    }

    public boolean isOperationInFlight() {
        return operation != Operation.IDLE;
    }

    public void setPendingDocumentSelection(
            String managerUserId,
            @Nullable ManagerDocumentFileType fileType
    ) {
        savedStateHandle.set(
                STATE_PENDING_FILE_TYPE,
                fileType == null ? "" : fileType.name()
        );
        savedStateHandle.set(
                STATE_PENDING_MANAGER_USER_ID,
                managerUserId == null ? "" : managerUserId
        );
    }

    @Nullable
    public PendingDocumentSelection consumePendingDocumentSelection() {
        String storedType = savedStateHandle.get(STATE_PENDING_FILE_TYPE);
        String managerUserId = savedStateHandle.get(STATE_PENDING_MANAGER_USER_ID);
        savedStateHandle.set(STATE_PENDING_FILE_TYPE, "");
        savedStateHandle.set(STATE_PENDING_MANAGER_USER_ID, "");
        if (storedType == null
                || storedType.isEmpty()
                || managerUserId == null
                || managerUserId.isEmpty()) {
            return null;
        }
        try {
            return new PendingDocumentSelection(
                    managerUserId,
                    ManagerDocumentFileType.valueOf(storedType)
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean uploadDocument(
            String managerUserId,
            ManagerDocumentFileType fileType,
            Uri fileUri
    ) {
        if (!beginOperation(Operation.UPLOAD)) {
            return false;
        }
        storageUploader.uploadDocument(
                managerUserId,
                fileType,
                fileUri,
                new RepositoryCallback<ManagerDocumentFileMetadata>() {
                    @Override
                    public void onSuccess(ManagerDocumentFileMetadata result) {
                        saveDraftMetadata(managerUserId, fileType, result);
                    }

                    @Override
                    public void onError(String message) {
                        finishWithError(message);
                    }
                }
        );
        return true;
    }

    private void saveDraftMetadata(
            String managerUserId,
            ManagerDocumentFileType fileType,
            ManagerDocumentFileMetadata metadata
    ) {
        managerRepository.saveManagerDocumentDraftFileMetadata(
                managerUserId,
                metadata,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        finishOperation();
                        uiEvent.setValue(UiEvent.uploadSaved(fileType));
                    }

                    @Override
                    public void onError(String message) {
                        finishWithError(message);
                    }
                }
        );
    }

    public boolean resolvePreview(ManagerDocumentFileMetadata metadata) {
        if (!beginOperation(Operation.PREVIEW)) {
            return false;
        }
        previewResolver.resolvePreviewUri(
                metadata,
                new RepositoryCallback<Uri>() {
                    @Override
                    public void onSuccess(Uri result) {
                        finishOperation();
                        uiEvent.setValue(UiEvent.previewReady(
                                result,
                                metadata.getContentType()
                        ));
                    }

                    @Override
                    public void onError(String message) {
                        finishWithError(message);
                    }
                }
        );
        return true;
    }

    public boolean submitRegistration(String managerUserId, String summary) {
        if (!beginOperation(Operation.SUBMISSION)) {
            return false;
        }
        managerRepository.saveManagerDocumentSummary(
                managerUserId,
                summary,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        finishOperation();
                        setCompletionPending(true);
                        uiEvent.setValue(UiEvent.submissionSucceeded());
                    }

                    @Override
                    public void onError(String message) {
                        finishWithError(message);
                    }
                }
        );
        return true;
    }

    /**
     * 프로세스 재생성으로 제출 콜백을 잃은 경우 최신 서버 상태로 잠금을 해제한다.
     */
    public void reconcileRecoveredSubmission(@Nullable ManagerHomeProfile profile) {
        if (!recoveringSubmission) {
            return;
        }
        recoveringSubmission = false;
        ManagerDocumentStatus status = profile == null
                ? ManagerDocumentStatus.NOT_SUBMITTED
                : profile.getDocumentStatus();
        finishOperation();
        if (status == ManagerDocumentStatus.PENDING_REVIEW
                || status == ManagerDocumentStatus.APPROVED) {
            setCompletionPending(true);
            return;
        }
        uiEvent.setValue(UiEvent.error(
                "이전 인증 요청 결과를 확인하지 못했습니다. 상태를 확인한 뒤 다시 요청해주세요."
        ));
    }

    public boolean isCompletionPending() {
        return Boolean.TRUE.equals(savedStateHandle.get(STATE_COMPLETION_PENDING));
    }

    public void clearCompletionPending() {
        setCompletionPending(false);
    }

    private void setCompletionPending(boolean pending) {
        savedStateHandle.set(STATE_COMPLETION_PENDING, pending);
    }

    private boolean beginOperation(Operation nextOperation) {
        if (operation != Operation.IDLE) {
            return false;
        }
        operation = nextOperation;
        savedStateHandle.set(STATE_OPERATION, nextOperation.name());
        operationInFlight.setValue(true);
        return true;
    }

    private void finishWithError(String message) {
        finishOperation();
        uiEvent.setValue(UiEvent.error(message));
    }

    private void finishOperation() {
        operation = Operation.IDLE;
        savedStateHandle.set(STATE_OPERATION, Operation.IDLE.name());
        operationInFlight.setValue(false);
    }

    private static Operation parseOperation(@Nullable String storedOperation) {
        if (storedOperation == null || storedOperation.isEmpty()) {
            return Operation.IDLE;
        }
        try {
            return Operation.valueOf(storedOperation);
        } catch (IllegalArgumentException ignored) {
            return Operation.IDLE;
        }
    }

    public static final class Factory extends androidx.lifecycle.AbstractSavedStateViewModelFactory {
        private final ManagerRepository managerRepository;
        private final ManagerDocumentStorageUploader storageUploader;
        private final ManagerDocumentPreviewResolver previewResolver;

        public Factory(
                androidx.savedstate.SavedStateRegistryOwner owner,
                ManagerRepository managerRepository,
                ManagerDocumentStorageUploader storageUploader,
                ManagerDocumentPreviewResolver previewResolver
        ) {
            super(owner, null);
            this.managerRepository = managerRepository;
            this.storageUploader = storageUploader;
            this.previewResolver = previewResolver;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        protected <T extends ViewModel> T create(
                @NonNull String key,
                @NonNull Class<T> modelClass,
                @NonNull SavedStateHandle handle
        ) {
            if (modelClass.isAssignableFrom(ManagerDocumentRegistrationViewModel.class)) {
                return (T) new ManagerDocumentRegistrationViewModel(
                        managerRepository,
                        storageUploader,
                        previewResolver,
                        handle
                );
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
