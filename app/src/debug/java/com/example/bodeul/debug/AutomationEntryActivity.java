package com.example.bodeul.debug;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.admin.AdminActivity;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingFollowUpActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.example.bodeul.ui.manager.ManagerGuideActivity;
import com.example.bodeul.ui.manager.ManagerHistoryActivity;
import com.example.bodeul.ui.manager.ManagerProfileActivity;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.report.GuardianReportActivity;

/**
 * 디버그 빌드에서만 adb 자동 진입을 받아 기준선 계정 로그인과 화면 라우팅을 수행한다.
 */
public class AutomationEntryActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "role";
    public static final String EXTRA_SCREEN = "screen";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_FORCE_SIGN_IN = "forceSignIn";

    private static final String TAG = "AutomationEntry";
    private static final String DEFAULT_PASSWORD = "bodeul1234";
    private static final String REQUEST_ID_PROGRESS = "request-seed-progress";
    private static final String REQUEST_ID_COMPLETED = "request-seed-completed";

    private AuthRepository authRepository;
    private TextView textPrimary;
    private TextView textSecondary;
    private AutomationScreen requestedScreen;
    private UserRole requestedRole;
    private String requestedRequestId;
    private boolean forceSignIn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.simple_list_item_2);

        textPrimary = findViewById(android.R.id.text1);
        textSecondary = findViewById(android.R.id.text2);
        authRepository = ServiceLocator.provideAuthRepository(this);

        requestedRole = parseRole(getIntent().getStringExtra(EXTRA_ROLE));
        requestedScreen = parseScreen(getIntent().getStringExtra(EXTRA_SCREEN));
        requestedRequestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        forceSignIn = getIntent().getBooleanExtra(EXTRA_FORCE_SIGN_IN, false);

        if (requestedRole == null) {
            showError("자동 진입 역할이 없습니다.", "role extra를 ADMIN, MANAGER, PATIENT, GUARDIAN 중 하나로 전달해 주세요.");
            return;
        }
        if (requestedScreen == null) {
            showError("자동 진입 화면이 없습니다.", "screen extra를 전달해 주세요.");
            return;
        }

        updateStatus(
                "자동 진입 준비 중",
                requestedRole.name() + " / " + requestedScreen.name()
        );
        routeToRequestedScreen();
    }

    private void routeToRequestedScreen() {
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!forceSignIn
                        && result.getRole() == requestedRole
                        && !AuthFlowRouter.requiresProfileCompletion(result)) {
                    openRequestedScreen(result);
                    return;
                }
                authRepository.signOut();
                signInBaseline();
            }

            @Override
            public void onError(String message) {
                signInBaseline();
            }
        });
    }

    private void signInBaseline() {
        BaselineAccount baselineAccount = BaselineAccount.fromRole(requestedRole);
        if (baselineAccount == null) {
            showError("기준선 계정을 찾지 못했습니다.", requestedRole.name());
            return;
        }

        updateStatus(
                "기준선 계정 로그인 중",
                baselineAccount.email
        );
        authRepository.signIn(
                baselineAccount.email,
                DEFAULT_PASSWORD,
                requestedRole,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        openRequestedScreen(result);
                    }

                    @Override
                    public void onError(String message) {
                        showError("기준선 계정 로그인에 실패했습니다.", message);
                    }
                }
        );
    }

    private void openRequestedScreen(User user) {
        Intent targetIntent = buildTargetIntent(user);
        if (targetIntent == null) {
            showError("대상 화면 인텐트를 만들지 못했습니다.", requestedScreen.name());
            return;
        }

        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ComponentName componentName = targetIntent.getComponent();
        updateStatus(
                "대상 화면으로 이동 중",
                componentName == null ? requestedScreen.name() : componentName.flattenToShortString()
        );
        startActivity(targetIntent);
        finish();
    }

    @Nullable
    private Intent buildTargetIntent(User user) {
        switch (requestedScreen) {
            case HOME:
                return AuthFlowRouter.createPostAuthIntent(this, user);
            case CLIENT_HOME:
                return new Intent(this, MainActivity.class);
            case BOOKING:
                return new Intent(this, BookingActivity.class);
            case BOOKING_STATUS:
                return BookingStatusActivity.createIntent(this, resolveRequestId(false));
            case BOOKING_FOLLOW_UP:
                return BookingFollowUpActivity.createIntent(this, resolveRequestId(true));
            case GUARDIAN_REPORT:
                return new Intent(this, GuardianReportActivity.class);
            case MANAGER_HOME:
                return new Intent(this, ManagerActivity.class);
            case MANAGER_HISTORY:
                return ManagerHistoryActivity.createIntent(this);
            case MANAGER_GUIDE:
                return new Intent(this, ManagerGuideActivity.class);
            case MANAGER_SUPPORT:
                return ManagerSupportActivity.createIntent(this);
            case MANAGER_PROFILE:
                return ManagerProfileActivity.createIntent(this);
            case ADMIN_DASHBOARD:
                return new Intent(this, AdminActivity.class);
            default:
                return null;
        }
    }

    private String resolveRequestId(boolean completedScreen) {
        if (!TextUtils.isEmpty(requestedRequestId)) {
            return requestedRequestId;
        }
        return completedScreen ? REQUEST_ID_COMPLETED : REQUEST_ID_PROGRESS;
    }

    @Nullable
    private UserRole parseRole(@Nullable String roleName) {
        if (TextUtils.isEmpty(roleName)) {
            return null;
        }
        try {
            return UserRole.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 진입 역할: " + roleName, exception);
            return null;
        }
    }

    @Nullable
    private AutomationScreen parseScreen(@Nullable String screenName) {
        if (TextUtils.isEmpty(screenName)) {
            return null;
        }
        try {
            return AutomationScreen.valueOf(screenName.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 진입 화면: " + screenName, exception);
            return null;
        }
    }

    private void updateStatus(String title, String detail) {
        textPrimary.setText(title);
        textSecondary.setText(detail);
        Log.i(TAG, title + " / " + detail);
    }

    private void showError(String title, String detail) {
        updateStatus(title, detail);
    }

    private enum AutomationScreen {
        HOME,
        CLIENT_HOME,
        BOOKING,
        BOOKING_STATUS,
        BOOKING_FOLLOW_UP,
        GUARDIAN_REPORT,
        MANAGER_HOME,
        MANAGER_HISTORY,
        MANAGER_GUIDE,
        MANAGER_SUPPORT,
        MANAGER_PROFILE,
        ADMIN_DASHBOARD
    }

    private enum BaselineAccount {
        ADMIN(UserRole.ADMIN, "admin@bodeul.app"),
        MANAGER(UserRole.MANAGER, "manager@bodeul.app"),
        PATIENT(UserRole.PATIENT, "patient@bodeul.app"),
        GUARDIAN(UserRole.GUARDIAN, "guardian@bodeul.app");

        private final UserRole role;
        private final String email;

        BaselineAccount(UserRole role, String email) {
            this.role = role;
            this.email = email;
        }

        @Nullable
        private static BaselineAccount fromRole(UserRole role) {
            for (BaselineAccount account : values()) {
                if (account.role == role) {
                    return account;
                }
            }
            return null;
        }
    }
}
