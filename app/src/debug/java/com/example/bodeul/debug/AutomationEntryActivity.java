package com.example.bodeul.debug;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerHomeProfile;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 디버그 빌드에서만 adb 자동 진입을 받아 기준선 계정 로그인과 화면 라우팅을 수행한다.
 */
public class AutomationEntryActivity extends AppCompatActivity {
    private static final byte[] SAMPLE_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0,
            0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, (byte) 0xDD,
            (byte) 0x8D, (byte) 0xB3, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    public static final String EXTRA_ROLE = "role";
    public static final String EXTRA_SCREEN = "screen";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_FORCE_SIGN_IN = "forceSignIn";
    public static final String EXTRA_UPLOAD_DOCUMENT_TYPE = "uploadDocumentType";
    public static final String EXTRA_UPLOAD_DOCUMENT_PATH = "uploadDocumentPath";

    private static final String TAG = "AutomationEntry";
    private static final String DEFAULT_PASSWORD = "bodeul1234";
    private static final String REQUEST_ID_PROGRESS = "request-seed-progress";
    private static final String REQUEST_ID_COMPLETED = "request-seed-completed";

    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private TextView textPrimary;
    private TextView textSecondary;
    private AutomationScreen requestedScreen;
    private UserRole requestedRole;
    private String requestedRequestId;
    private boolean forceSignIn;
    private ManagerDocumentFileType requestedUploadDocumentType;
    private String requestedUploadDocumentPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.simple_list_item_2);

        textPrimary = findViewById(android.R.id.text1);
        textSecondary = findViewById(android.R.id.text2);
        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerDocumentStorageUploader = ServiceLocator.provideManagerDocumentStorageUploader(this);

        requestedRole = parseRole(getIntent().getStringExtra(EXTRA_ROLE));
        requestedScreen = parseScreen(getIntent().getStringExtra(EXTRA_SCREEN));
        requestedRequestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        forceSignIn = getIntent().getBooleanExtra(EXTRA_FORCE_SIGN_IN, false);
        requestedUploadDocumentType = parseDocumentFileType(
                getIntent().getStringExtra(EXTRA_UPLOAD_DOCUMENT_TYPE)
        );
        requestedUploadDocumentPath = getIntent().getStringExtra(EXTRA_UPLOAD_DOCUMENT_PATH);

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
                    runRequestedAction(result);
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
                        runRequestedAction(result);
                    }

                    @Override
                    public void onError(String message) {
                        showError("기준선 계정 로그인에 실패했습니다.", message);
                    }
                }
        );
    }

    private void runRequestedAction(User user) {
        if (!shouldUploadManagerDocument()) {
            openRequestedScreen(user);
            return;
        }
        uploadManagerDocument(user);
    }

    private boolean shouldUploadManagerDocument() {
        return requestedRole == UserRole.MANAGER
                && requestedUploadDocumentType != null;
    }

    private void uploadManagerDocument(User user) {
        if (user.getRole() != UserRole.MANAGER) {
            showError("서류 업로드는 매니저 계정만 지원합니다.", user.getRole().name());
            return;
        }

        File localFile = resolveUploadFile();
        if (localFile == null || !localFile.exists() || !localFile.isFile()) {
            showError("업로드할 파일을 준비하지 못했습니다.", requestedUploadDocumentPath);
            return;
        }

        updateStatus(
                "원본 파일 업로드 중",
                requestedUploadDocumentType.name() + " / " + localFile.getName()
        );
        managerDocumentStorageUploader.uploadDocument(
                user.getId(),
                requestedUploadDocumentType,
                Uri.fromFile(localFile),
                new RepositoryCallback<ManagerDocumentFileMetadata>() {
                    @Override
                    public void onSuccess(ManagerDocumentFileMetadata result) {
                        saveUploadedDocumentMetadata(user, result);
                    }

                    @Override
                    public void onError(String message) {
                        showError("원본 파일 업로드에 실패했습니다.", message);
                    }
                }
        );
    }

    private void saveUploadedDocumentMetadata(User user, ManagerDocumentFileMetadata uploadedMetadata) {
        updateStatus(
                "서류 메타데이터 저장 중",
                uploadedMetadata.getFileName()
        );
        managerRepository.saveManagerDocumentFileMetadata(
                user.getId(),
                uploadedMetadata,
                new RepositoryCallback<ManagerHomeProfile>() {
                    @Override
                    public void onSuccess(ManagerHomeProfile result) {
                        openRequestedScreen(user);
                    }

                    @Override
                    public void onError(String message) {
                        showError("서류 메타데이터 저장에 실패했습니다.", message);
                    }
                }
        );
    }

    @Nullable
    private File resolveUploadFile() {
        if (!TextUtils.isEmpty(requestedUploadDocumentPath)) {
            File candidate = new File(requestedUploadDocumentPath);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return createSampleUploadFile();
    }

    @Nullable
    private File createSampleUploadFile() {
        if (requestedUploadDocumentType == null) {
            return null;
        }
        File outputFile = new File(
                getCacheDir(),
                "automation-" + requestedUploadDocumentType.getStorageKey() + ".png"
        );
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outputFile, false);
            outputStream.write(SAMPLE_PNG_BYTES);
            outputStream.flush();
            return outputFile;
        } catch (IOException exception) {
            Log.e(TAG, "자동 업로드 샘플 파일 생성 실패", exception);
            return null;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    // 닫기 실패는 업로드 경로 판단에 영향이 없다.
                }
            }
        }
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
            return UserRole.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
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
            return AutomationScreen.valueOf(screenName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 진입 화면: " + screenName, exception);
            return null;
        }
    }

    @Nullable
    private ManagerDocumentFileType parseDocumentFileType(@Nullable String documentTypeName) {
        if (TextUtils.isEmpty(documentTypeName)) {
            return null;
        }
        String normalizedName = documentTypeName.trim();
        ManagerDocumentFileType byStorageKey = ManagerDocumentFileType.fromStorageKey(normalizedName);
        if (byStorageKey != null) {
            return byStorageKey;
        }
        try {
            return ManagerDocumentFileType.valueOf(normalizedName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "지원하지 않는 자동 업로드 서류 유형: " + documentTypeName, exception);
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