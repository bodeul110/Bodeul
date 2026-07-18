package com.example.bodeul.ui.manager;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.realtime.SupabaseCompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
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
    private final SupabaseCompanionRealtimeSubscriber realtimeSubscriber;

    private User currentUser;
    private boolean liveLocationShareInFlight = false;
    private PendingLocationUpdate pendingLiveLocationUpdate;
    private String subscribedSessionId = "";

    public ManagerGuideViewModel(
            AuthRepository authRepository,
            ManagerRepository managerRepository,
            ManagerGuideCoordinator coordinator,
            SupabaseCompanionRealtimeSubscriber realtimeSubscriber
    ) {
        this.authRepository = authRepository;
        this.managerRepository = managerRepository;
        this.coordinator = coordinator;
        this.realtimeSubscriber = realtimeSubscriber;
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
                ensureRealtimeSubscription(result);
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

    private void ensureRealtimeSubscription(ManagerDashboard dashboard) {
        String sessionId = dashboard == null || dashboard.getSession() == null
                ? ""
                : dashboard.getSession().getRealtimeSessionId();
        if (sessionId.isEmpty() || sessionId.equals(subscribedSessionId)) {
            return;
        }
        subscribedSessionId = sessionId;
        realtimeSubscriber.subscribe(sessionId, this::loadDashboard);
    }

    @Override
    protected void onCleared() {
        realtimeSubscriber.stop();
        super.onCleared();
    }

    public void advanceStep() {
        if (currentUser == null) return;
        managerRepository.advanceCurrentStep(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("?ㅼ쓬 ?④퀎濡??대룞?덉뒿?덈떎.");
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
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        managerRepository.saveLocationSummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("?꾩튂 ?붿빟????λ릺?덉뒿?덈떎.");
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
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        managerRepository.saveGuardianUpdate(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("蹂댄샇???뚮┝????λ릺?덉뒿?덈떎.");
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
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        managerRepository.saveFieldPhotoNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("?꾩옣 ?ъ쭊 硫붾え媛 ??λ릺?덉뒿?덈떎.");
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
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        managerRepository.saveMedicationNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("蹂듭빟 ?덈궡媛 ??λ릺?덉뒿?덈떎.");
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
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        managerRepository.savePharmacySummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("?쎄뎅 ?숉뻾 ?붿빟????λ릺?덉뒿?덈떎.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void togglePrescriptionCollected() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isPrescriptionCollected();
                managerRepository.updatePrescriptionCollected(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue
                                        ? "泥섎갑???섎졊???꾨즺濡??쒖떆?덉뒿?덈떎."
                                        : "泥섎갑???섎졊 ?꾩쑝濡??섎룎?몄뒿?덈떎.");
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
                                _toastMessage.setValue(nextValue ? "약 수령을 완료로 표시했습니다." : "약 수령 전으로 되돌렸습니다.");
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

    public void toggleMedicationGuidanceCompleted() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isMedicationGuidanceCompleted();
                managerRepository.updateMedicationGuidanceCompleted(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue
                                        ? "蹂듭빟 ?덈궡瑜??꾨즺濡??쒖떆?덉뒿?덈떎."
                                        : "蹂듭빟 ?덈궡 ?꾩쑝濡??섎룎?몄뒿?덈떎.");
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

    public void submitReport(
            String summary,
            String treatment,
            String medication,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            @Nullable MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisit
    ) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("?꾩닔 ?낅젰 ??ぉ??梨꾩썙二쇱꽭??");
            return;
        }
        boolean hasMedicationComparisonInput = !TextUtils.isEmpty(medication)
                || !TextUtils.isEmpty(medicationName)
                || !TextUtils.isEmpty(medicationChangeSummary)
                || !TextUtils.isEmpty(medicationScheduleNote);
        if (hasMedicationComparisonInput && medicationComparisonDecision == null) {
            _toastMessage.setValue("복약 대조 판단을 선택해 주세요.");
            return;
        }
        if (medicationComparisonDecision == MedicationComparisonDecision.RECHECK_REQUIRED
                && TextUtils.isEmpty(medicationComparisonNote)) {
            _toastMessage.setValue("재확인 사유를 입력해 주세요.");
            return;
        }
        managerRepository.submitSessionReport(
                currentUser.getId(),
                summary,
                treatment,
                medication,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisit,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        _toastMessage.setValue("?숉뻾 ?쇱?媛 ?쒖텧?섏뿀?듬땲??");
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
                        _toastMessage.setValue("?꾩옱 ?꾩튂瑜?怨듭쑀?덉뒿?덈떎.");
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
                            _toastMessage.setValue("?ㅼ떆媛??꾩튂 怨듭쑀媛 ?쒖옉?섏뿀?듬땲??");
                        } else {
                            _toastMessage.setValue("?ㅼ떆媛??꾩튂 怨듭쑀媛 以묒??섏뿀?듬땲??");
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
        private final SupabaseCompanionRealtimeSubscriber realtimeSubscriber;

        public Factory(
                AuthRepository authRepository,
                ManagerRepository managerRepository,
                ManagerGuideCoordinator coordinator,
                SupabaseCompanionRealtimeSubscriber realtimeSubscriber
        ) {
            this.authRepository = authRepository;
            this.managerRepository = managerRepository;
            this.coordinator = coordinator;
            this.realtimeSubscriber = realtimeSubscriber;
        }

        @androidx.annotation.NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@androidx.annotation.NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ManagerGuideViewModel.class)) {
                return (T) new ManagerGuideViewModel(
                        authRepository,
                        managerRepository,
                        coordinator,
                        realtimeSubscriber);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
