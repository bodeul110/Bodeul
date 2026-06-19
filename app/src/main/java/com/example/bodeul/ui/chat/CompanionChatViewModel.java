package com.example.bodeul.ui.chat;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;

public class CompanionChatViewModel extends ViewModel {

    public enum StatePanelType {
        NONE,
        PERMISSION,
        AUTH,
        EMPTY,
        LOAD_ERROR
    }

    public static class UiState {
        public final boolean isLoading;
        @Nullable
        public final CompanionChatScreenModel screenModel;
        public final StatePanelType statePanelType;
        @Nullable
        public final String errorMessage;
        public final boolean requireProfileCompletion;

        public UiState(
                boolean isLoading,
                @Nullable CompanionChatScreenModel screenModel,
                StatePanelType statePanelType,
                @Nullable String errorMessage,
                boolean requireProfileCompletion
        ) {
            this.isLoading = isLoading;
            this.screenModel = screenModel;
            this.statePanelType = statePanelType;
            this.errorMessage = errorMessage;
            this.requireProfileCompletion = requireProfileCompletion;
        }

        public static UiState loading() {
            return new UiState(true, null, StatePanelType.NONE, null, false);
        }

        public static UiState screen(CompanionChatScreenModel screenModel) {
            return new UiState(false, screenModel, StatePanelType.NONE, null, false);
        }

        public static UiState panel(StatePanelType type, @Nullable String errorMessage) {
            return new UiState(false, null, type, errorMessage, false);
        }

        public static UiState profileCompletion() {
            return new UiState(false, null, StatePanelType.NONE, null, true);
        }
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<Long> messageSentEvent = new MutableLiveData<>();

    private final AuthRepository authRepository;
    private final BookingRepository bookingRepository;
    private final ManagerRepository managerRepository;
    private final CompanionChatAttachmentUploader attachmentUploader;
    private final CompanionChatCoordinator coordinator;

    @Nullable
    private User currentUser;
    @Nullable
    private String requestId;
    private String currentSessionId = "";

    public CompanionChatViewModel(
            AuthRepository authRepository,
            BookingRepository bookingRepository,
            ManagerRepository managerRepository,
            CompanionChatAttachmentUploader attachmentUploader,
            CompanionChatCoordinator coordinator
    ) {
        this.authRepository = authRepository;
        this.bookingRepository = bookingRepository;
        this.managerRepository = managerRepository;
        this.attachmentUploader = attachmentUploader;
        this.coordinator = coordinator;
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Long> getMessageSentEvent() {
        return messageSentEvent;
    }

    public void init(@Nullable String requestId) {
        this.requestId = requestId;
        reload();
    }

    public void reload() {
        uiState.setValue(UiState.loading());
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    uiState.setValue(UiState.profileCompletion());
                    return;
                }

                currentUser = result;
                if (result.getRole() == UserRole.MANAGER) {
                    loadManagerDashboard(result);
                    return;
                }
                if (result.getRole() == UserRole.PATIENT || result.getRole() == UserRole.GUARDIAN) {
                    if (TextUtils.isEmpty(requestId)) {
                        uiState.setValue(UiState.panel(
                                StatePanelType.LOAD_ERROR,
                                "예약 정보를 확인하지 못했습니다."
                        ));
                        return;
                    }
                    loadBookingDetail(result);
                    return;
                }
                uiState.setValue(UiState.panel(StatePanelType.PERMISSION, null));
            }

            @Override
            public void onError(String message) {
                uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            }
        });
    }

    public void sendMessage(String message, @Nullable CompanionChatPendingAttachment pendingAttachment) {
        if (currentUser == null) {
            return;
        }

        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isEmpty() && pendingAttachment == null) {
            toastMessage.setValue("메시지나 첨부 파일을 준비해 주세요.");
            return;
        }

        setLoadingWithCurrentScreen();
        if (pendingAttachment != null) {
            uploadPendingAttachmentAndSend(normalizedMessage, pendingAttachment);
            return;
        }
        sendMessageInternal(normalizedMessage, null);
    }

    public void toastMessageHandled() {
        toastMessage.setValue(null);
    }

    public void messageSentHandled() {
        messageSentEvent.setValue(null);
    }

    private void loadBookingDetail(User user) {
        bookingRepository.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                currentSessionId = result.getSession() == null ? "" : result.getSession().getId();
                uiState.setValue(UiState.screen(coordinator.createForBooking(
                        user,
                        result,
                        bookingRepository.isFirebaseBacked()
                )));
                bookingRepository.markCompanionChatRead(user, requestId);
            }

            @Override
            public void onError(String message) {
                currentSessionId = "";
                uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, message));
            }
        });
    }

    private void loadManagerDashboard(User user) {
        managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                currentSessionId = result.getSession() == null ? "" : result.getSession().getId();
                uiState.setValue(UiState.screen(coordinator.createForManager(
                        user,
                        result,
                        managerRepository.isFirebaseBacked()
                )));
                managerRepository.markCompanionChatRead(user.getId());
            }

            @Override
            public void onError(String message) {
                currentSessionId = "";
                if (ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message)) {
                    uiState.setValue(UiState.panel(StatePanelType.EMPTY, null));
                    return;
                }
                uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, message));
            }
        });
    }

    private void uploadPendingAttachmentAndSend(
            String normalizedMessage,
            CompanionChatPendingAttachment pendingAttachment
    ) {
        if (TextUtils.isEmpty(currentSessionId)) {
            restoreCurrentScreen();
            toastMessage.setValue("첨부 파일 세션 정보를 확인하지 못했습니다.");
            return;
        }

        attachmentUploader.uploadAttachment(
                currentSessionId,
                pendingAttachment.getFileUri(),
                new RepositoryCallback<CompanionChatAttachment>() {
                    @Override
                    public void onSuccess(CompanionChatAttachment result) {
                        sendMessageInternal(normalizedMessage, result);
                    }

                    @Override
                    public void onError(String message) {
                        restoreCurrentScreen();
                        toastMessage.setValue(message);
                    }
                }
        );
    }

    private void sendMessageInternal(
            String normalizedMessage,
            @Nullable CompanionChatAttachment attachment
    ) {
        if (currentUser == null) {
            restoreCurrentScreen();
            return;
        }

        if (currentUser.getRole() == UserRole.MANAGER) {
            managerRepository.sendCompanionChatMessage(
                    currentUser.getId(),
                    normalizedMessage,
                    attachment,
                    new RepositoryCallback<ManagerDashboard>() {
                        @Override
                        public void onSuccess(ManagerDashboard result) {
                            currentSessionId = result.getSession() == null ? "" : result.getSession().getId();
                            uiState.setValue(UiState.screen(coordinator.createForManager(
                                    currentUser,
                                    result,
                                    managerRepository.isFirebaseBacked()
                            )));
                            messageSentEvent.setValue(System.currentTimeMillis());
                        }

                        @Override
                        public void onError(String errorMessage) {
                            restoreCurrentScreen();
                            toastMessage.setValue(errorMessage);
                        }
                    }
            );
            return;
        }

        bookingRepository.sendCompanionChatMessage(
                currentUser,
                requestId,
                normalizedMessage,
                attachment,
                new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail result) {
                        currentSessionId = result.getSession() == null ? "" : result.getSession().getId();
                        uiState.setValue(UiState.screen(coordinator.createForBooking(
                                currentUser,
                                result,
                                bookingRepository.isFirebaseBacked()
                        )));
                        messageSentEvent.setValue(System.currentTimeMillis());
                    }

                    @Override
                    public void onError(String errorMessage) {
                        restoreCurrentScreen();
                        toastMessage.setValue(errorMessage);
                    }
                }
        );
    }

    private void setLoadingWithCurrentScreen() {
        UiState currentState = uiState.getValue();
        uiState.setValue(new UiState(
                true,
                currentState == null ? null : currentState.screenModel,
                StatePanelType.NONE,
                null,
                false
        ));
    }

    private void restoreCurrentScreen() {
        UiState currentState = uiState.getValue();
        uiState.setValue(new UiState(
                false,
                currentState == null ? null : currentState.screenModel,
                StatePanelType.NONE,
                null,
                false
        ));
    }

    public static class Factory implements androidx.lifecycle.ViewModelProvider.Factory {
        private final AuthRepository authRepository;
        private final BookingRepository bookingRepository;
        private final ManagerRepository managerRepository;
        private final CompanionChatAttachmentUploader attachmentUploader;
        private final CompanionChatCoordinator coordinator;

        public Factory(
                AuthRepository authRepository,
                BookingRepository bookingRepository,
                ManagerRepository managerRepository,
                CompanionChatAttachmentUploader attachmentUploader,
                CompanionChatCoordinator coordinator
        ) {
            this.authRepository = authRepository;
            this.bookingRepository = bookingRepository;
            this.managerRepository = managerRepository;
            this.attachmentUploader = attachmentUploader;
            this.coordinator = coordinator;
        }

        @androidx.annotation.NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@androidx.annotation.NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CompanionChatViewModel.class)) {
                return (T) new CompanionChatViewModel(
                        authRepository,
                        bookingRepository,
                        managerRepository,
                        attachmentUploader,
                        coordinator
                );
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
