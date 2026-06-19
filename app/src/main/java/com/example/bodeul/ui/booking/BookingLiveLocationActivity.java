package com.example.bodeul.ui.booking;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.map.HospitalMapCoordinateQuery;
import com.example.bodeul.data.map.HospitalMapCoordinateResult;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.route.RouteLine;
import com.kakao.vectormap.route.RouteLineOptions;
import com.kakao.vectormap.route.RouteLineSegment;
import com.kakao.vectormap.route.RouteLineStyle;
import com.kakao.vectormap.route.RouteLineStyles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 환자와 보호자가 현재 동행 위치 공유와 현장 메모를 한 화면에서 확인한다.
 */
public class BookingLiveLocationActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";
    private static final int REQUEST_FINE_LOCATION = 1001;

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private BookingLiveLocationCoordinator coordinator;
    private BookingLiveLocationBinder binder;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private String requestId;
    private AppointmentRequestDetail currentDetail;
    private Runnable detailObserverRegistration;
    private KakaoLocalPlaceSearchClient placeSearchClient;

    private MapView mapView;
    private KakaoMap kakaoMap;
    private Label managerMarker;
    private Label hospitalMarker;
    private Label pharmacyMarker;
    private Label trackingLabel;
    private RouteLine historyRouteLine;
    private HospitalMapCoordinateResult currentCoordinateResult;
    private String currentCoordinateQueryKey = "";
    private boolean coordinateSearchInFlight;

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, BookingLiveLocationActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_live_location);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        coordinator = new BookingLiveLocationCoordinator(this, new BookingPresentationFormatter(this));
        placeSearchClient = new KakaoLocalPlaceSearchClient(this);

        statePanel = findViewById(R.id.bookingLiveLocationStatePanel);
        contentContainer = findViewById(R.id.bookingLiveLocationContentContainer);
        progressBar = findViewById(R.id.progressBookingLiveLocation);

        binder = new BookingLiveLocationBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textBookingLiveLocationMode),
                findViewById(R.id.textBookingLiveLocationTitle),
                findViewById(R.id.textBookingLiveLocationSubtitle),
                findViewById(R.id.textBookingLiveLocationHeroBadge),
                findViewById(R.id.textBookingLiveLocationHeroTitle),
                findViewById(R.id.textBookingLiveLocationHeroBody),
                findViewById(R.id.textBookingLiveLocationStatusSectionTitle),
                findViewById(R.id.textBookingLiveLocationMemoSectionTitle),
                findViewById(R.id.textBookingLiveLocationHistorySectionTitle),
                findViewById(R.id.textBookingLiveLocationMapSectionTitle),
                findViewById(R.id.textBookingLiveLocationMapSectionHelper),
                findViewById(R.id.textBookingLiveLocationMapHighlightTitle),
                findViewById(R.id.textBookingLiveLocationMapHighlightBody),
                findViewById(R.id.viewBookingLiveLocationHospitalMap),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationStatusLines),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationMemoLines),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationHistoryLines),
                (LinearLayout) findViewById(R.id.layoutBookingLiveLocationMapActions),
                (MaterialButton) findViewById(R.id.buttonBookingLiveLocationPrimary),
                (MaterialButton) findViewById(R.id.buttonBookingLiveLocationRefresh),
                new BookingLiveLocationMapActionBinder(this::openMapFallback)
        );

        findViewById(R.id.buttonBackBookingLiveLocation).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingLiveLocationPrimary).setOnClickListener(view -> openBookingStatus());
        findViewById(R.id.buttonBookingLiveLocationRefresh).setOnClickListener(view -> startObserving());
        findViewById(R.id.buttonBookingLiveLocationChat).setOnClickListener(view -> openCompanionChat());
        contentContainer.setVisibility(View.GONE);

        mapView = findViewById(R.id.mapViewBookingLiveLocation);
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
                } else {
                    ActivityCompat.requestPermissions(BookingLiveLocationActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startObserving();
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
        super.onStop();
        stopObserving();
    }

    private void stopObserving() {
        if (detailObserverRegistration != null) {
            detailObserverRegistration.run();
            detailObserverRegistration = null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (kakaoMap != null) {
                    startMapTracking();
                }
            }
        }
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

    private void startObserving() {
        if (TextUtils.isEmpty(requestId)) {
            showLoadErrorState(getString(R.string.booking_status_request_missing));
            return;
        }

        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.PATIENT && result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                stopObserving();
                detailObserverRegistration = bookingRepository.observeAppointmentRequestDetail(
                        result,
                        requestId,
                        new RepositoryCallback<AppointmentRequestDetail>() {
                            @Override
                            public void onSuccess(AppointmentRequestDetail detail) {
                                currentDetail = detail;
                                binder.bindScreen(coordinator.createScreenModel(
                                        result,
                                        detail,
                                        bookingRepository.isFirebaseBacked()
                                ));
                                updateMapMarker();
                                contentContainer.setVisibility(View.VISIBLE);
                                hideBlockingState();
                                setLoading(false);
                            }

                            @Override
                            public void onError(String message) {
                                currentDetail = null;
                                setLoading(false);
                                showLoadErrorState(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void openMapFallback(BookingLiveLocationMapActionModel model) {
        if (!BookingLiveLocationMapFallbackLauncher.open(this, model)) {
            Toast.makeText(this, R.string.guide_map_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookingStatus() {
        if (currentDetail == null) {
            finish();
            return;
        }
        startActivity(BookingStatusActivity.createIntent(
                this,
                currentDetail.getAppointmentRequest().getId()
        ));
    }

    private void openCompanionChat() {
        if (TextUtils.isEmpty(requestId)) {
            Toast.makeText(this, R.string.booking_status_request_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(CompanionChatActivity.createIntent(this, requestId));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            contentContainer.setVisibility(View.GONE);
        }
    }

    private void updateMapMarker() {
        if (kakaoMap == null || currentDetail == null) return;

        updateSharedLocationMarker();
        updateHospitalAndPharmacyMarkers();
        updateHistoryRouteLine();
    }

    private void updateSharedLocationMarker() {
        if (kakaoMap == null || currentDetail == null || currentDetail.getSession() == null) {
            return;
        }

        Double lat = currentDetail.getSession().getSharedLatitude();
        Double lng = currentDetail.getSession().getSharedLongitude();

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
        if (kakaoMap == null || currentDetail == null) {
            return;
        }
        HospitalMapCoordinateQuery query = new HospitalMapCoordinateQuery(
                currentDetail.getAppointmentRequest().getHospitalName(),
                currentDetail.getAppointmentRequest().getDepartmentName()
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
                    "hospital",
                    result.getHospitalCoordinate(),
                    R.drawable.ic_map_marker_hospital
            );
        }
        if (result.getPharmacyCoordinate() != null) {
            pharmacyMarker = upsertPlaceMarker(
                    pharmacyMarker,
                    "pharmacy",
                    result.getPharmacyCoordinate(),
                    R.drawable.ic_map_marker_pharmacy
            );
        }
        if ((currentDetail.getSession() == null || !currentDetail.getSession().hasSharedLocationCoordinates())
                && result.getHospitalCoordinate() != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(
                    result.getHospitalCoordinate().getLatitude(),
                    result.getHospitalCoordinate().getLongitude()
            )));
        }
    }

    // 최근 위치 이력을 지도 경로로 이어서 현재 이동 흐름을 한눈에 보이게 한다.
    private void updateHistoryRouteLine() {
        if (kakaoMap == null || currentDetail == null) {
            return;
        }

        List<CompanionLocationHistoryEntry> historyEntries =
                CompanionLocationDisplayHelper.resolveHistoryEntries(currentDetail.getSession(), 12);
        if (historyEntries.size() < 2) {
            if (historyRouteLine != null) {
                historyRouteLine.remove();
                historyRouteLine = null;
            }
            return;
        }

        List<LatLng> points = new ArrayList<>();
        List<CompanionLocationHistoryEntry> orderedEntries = new ArrayList<>(historyEntries);
        Collections.reverse(orderedEntries);
        for (CompanionLocationHistoryEntry entry : orderedEntries) {
            points.add(LatLng.from(entry.getLatitude(), entry.getLongitude()));
        }

        RouteLineSegment segment = RouteLineSegment.from(
                points,
                RouteLineStyles.from(
                        RouteLineStyle.from(12f, getColor(R.color.bodeul_primary), 4f, getColor(R.color.white))
                )
        );

        if (historyRouteLine == null) {
            historyRouteLine = kakaoMap.getRouteLineManager()
                    .getLayer()
                    .addRouteLine(RouteLineOptions.from("shared-location-history", segment));
        } else {
            historyRouteLine.changeSegments(segment);
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

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.booking_live_location_title)),
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

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.booking_live_location_title)),
                body,
                getString(R.string.state_action_retry),
                view -> startObserving(),
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
                statePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(statePanel);
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
