package com.example.bodeul.ui.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.map.HospitalMapCoordinateQuery;
import com.example.bodeul.data.map.HospitalMapCoordinateResult;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.LabelLayer;

public class ManagerGuideActivity extends AppCompatActivity {
    private static final int REQUEST_FINE_LOCATION = 1001;
    private static final int LOCATION_ACTION_NONE = 0;
    private static final int LOCATION_ACTION_SHARE_ONCE = 1;
    private static final int LOCATION_ACTION_START_LIVE = 2;

    private ManagerGuideViewModel viewModel;
    private ManagerGuideDashboardBinder managerGuideDashboardBinder;

    private int pendingLocationPermissionAction = LOCATION_ACTION_NONE;
    private boolean liveLocationActivationInFlight;
    private boolean activityVisible;

    private View managerGuideStatePanel;
    private View managerGuideContentContainer;
    private TextInputEditText inputGuideLocationSummary;
    private TextInputEditText inputGuardianUpdate;
    private TextInputEditText inputGuidePhotoNote;
    private TextInputEditText inputMedicationNote;
    private TextInputEditText inputPharmacySummary;
    private TextInputEditText inputReportSummary;
    private TextInputEditText inputReportTreatment;
    private TextInputEditText inputReportMedicationName;
    private TextInputEditText inputReportMedicationChangeSummary;
    private TextInputEditText inputReportMedicationScheduleNote;
    private TextInputEditText inputNextVisit;

    private MapView mapView;
    private KakaoMap kakaoMap;
    private Label managerMarker;
    private Label hospitalMarker;
    private Label pharmacyMarker;
    private Label trackingLabel;
    private ManagerDashboard currentDashboard;
    private KakaoLocalPlaceSearchClient placeSearchClient;
    private HospitalMapCoordinateResult currentCoordinateResult;
    private String currentCoordinateQueryKey = "";
    private boolean coordinateSearchInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_guide);

        AuthRepository authRepository = ServiceLocator.provideAuthRepository(this);
        ManagerRepository managerRepository = ServiceLocator.provideManagerRepository(this);
        placeSearchClient = new KakaoLocalPlaceSearchClient(this);
        ManagerGuideCoordinator coordinator = new ManagerGuideCoordinator(
                this,
                new ManagerGuidePresentationFormatter(this)
        );

        ManagerGuideViewModel.Factory factory = new ManagerGuideViewModel.Factory(
                authRepository, managerRepository, coordinator
        );
        viewModel = new ViewModelProvider(this, factory).get(ManagerGuideViewModel.class);

        managerGuideStatePanel = findViewById(R.id.managerGuideStatePanel);
        managerGuideContentContainer = findViewById(R.id.managerGuideContentContainer);
        inputGuideLocationSummary = findViewById(R.id.inputGuideLocationSummary);
        inputGuardianUpdate = findViewById(R.id.inputGuardianUpdate);
        inputGuidePhotoNote = findViewById(R.id.inputGuidePhotoNote);
        inputMedicationNote = findViewById(R.id.inputMedicationNote);
        inputPharmacySummary = findViewById(R.id.inputPharmacySummary);
        inputReportSummary = findViewById(R.id.inputReportSummary);
        inputReportTreatment = findViewById(R.id.inputReportTreatment);
        inputReportMedicationName = findViewById(R.id.inputReportMedicationName);
        inputReportMedicationChangeSummary = findViewById(R.id.inputReportMedicationChangeSummary);
        inputReportMedicationScheduleNote = findViewById(R.id.inputReportMedicationScheduleNote);
        inputNextVisit = findViewById(R.id.inputNextVisit);

        managerGuideDashboardBinder = new ManagerGuideDashboardBinder(
                LayoutInflater.from(this),
                new ManagerGuideStageItemBinder(this),
                findViewById(R.id.textGuideMode),
                findViewById(R.id.textGuideTitle),
                findViewById(R.id.textGuideSubtitle),
                findViewById(R.id.textGuideHeroBadge),
                findViewById(R.id.textGuideHeroTitle),
                findViewById(R.id.textGuideHeroBody),
                findViewById(R.id.textGuideHeroNote),
                findViewById(R.id.viewGuideHospitalMap),
                findViewById(R.id.guideMapActionContainer),
                new ManagerGuideMapActionBinder(this::openMapFallback),
                (LinearLayout) findViewById(R.id.guideStageRailContainer),
                findViewById(R.id.textGuideFocusBadge),
                findViewById(R.id.textGuideFocusTitle),
                findViewById(R.id.textGuideFocusBody),
                findViewById(R.id.textGuideFocusPreviewLabel),
                findViewById(R.id.textGuideFocusPreviewBody),
                findViewById(R.id.viewGuideFocusPreview),
                findViewById(R.id.textGuideLiveLocationStatus),
                findViewById(R.id.textGuideLiveLocationHistory),
                inputGuideLocationSummary,
                inputGuardianUpdate,
                inputGuidePhotoNote,
                inputMedicationNote,
                inputPharmacySummary,
                findViewById(R.id.textGuidePharmacyProgressSummary),
                inputReportSummary,
                inputReportTreatment,
                inputReportMedicationName,
                inputReportMedicationChangeSummary,
                inputReportMedicationScheduleNote,
                inputNextVisit,
                (MaterialButton) findViewById(R.id.buttonAdvanceGuide),
                (MaterialButton) findViewById(R.id.buttonSaveLocationSummary),
                (MaterialButton) findViewById(R.id.buttonShareCurrentLocation),
                (MaterialButton) findViewById(R.id.buttonStartLiveLocationSharing),
                (MaterialButton) findViewById(R.id.buttonStopLiveLocationSharing),
                (MaterialButton) findViewById(R.id.buttonSaveGuardianUpdate),
                (MaterialButton) findViewById(R.id.buttonSaveGuidePhotoNote),
                (MaterialButton) findViewById(R.id.buttonSaveMedicationNote),
                (MaterialButton) findViewById(R.id.buttonSavePharmacySummary),
                (MaterialButton) findViewById(R.id.buttonTogglePrescriptionCollected),
                (MaterialButton) findViewById(R.id.buttonTogglePharmacyCompleted),
                (MaterialButton) findViewById(R.id.buttonToggleMedicationGuidanceCompleted),
                (MaterialButton) findViewById(R.id.buttonSubmitReport)
        );

        findViewById(R.id.buttonBackGuide).setOnClickListener(view -> finish());
        findViewById(R.id.buttonAdvanceGuide).setOnClickListener(view -> viewModel.advanceStep());
        findViewById(R.id.buttonSaveLocationSummary).setOnClickListener(view -> viewModel.saveLocationSummary(valueOf(inputGuideLocationSummary)));
        findViewById(R.id.buttonSaveGuardianUpdate).setOnClickListener(view -> viewModel.saveGuardianUpdate(valueOf(inputGuardianUpdate)));
        findViewById(R.id.buttonSaveGuidePhotoNote).setOnClickListener(view -> viewModel.saveFieldPhotoNote(valueOf(inputGuidePhotoNote)));
        findViewById(R.id.buttonSaveMedicationNote).setOnClickListener(view -> viewModel.saveMedicationNote(valueOf(inputMedicationNote)));
        findViewById(R.id.buttonSavePharmacySummary).setOnClickListener(view -> viewModel.savePharmacySummary(valueOf(inputPharmacySummary)));
        findViewById(R.id.buttonTogglePrescriptionCollected).setOnClickListener(view -> viewModel.togglePrescriptionCollected());
        findViewById(R.id.buttonTogglePharmacyCompleted).setOnClickListener(view -> viewModel.togglePharmacyCompleted());
        findViewById(R.id.buttonToggleMedicationGuidanceCompleted).setOnClickListener(view -> viewModel.toggleMedicationGuidanceCompleted());
        findViewById(R.id.buttonSubmitReport).setOnClickListener(view -> viewModel.submitReport(
                valueOf(inputReportSummary),
                valueOf(inputReportTreatment),
                valueOf(inputMedicationNote),
                valueOf(inputReportMedicationName),
                valueOf(inputReportMedicationChangeSummary),
                valueOf(inputReportMedicationScheduleNote),
                valueOf(inputNextVisit)
        ));
        findViewById(R.id.buttonGuideOpenChat).setOnClickListener(view -> openCompanionChat());
        findViewById(R.id.buttonShareCurrentLocation).setOnClickListener(view -> shareCurrentLocation());
        findViewById(R.id.buttonStartLiveLocationSharing).setOnClickListener(view -> startLiveLocationSharing());
        findViewById(R.id.buttonStopLiveLocationSharing).setOnClickListener(view -> stopLiveLocationSharing(true, true));

        mapView = findViewById(R.id.mapViewManagerGuide);
        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
            }

            @Override
            public void onMapError(Exception e) {
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(KakaoMap map) {
                kakaoMap = map;
                mapView.setVisibility(View.VISIBLE);
                updateMapMarker();
                if (hasLocationPermission()) {
                    startMapTracking();
                }
            }
        });

        viewModel.getUiState().observe(this, this::handleUiState);
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.toastMessageHandled();
            }
        });

        if (savedInstanceState == null) {
            // Note: viewModel.reload() will be called in onStart()
        }
    }

    private void handleUiState(ManagerGuideViewModel.UiState state) {
        if (state == null) return;

        if (state.requireProfileCompletion) {
            openProfileCompletion();
            return;
        }

        if (state.statePanelType != ManagerGuideViewModel.StatePanelType.NONE) {
            managerGuideContentContainer.setVisibility(View.GONE);
            switch (state.statePanelType) {
                case PERMISSION:
                    showPermissionState();
                    break;
                case AUTH:
                    showAuthState();
                    break;
                case EMPTY:
                    showEmptyState();
                    break;
                case LOAD_ERROR:
                    showLoadErrorState(state.errorMessage);
                    break;
                default:
                    StatePanelHelper.hide(managerGuideStatePanel);
                    break;
            }
        } else {
            StatePanelHelper.hide(managerGuideStatePanel);
            if (state.screenModel != null) {
                managerGuideContentContainer.setVisibility(View.VISIBLE);
                managerGuideDashboardBinder.bindScreen(state.screenModel);
                currentDashboard = state.dashboard;
                updateMapMarker();
            } else {
                managerGuideContentContainer.setVisibility(View.GONE);
            }
        }

        syncLiveLocationTrackingWithDashboard(state.dashboard);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void updateMapMarker() {
        if (kakaoMap == null || currentDashboard == null) return;

        updateSharedLocationMarker();
        updateHospitalAndPharmacyMarkers();
    }

    private void updateSharedLocationMarker() {
        if (kakaoMap == null || currentDashboard == null) {
            return;
        }

        CompanionSession session = currentDashboard.getSession();
        if (session == null) return;

        Double lat = session.getSharedLatitude();
        Double lng = session.getSharedLongitude();

        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            LatLng position = LatLng.from(lat, lng);
            LabelManager labelManager = kakaoMap.getLabelManager();
            LabelLayer layer = labelManager.getLayer();

            if (managerMarker == null) {
                android.graphics.Bitmap markerBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_map_marker);
                LabelOptions options = LabelOptions.from(position);
                if (markerBitmap != null) {
                    options.setStyles(LabelStyle.from(markerBitmap));
                } else {
                    options.setStyles(LabelStyle.from(R.drawable.ic_map_marker));
                }
                managerMarker = layer.addLabel(options);
            } else {
                managerMarker.moveTo(position);
            }
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(position));
        }
    }

    private void updateHospitalAndPharmacyMarkers() {
        if (kakaoMap == null || currentDashboard == null) {
            return;
        }
        HospitalMapCoordinateQuery query = new HospitalMapCoordinateQuery(
                currentDashboard.getAppointmentRequest().getHospitalName(),
                currentDashboard.getAppointmentRequest().getDepartmentName()
        );
        if (query.isEmpty() || !placeSearchClient.isConfigured()) {
            return;
        }

        String queryKey = query.buildPrimaryHospitalQuery();
        if (TextUtils.equals(currentCoordinateQueryKey, queryKey) && currentCoordinateResult != null) {
            renderHospitalAndPharmacyMarkers(currentCoordinateResult);
            return;
        }
        if (TextUtils.equals(currentCoordinateQueryKey, queryKey) && coordinateSearchInFlight) {
            return;
        }

        currentCoordinateQueryKey = queryKey;
        coordinateSearchInFlight = true;
        placeSearchClient.searchHospitalAndPharmacy(query, new KakaoLocalPlaceSearchClient.Callback() {
            @Override
            public void onSuccess(HospitalMapCoordinateResult result) {
                if (!TextUtils.equals(currentCoordinateQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = result;
                renderHospitalAndPharmacyMarkers(result);
            }

            @Override
            public void onError(String message) {
                if (!TextUtils.equals(currentCoordinateQueryKey, queryKey)) {
                    return;
                }
                coordinateSearchInFlight = false;
                currentCoordinateResult = new HospitalMapCoordinateResult(null, null);
            }
        });
    }

    private void renderHospitalAndPharmacyMarkers(HospitalMapCoordinateResult result) {
        if (kakaoMap == null || result == null) {
            return;
        }
        if (result.getHospitalCoordinate() != null) {
            hospitalMarker = upsertPlaceMarker(
                    hospitalMarker,
                    "guide-hospital",
                    result.getHospitalCoordinate(),
                    R.drawable.ic_map_marker_hospital
            );
        }
        if (result.getPharmacyCoordinate() != null) {
            pharmacyMarker = upsertPlaceMarker(
                    pharmacyMarker,
                    "guide-pharmacy",
                    result.getPharmacyCoordinate(),
                    R.drawable.ic_map_marker_pharmacy
            );
        }
        if ((currentDashboard.getSession() == null || !currentDashboard.getSession().hasSharedLocationCoordinates())
                && result.getHospitalCoordinate() != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(
                    result.getHospitalCoordinate().getLatitude(),
                    result.getHospitalCoordinate().getLongitude()
            )));
        }
    }

    private Label upsertPlaceMarker(
            Label marker,
            String markerId,
            KakaoPlaceCoordinate coordinate,
            int drawableResId
    ) {
        LatLng position = LatLng.from(coordinate.getLatitude(), coordinate.getLongitude());
        if (marker == null) {
            android.graphics.Bitmap markerBitmap = getBitmapFromVectorDrawable(this, drawableResId);
            LabelOptions options = LabelOptions.from(markerId, position);
            if (markerBitmap != null) {
                options.setStyles(LabelStyle.from(markerBitmap));
            } else {
                options.setStyles(LabelStyle.from(drawableResId));
            }
            return kakaoMap.getLabelManager().getLayer().addLabel(options);
        }
        marker.moveTo(position);
        return marker;
    }

    private void startMapTracking() {
        if (kakaoMap == null) return;
        if (trackingLabel == null) {
            android.graphics.Bitmap trackingBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_tracking_dot);
            LabelOptions options = LabelOptions.from("tracking", LatLng.from(0, 0));
            if (trackingBitmap != null) {
                options.setStyles(LabelStyle.from(trackingBitmap).setAnchorPoint(0.5f, 0.5f));
            } else {
                options.setStyles(LabelStyle.from(R.drawable.ic_tracking_dot).setAnchorPoint(0.5f, 0.5f));
            }
            trackingLabel = kakaoMap.getLabelManager().getLayer().addLabel(options);
        }
        kakaoMap.getTrackingManager().startTracking(trackingLabel);
    }

    private android.graphics.Bitmap getBitmapFromVectorDrawable(android.content.Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                Math.max(1, drawable.getIntrinsicWidth()),
                Math.max(1, drawable.getIntrinsicHeight()),
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private void openMapFallback(ManagerGuideMapActionModel model) {
        if (!ManagerGuideMapFallbackLauncher.open(this, model)) {
            Toast.makeText(this, R.string.guide_map_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openCompanionChat() {
        startActivity(CompanionChatActivity.createIntent(this));
    }

    private void shareCurrentLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission(LOCATION_ACTION_SHARE_ONCE);
            return;
        }
        performSingleLocationShare();
    }

    private void startLiveLocationSharing() {
        if (!hasLocationPermission()) {
            requestLocationPermission(LOCATION_ACTION_START_LIVE);
            return;
        }
        performStartLiveLocationSharing();
    }

    private void performSingleLocationShare() {
        ManagerCurrentLocationSharer.share(this, new ManagerCurrentLocationSharer.Callback() {
            @Override
            public void onSuccess(double latitude, double longitude, String summary) {
                viewModel.shareCurrentLocation(latitude, longitude, summary);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performStartLiveLocationSharing() {
        ManagerLocationService.start(this);
        liveLocationActivationInFlight = true;
        viewModel.updateLiveLocationSharingState(true, () -> {
            liveLocationActivationInFlight = false;
        }, () -> {
            liveLocationActivationInFlight = false;
            stopTrackerOnly();
        });
    }

    private void stopLiveLocationSharing(boolean persistRemoteState, boolean showToast) {
        stopTrackerOnly();
        if (!persistRemoteState) {
            return;
        }
        viewModel.updateLiveLocationSharingState(false, () -> {
            if (showToast) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_live_location_stopped, Toast.LENGTH_SHORT).show();
            }
        }, null);
    }

    private void stopTrackerOnly() {
        ManagerLocationService.stop(this);
        liveLocationActivationInFlight = false;
        viewModel.resetLiveLocationInFlight();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission(int action) {
        pendingLocationPermissionAction = action;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_FINE_LOCATION) {
            return;
        }
        int action = pendingLocationPermissionAction;
        pendingLocationPermissionAction = LOCATION_ACTION_NONE;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (kakaoMap != null) {
                startMapTracking();
            }
            if (action == LOCATION_ACTION_START_LIVE) {
                startLiveLocationSharing();
                return;
            }
            if (action == LOCATION_ACTION_SHARE_ONCE) {
                shareCurrentLocation();
                return;
            }
        }
        if (action != LOCATION_ACTION_NONE) {
            Toast.makeText(this, R.string.guide_share_location_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityVisible = true;
        StatePanelHelper.hide(managerGuideStatePanel);
        viewModel.reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.pause();
        }
    }

    @Override
    protected void onStop() {
        activityVisible = false;
        super.onStop();
        if (isChangingConfigurations()) {
            stopTrackerOnly();
            return;
        }
        ManagerDashboard dashboard = viewModel.getUiState().getValue() != null ? viewModel.getUiState().getValue().dashboard : null;
        if (dashboard != null && dashboard.getSession().isLiveLocationSharingActive()) {
            stopLiveLocationSharing(true, false);
            return;
        }
        stopTrackerOnly();
    }

    private void syncLiveLocationTrackingWithDashboard(@Nullable ManagerDashboard dashboard) {
        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        if (!activityVisible) {
            stopTrackerOnly();
            return;
        }
        if (session == null || !session.isLiveLocationSharingActive()) {
            if (liveLocationActivationInFlight) {
                return;
            }
            stopTrackerOnly();
            return;
        }
        if (!liveLocationActivationInFlight) {
            ManagerLocationService.start(this);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.guide_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.companion_chat_empty_title),
                getString(R.string.companion_chat_empty_session_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.guide_title)),
                body,
                getString(R.string.state_action_retry),
                view -> viewModel.loadDashboard(),
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                managerGuideStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerGuideContentContainer.setVisibility(View.GONE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
