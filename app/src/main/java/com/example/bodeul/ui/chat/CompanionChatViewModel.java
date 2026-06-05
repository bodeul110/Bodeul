package com.example.bodeul.ui.chat;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
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

        public UiState(boolean isLoading, @Nullable CompanionChatScreenModel screenModel, StatePanelType statePanelType, @Nullable String errorMessage, boolean requireProfileCompletion) {
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

    private final MutableLiveData<UiState> _uiState = new MutableLiveData<>(UiState.loading());
    public LiveData<UiState> getUiState() {
        return _uiState;
    }

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public LiveData<String> getToastMessage() {
        return _toastMessage;
    }

    private final AuthRepository authRepository;
    private final BookingRepository bookingRepository;
    private final ManagerRepository managerRepository;
    private final CompanionChatCoordinator coordinator;

    private User currentUser;
    private String requestId;

    public CompanionChatViewModel(AuthRepository authRepository, BookingRepository bookingRepository, ManagerRepository managerRepository, CompanionChatCoordinator coordinator) {
        this.authRepository = authRepository;
        this.bookingRepository = bookingRepository;
        this.managerRepository = managerRepository;
        this.coordinator = coordinator;
    }

    public void init(String requestId) {
        this.requestId = requestId;
        reload();
    }

    public void reload() {
        _uiState.setValue(UiState.loading());
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    _uiState.setValue(UiState.profileCompletion());
                    return;
                }
                currentUser = result;
                if (result.getRole() == UserRole.MANAGER) {
                    loadManagerDashboard(result);
                    return;
                }
                if (result.getRole() == UserRole.PATIENT || result.getRole() == UserRole.GUARDIAN) {
                    if (TextUtils.isEmpty(requestId)) {
                        _uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, "예약 정보가 없습니다."));
                        return;
                    }
                    loadBookingDetail(result);
                    return;
                }
                _uiState.setValue(UiState.panel(StatePanelType.PERMISSION, null));
            }

            @Override
            public void onError(String message) {
                _uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            }
        });
    }

    private void loadBookingDetail(User user) {
        bookingRepository.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                _uiState.setValue(UiState.screen(coordinator.createForBooking(
                        user,
                        result,
                        bookingRepository.isFirebaseBacked()
                )));
            }

            @Override
            public void onError(String message) {
                _uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, message));
            }
        });
    }

    private void loadManagerDashboard(User user) {
        managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _uiState.setValue(UiState.screen(coordinator.createForManager(
                        user,
                        result,
                        managerRepository.isFirebaseBacked()
                )));
            }

            @Override
            public void onError(String message) {
                if (ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message)) {
                    _uiState.setValue(UiState.panel(StatePanelType.EMPTY, null));
                    return;
                }
                _uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, message));
            }
        });
    }

    public void sendMessage(String message) {
        if (currentUser == null) {
            return;
        }

        if (message.trim().isEmpty()) {
            _toastMessage.setValue("메시지를 입력해주세요.");
            return;
        }

        UiState currentState = _uiState.getValue();
        _uiState.setValue(new UiState(true, currentState != null ? currentState.screenModel : null, StatePanelType.NONE, null, false));

        if (currentUser.getRole() == UserRole.MANAGER) {
            managerRepository.sendCompanionChatMessage(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
                @Override
                public void onSuccess(ManagerDashboard result) {
                    _uiState.setValue(UiState.screen(coordinator.createForManager(
                            currentUser,
                            result,
                            managerRepository.isFirebaseBacked()
                    )));
                }

                @Override
                public void onError(String errorMessage) {
                    UiState prev = _uiState.getValue();
                    _uiState.setValue(new UiState(false, prev != null ? prev.screenModel : null, StatePanelType.NONE, null, false));
                    _toastMessage.setValue(errorMessage);
                }
            });
            return;
        }

        bookingRepository.sendCompanionChatMessage(currentUser, requestId, message, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override
            public void onSuccess(AppointmentRequestDetail result) {
                _uiState.setValue(UiState.screen(coordinator.createForBooking(
                        currentUser,
                        result,
                        bookingRepository.isFirebaseBacked()
                )));
            }

            @Override
            public void onError(String errorMessage) {
                UiState prev = _uiState.getValue();
                _uiState.setValue(new UiState(false, prev != null ? prev.screenModel : null, StatePanelType.NONE, null, false));
                _toastMessage.setValue(errorMessage);
            }
        });
    }

    public void toastMessageHandled() {
        _toastMessage.setValue(null);
    }

    public static class Factory implements androidx.lifecycle.ViewModelProvider.Factory {
        private final AuthRepository authRepository;
        private final BookingRepository bookingRepository;
        private final ManagerRepository managerRepository;
        private final CompanionChatCoordinator coordinator;

        public Factory(AuthRepository authRepository, BookingRepository bookingRepository, ManagerRepository managerRepository, CompanionChatCoordinator coordinator) {
            this.authRepository = authRepository;
            this.bookingRepository = bookingRepository;
            this.managerRepository = managerRepository;
            this.coordinator = coordinator;
        }

        @androidx.annotation.NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@androidx.annotation.NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CompanionChatViewModel.class)) {
                return (T) new CompanionChatViewModel(authRepository, bookingRepository, managerRepository, coordinator);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
