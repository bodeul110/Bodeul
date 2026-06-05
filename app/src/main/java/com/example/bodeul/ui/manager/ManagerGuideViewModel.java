package com.example.bodeul.ui.manager;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;

public class ManagerGuideViewModel extends ViewModel {

    public enum StatePanelType {
        NONE,
        PERMISSION,
        AUTH,
        EMPTY,
        LOAD_ERROR
    }

    public static class UiState {
        @Nullable
        public final ManagerDashboard dashboard;
        @Nullable
        public final ManagerGuideScreenModel screenModel;
        public final StatePanelType statePanelType;
        @Nullable
        public final String errorMessage;
        public final boolean requireProfileCompletion;

        public UiState(@Nullable ManagerDashboard dashboard, @Nullable ManagerGuideScreenModel screenModel, StatePanelType statePanelType, @Nullable String errorMessage, boolean requireProfileCompletion) {
            this.dashboard = dashboard;
            this.screenModel = screenModel;
            this.statePanelType = statePanelType;
            this.errorMessage = errorMessage;
            this.requireProfileCompletion = requireProfileCompletion;
        }

        public static UiState loading() {
            return new UiState(null, null, StatePanelType.NONE, null, false);
        }

        public static UiState screen(ManagerDashboard dashboard, ManagerGuideScreenModel screenModel) {
            return new UiState(dashboard, screenModel, StatePanelType.NONE, null, false);
        }

        public static UiState panel(StatePanelType type, @Nullable String errorMessage) {
            return new UiState(null, null, type, errorMessage, false);
        }

        public static UiState profileCompletion() {
            return new UiState(null, null, StatePanelType.NONE, null, true);
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
    private final ManagerRepository managerRepository;
    private final ManagerGuideCoordinator coordinator;

    private User currentUser;
    private boolean liveLocationShareInFlight = false;
    private PendingLocationUpdate pendingLiveLocationUpdate;

    public ManagerGuideViewModel(AuthRepository authRepository, ManagerRepository managerRepository, ManagerGuideCoordinator coordinator) {
        this.authRepository = authRepository;
        this.managerRepository = managerRepository;
        this.coordinator = coordinator;
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
                if (result.getRole() != UserRole.MANAGER) {
                    _uiState.setValue(UiState.panel(StatePanelType.PERMISSION, null));
                    return;
                }

                currentUser = result;
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                _uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            }
        });
    }

    public void loadDashboard() {
        if (currentUser == null) {
            _uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            return;
        }

        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                bindDashboard(result);
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

    private void bindDashboard(@Nullable ManagerDashboard dashboard) {
        if (dashboard == null) {
            _uiState.setValue(UiState.panel(StatePanelType.EMPTY, null));
            return;
        }
        _uiState.setValue(UiState.screen(dashboard, coordinator.createScreenModel(
                dashboard,
                managerRepository.isFirebaseBacked()
        )));
    }

    public void advanceStep() {
        if (currentUser == null) return;
        managerRepository.advanceCurrentStep(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("다음 단계로 이동했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void saveLocationSummary(String summary) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.saveLocationSummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("위치 요약이 저장되었습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void saveGuardianUpdate(String message) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(message)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.saveGuardianUpdate(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("보호자 알림이 저장되었습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String errorMessage) {
                _toastMessage.setValue(errorMessage);
            }
        });
    }

    public void saveFieldPhotoNote(String note) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(note)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.saveFieldPhotoNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("현장 사진 메모가 저장되었습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void saveMedicationNote(String note) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(note)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.saveMedicationNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("복약 안내가 저장되었습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void savePharmacySummary(String summary) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.savePharmacySummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("약국 동행 요약이 저장되었습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void togglePharmacyCompleted() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isPharmacyCompleted();
                managerRepository.updatePharmacyCompleted(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue ? "약국 수령 완료" : "약국 수령 미완료 변경");
                                bindDashboard(updated);
                            }

                            @Override
                            public void onError(String message) {
                                _toastMessage.setValue(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void submitReport(String summary, String treatment, String medication, String nextVisit) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("필수 입력 항목을 채워주세요.");
            return;
        }
        managerRepository.submitSessionReport(
                currentUser.getId(),
                summary,
                treatment,
                medication,
                nextVisit,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        _toastMessage.setValue("동행 일지가 제출되었습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        _toastMessage.setValue(message);
                    }
                }
        );
    }

    public void shareCurrentLocation(double latitude, double longitude, String summary) {
        if (currentUser == null) return;
        managerRepository.shareCurrentLocation(
                currentUser.getId(),
                latitude,
                longitude,
                summary,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        _toastMessage.setValue("현재 위치를 공유했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        _toastMessage.setValue(message);
                    }
                }
        );
    }

    public void updateLiveLocationSharingState(boolean active, Runnable onActivationComplete, Runnable onActivationFailed) {
        if (currentUser == null) {
            if (onActivationFailed != null) onActivationFailed.run();
            return;
        }
        managerRepository.updateLiveLocationSharingState(
                currentUser.getId(),
                active,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        if (active) {
                            _toastMessage.setValue("실시간 위치 공유가 시작되었습니다.");
                        } else {
                            _toastMessage.setValue("실시간 위치 공유가 중지되었습니다.");
                        }
                        bindDashboard(result);
                        if (onActivationComplete != null) onActivationComplete.run();
                    }

                    @Override
                    public void onError(String message) {
                        _toastMessage.setValue(message);
                        if (onActivationFailed != null) onActivationFailed.run();
                    }
                }
        );
    }

    public void enqueueLiveLocationShare(double latitude, double longitude, String summary, boolean isTrackerRunning) {
        if (currentUser == null) return;
        PendingLocationUpdate update = new PendingLocationUpdate(latitude, longitude, summary);
        if (liveLocationShareInFlight) {
            pendingLiveLocationUpdate = update;
            return;
        }
        dispatchLiveLocationShare(update, isTrackerRunning);
    }

    private void dispatchLiveLocationShare(PendingLocationUpdate update, boolean isTrackerRunning) {
        if (currentUser == null) return;
        liveLocationShareInFlight = true;
        managerRepository.shareCurrentLocation(
                currentUser.getId(),
                update.latitude,
                update.longitude,
                update.summary,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        liveLocationShareInFlight = false;
                        bindDashboard(result);
                        flushPendingLiveLocationShare(isTrackerRunning);
                    }

                    @Override
                    public void onError(String message) {
                        liveLocationShareInFlight = false;
                        _toastMessage.setValue(message);
                        flushPendingLiveLocationShare(isTrackerRunning);
                    }
                }
        );
    }

    private void flushPendingLiveLocationShare(boolean isTrackerRunning) {
        PendingLocationUpdate nextUpdate = pendingLiveLocationUpdate;
        pendingLiveLocationUpdate = null;
        if (nextUpdate != null && isTrackerRunning) {
            dispatchLiveLocationShare(nextUpdate, true);
        }
    }

    public void resetLiveLocationInFlight() {
        liveLocationShareInFlight = false;
        pendingLiveLocationUpdate = null;
    }

    public void toastMessageHandled() {
        _toastMessage.setValue(null);
    }

    private static class PendingLocationUpdate {
        final double latitude;
        final double longitude;
        final String summary;

        PendingLocationUpdate(double latitude, double longitude, String summary) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.summary = summary;
        }
    }

    public static class Factory implements androidx.lifecycle.ViewModelProvider.Factory {
        private final AuthRepository authRepository;
        private final ManagerRepository managerRepository;
        private final ManagerGuideCoordinator coordinator;

        public Factory(AuthRepository authRepository, ManagerRepository managerRepository, ManagerGuideCoordinator coordinator) {
            this.authRepository = authRepository;
            this.managerRepository = managerRepository;
            this.coordinator = coordinator;
        }

        @androidx.annotation.NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@androidx.annotation.NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ManagerGuideViewModel.class)) {
                return (T) new ManagerGuideViewModel(authRepository, managerRepository, coordinator);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
