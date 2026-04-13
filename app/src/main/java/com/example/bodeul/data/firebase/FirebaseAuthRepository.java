package com.example.bodeul.data.firebase;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Firebase Authentication과 Firestore를 이용해 로그인과 회원가입을 처리한다.
 */
public class FirebaseAuthRepository implements AuthRepository {
    private static final String RESOURCE_AUTH_CUSTOM_DOMAIN = "firebase_auth_custom_domain";
    private static final String RESOURCE_AUTH_CONTINUE_URL = "firebase_auth_continue_url";

    private final Context appContext;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final CredentialManager credentialManager;
    private final Executor mainExecutor;

    // 같은 세션 안에서는 중복 조회를 줄이기 위해 사용자 프로필을 캐시한다.
    @Nullable
    private User cachedUser;

    public FirebaseAuthRepository(
            Context appContext,
            FirebaseAuth firebaseAuth,
            FirebaseFirestore firestore
    ) {
        this.appContext = appContext;
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
        this.credentialManager = CredentialManager.create(appContext);
        this.mainExecutor = ContextCompat.getMainExecutor(appContext);
        this.firebaseAuth.setLanguageCode("ko");
        applyConfiguredAuthDomain();
    }

    @Override
    public void getCurrentUser(RepositoryCallback<User> callback) {
        // 이미 읽어둔 프로필이 있으면 네트워크 호출 없이 바로 반환한다.
        if (cachedUser != null) {
            callback.onSuccess(cachedUser);
            return;
        }

        // Auth 세션만으로는 역할 정보가 없어서 users 문서를 다시 읽어야 한다.
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }

        // 이메일 인증 여부를 최신 상태로 확인하기 위해 현재 사용자를 다시 고친다.
        currentUser.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser reloadedUser = firebaseAuth.getCurrentUser();
                    if (reloadedUser == null) {
                        callback.onError("로그인이 필요합니다.");
                        return;
                    }
                    if (!isSocialUser(reloadedUser) && !reloadedUser.isEmailVerified()) {
                        signOut();
                        callback.onError("이메일 인증을 완료한 뒤 다시 로그인해주세요.");
                        return;
                    }
                    loadUserProfile(reloadedUser.getUid(), null, callback);
                })
                .addOnFailureListener(exception -> {
                    signOut();
                    callback.onError("로그인 상태를 확인하지 못했습니다.");
                });
    }

    @Override
    public void signIn(String email, String password, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 인증 성공 후에는 Firestore의 사용자 프로필을 읽어 역할까지 검증한다.
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("로그인에 실패했습니다.");
                        return;
                    }
                    if (!firebaseUser.isEmailVerified()) {
                        // 미인증 계정은 자동 재발송하지 않고 사용자가 직접 다시 보내도록 안내한다.
                        signOut();
                        callback.onError("이메일 인증이 필요합니다. 받은 편지함과 스팸함을 확인하고, 필요하면 인증 메일 다시 보내기를 눌러주세요.");
                        return;
                    }
                    loadUserProfile(firebaseUser.getUid(), expectedRole, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveSignInMessage(exception)));
    }

    @Override
    public void signInWithGoogle(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 구글 로그인은 Firebase 콘솔과 google-services 설정이 모두 맞아야 동작한다.
        String serverClientId = resolveGoogleServerClientId(activity);
        if (TextUtils.isEmpty(serverClientId)) {
            callback.onError("구글 로그인 설정이 아직 완료되지 않았습니다. Firebase 콘솔에서 Google 로그인을 활성화하고 google-services.json을 다시 받아주세요.");
            return;
        }

        requestGoogleCredential(activity, serverClientId, true, expectedRole, callback);
    }

    @Override
    public void register(
            String name,
            String email,
            String phone,
            String password,
            UserRole role,
            RepositoryCallback<User> callback
    ) {
        // Auth 계정 생성과 Firestore 프로필 저장을 순서대로 처리한다.
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("회원가입에 실패했습니다.");
                        return;
                    }

                    Map<String, Object> userDocument = new HashMap<>();
                    userDocument.put("name", name);
                    userDocument.put("email", email);
                    userDocument.put("phone", phone);
                    userDocument.put("role", role.name());

                    firestore.collection("users")
                            .document(firebaseUser.getUid())
                            .set(userDocument)
                            .addOnSuccessListener(unused -> {
                                User user = new User(firebaseUser.getUid(), role, name, email, phone);
                                cachedUser = user;

                                // 회원가입 직후 인증 메일을 보내고 다음 로그인은 인증 후에만 허용한다.
                                sendVerificationEmail(firebaseUser, false, new RepositoryCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        signOut();
                                        callback.onSuccess(user);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        signOut();
                                        callback.onError(message);
                                    }
                                });
                            })
                            .addOnFailureListener(exception -> {
                                // 프로필 저장이 실패하면 Auth 계정만 남지 않도록 정리를 시도한다.
                                firebaseUser.delete().addOnCompleteListener(task -> signOut());
                                callback.onError("사용자 프로필을 저장하지 못했습니다.");
                            });
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveRegisterMessage(exception)));
    }

    @Override
    public void resetPassword(String email, RepositoryCallback<Void> callback) {
        // 커스텀 도메인이 설정돼 있으면 같은 도메인으로 재설정 링크를 보낸다.
        sendPasswordResetEmail(email, callback);
    }

    @Override
    public void resendVerificationEmail(
            String email,
            String password,
            RepositoryCallback<Void> callback
    ) {
        // 사용자가 명시적으로 요청했을 때만 인증 메일을 다시 보낸다.
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        signOut();
                        callback.onError("계정 정보를 다시 확인해주세요.");
                        return;
                    }
                    if (firebaseUser.isEmailVerified()) {
                        signOut();
                        callback.onError("이미 이메일 인증이 완료된 계정입니다. 로그인해주세요.");
                        return;
                    }

                    sendVerificationEmail(firebaseUser, true, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            signOut();
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {
                            signOut();
                            callback.onError(message);
                        }
                    });
                })
                .addOnFailureListener(exception -> {
                    signOut();
                    callback.onError(resolveSignInMessage(exception));
                });
    }

    @Override
    public void signOut() {
        // 로컬 캐시와 Firebase 세션을 함께 비우고, Credential Manager에도 로그아웃을 알린다.
        cachedUser = null;
        firebaseAuth.signOut();
        clearCredentialState();
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void requestGoogleCredential(
            Activity activity,
            String serverClientId,
            boolean filterByAuthorizedAccounts,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(serverClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // 먼저 기존에 승인된 계정을 우선 보여주고, 없으면 전체 계정 선택으로 한 번 더 시도한다.
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result.getCredential(), expectedRole, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException exception) {
                        if (filterByAuthorizedAccounts && exception instanceof NoCredentialException) {
                            requestGoogleCredential(activity, serverClientId, false, expectedRole, callback);
                            return;
                        }
                        callback.onError(resolveGoogleRequestMessage(exception));
                    }
                }
        );
    }

    private void handleGoogleCredential(
            Credential credential,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        // Credential Manager가 돌려준 값이 구글 ID 토큰인지 확인한 뒤 Firebase 인증으로 교환한다.
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            callback.onError("구글 계정 인증 정보를 확인하지 못했습니다.");
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        try {
            GoogleIdTokenCredential googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());
            firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken(), expectedRole, callback);
        } catch (RuntimeException exception) {
            callback.onError("구글 계정 인증 정보를 해석하지 못했습니다.");
        }
    }

    private void firebaseAuthWithGoogle(
            String idToken,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("구글 로그인에 실패했습니다.");
                        return;
                    }
                    syncGoogleUserProfile(firebaseUser, expectedRole, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveGoogleFirebaseMessage(exception)));
    }

    private void syncGoogleUserProfile(
            FirebaseUser firebaseUser,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        firestore.collection("users")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // 첫 구글 로그인이라면 선택한 역할 기준으로 Firestore 프로필을 만든다.
                    if (!documentSnapshot.exists()) {
                        createGoogleUserProfile(firebaseUser, expectedRole, callback);
                        return;
                    }

                    User user = toUser(documentSnapshot);
                    if (user == null) {
                        signOut();
                        callback.onError("users 컬렉션의 계정 정보를 확인해주세요.");
                        return;
                    }

                    if (user.getRole() != expectedRole) {
                        signOut();
                        callback.onError("선택한 사용자 유형과 계정 유형이 일치하지 않습니다.");
                        return;
                    }

                    cachedUser = user;
                    callback.onSuccess(user);
                })
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    private void createGoogleUserProfile(
            FirebaseUser firebaseUser,
            UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        String email = firebaseUser.getEmail();
        if (TextUtils.isEmpty(email)) {
            signOut();
            callback.onError("구글 계정에서 이메일 정보를 확인하지 못했습니다.");
            return;
        }

        String displayName = firebaseUser.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = defaultNameForRole(expectedRole);
        }

        String phoneNumber = firebaseUser.getPhoneNumber();
        if (phoneNumber == null) {
            phoneNumber = "";
        }

        final String finalName = displayName;
        final String finalPhone = phoneNumber;

        Map<String, Object> userDocument = new HashMap<>();
        userDocument.put("name", finalName);
        userDocument.put("email", email);
        userDocument.put("phone", finalPhone);
        userDocument.put("role", expectedRole.name());

        firestore.collection("users")
                .document(firebaseUser.getUid())
                .set(userDocument)
                .addOnSuccessListener(unused -> {
                    User user = new User(firebaseUser.getUid(), expectedRole, finalName, email, finalPhone);
                    cachedUser = user;
                    callback.onSuccess(user);
                })
                .addOnFailureListener(exception -> {
                    signOut();
                    callback.onError("구글 계정 프로필을 저장하지 못했습니다.");
                });
    }

    private void loadUserProfile(
            String userId,
            @Nullable UserRole expectedRole,
            RepositoryCallback<User> callback
    ) {
        // 로그인 역할과 실제 저장된 역할이 다르면 즉시 로그아웃시켜 화면 분기를 단순화한다.
        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Auth uid를 기준으로 users 컬렉션의 단일 사용자 프로필을 직렬화한다.
                    User user = toUser(documentSnapshot);
                    if (user == null) {
                        signOut();
                        callback.onError("users 컬렉션에서 계정 정보를 찾지 못했습니다.");
                        return;
                    }

                    if (expectedRole != null && user.getRole() != expectedRole) {
                        signOut();
                        callback.onError("선택한 사용자 유형과 계정 유형이 일치하지 않습니다.");
                        return;
                    }

                    cachedUser = user;
                    callback.onSuccess(user);
                })
                .addOnFailureListener(exception ->
                        callback.onError("사용자 정보를 불러오지 못했습니다."));
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        // users 문서의 필수 필드가 하나라도 비어 있으면 앱 모델로 변환하지 않는다.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String roleValue = documentSnapshot.getString("role");
        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        if (roleValue == null || name == null || email == null) {
            return null;
        }

        // phone은 선택값으로 보고 비어 있으면 빈 문자열로 정리한다.
        UserRole role = UserRole.valueOf(roleValue);
        return new User(documentSnapshot.getId(), role, name, email, phone == null ? "" : phone);
    }

    private boolean isSocialUser(FirebaseUser firebaseUser) {
        for (com.google.firebase.auth.UserInfo userInfo : firebaseUser.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(userInfo.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String resolveGoogleServerClientId(Context context) {
        int resourceId = context.getResources()
                .getIdentifier("default_web_client_id", "string", context.getPackageName());
        if (resourceId == 0) {
            return null;
        }

        String serverClientId = context.getString(resourceId);
        return TextUtils.isEmpty(serverClientId) ? null : serverClientId;
    }

    private void applyConfiguredAuthDomain() {
        // 콘솔에서 허용한 커스텀 인증 도메인이 있으면 기본 firebaseapp.com 대신 사용한다.
        String customDomain = resolveOptionalStringResource(RESOURCE_AUTH_CUSTOM_DOMAIN);
        if (TextUtils.isEmpty(customDomain)) {
            return;
        }
        firebaseAuth.setCustomAuthDomain(normalizeDomain(customDomain));
    }

    @Nullable
    private ActionCodeSettings createEmailActionSettings() {
        String customDomain = resolveOptionalStringResource(RESOURCE_AUTH_CUSTOM_DOMAIN);
        String continueUrl = resolveOptionalStringResource(RESOURCE_AUTH_CONTINUE_URL);
        if (TextUtils.isEmpty(customDomain) && TextUtils.isEmpty(continueUrl)) {
            return null;
        }

        // 메일 링크가 브랜드 도메인을 우선 사용하도록 ActionCodeSettings를 구성한다.
        String normalizedDomain = TextUtils.isEmpty(customDomain) ? null : normalizeDomain(customDomain);
        ActionCodeSettings.Builder builder = ActionCodeSettings.newBuilder()
                .setHandleCodeInApp(false)
                .setAndroidPackageName(appContext.getPackageName(), false, null);

        if (!TextUtils.isEmpty(normalizedDomain)) {
            builder.setLinkDomain(normalizedDomain);
        }
        if (!TextUtils.isEmpty(continueUrl)) {
            builder.setUrl(normalizeContinueUrl(continueUrl));
        } else if (!TextUtils.isEmpty(normalizedDomain)) {
            builder.setUrl("https://" + normalizedDomain);
        }
        return builder.build();
    }

    private void sendPasswordResetEmail(String email, RepositoryCallback<Void> callback) {
        ActionCodeSettings actionCodeSettings = createEmailActionSettings();
        if (actionCodeSettings == null) {
            firebaseAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(exception ->
                            callback.onError(resolveResetPasswordMessage(exception)));
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email, actionCodeSettings)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception ->
                        firebaseAuth.sendPasswordResetEmail(email)
                                .addOnSuccessListener(unused -> callback.onSuccess(null))
                                .addOnFailureListener(fallbackException ->
                                        callback.onError(resolveResetPasswordMessage(fallbackException))));
    }

    private void sendVerificationEmail(
            FirebaseUser firebaseUser,
            boolean resend,
            RepositoryCallback<Void> callback
    ) {
        ActionCodeSettings actionCodeSettings = createEmailActionSettings();
        if (actionCodeSettings == null) {
            firebaseUser.sendEmailVerification()
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(exception ->
                            callback.onError(resolveVerificationEmailMessage(exception, resend)));
            return;
        }

        firebaseUser.sendEmailVerification(actionCodeSettings)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception ->
                        firebaseUser.sendEmailVerification()
                                .addOnSuccessListener(unused -> callback.onSuccess(null))
                                .addOnFailureListener(fallbackException ->
                                        callback.onError(resolveVerificationEmailMessage(fallbackException, resend))));
    }

    @Nullable
    private String resolveOptionalStringResource(String resourceName) {
        int resourceId = appContext.getResources()
                .getIdentifier(resourceName, "string", appContext.getPackageName());
        if (resourceId == 0) {
            return null;
        }

        String value = appContext.getString(resourceId).trim();
        return TextUtils.isEmpty(value) ? null : value;
    }

    private String normalizeDomain(String value) {
        String normalized = value.trim().replaceFirst("^https?://", "");
        int pathStartIndex = normalized.indexOf('/');
        if (pathStartIndex >= 0) {
            normalized = normalized.substring(0, pathStartIndex);
        }
        return normalized;
    }

    private String normalizeContinueUrl(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }

    private void clearCredentialState() {
        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(
                clearRequest,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        // 로그아웃 후에는 추가 작업이 없다.
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException exception) {
                        // 자격 증명 정리 실패는 다음 로그인 시 재시도되므로 무시한다.
                    }
                }
        );
    }

    private String defaultNameForRole(UserRole role) {
        switch (role) {
            case MANAGER:
                return "매니저";
            case GUARDIAN:
                return "보호자";
            case ADMIN:
                return "관리자";
            case PATIENT:
            default:
                return "환자";
        }
    }

    private String resolveSignInMessage(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException
                || exception instanceof FirebaseAuthInvalidUserException) {
            return "이메일 또는 비밀번호를 확인해주세요.";
        }
        return "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveRegisterMessage(Exception exception) {
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "비밀번호는 6자 이상 입력해주세요.";
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "이미 가입된 이메일입니다.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "올바른 이메일 형식을 입력해주세요.";
        }
        return "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveResetPasswordMessage(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "올바른 이메일 형식을 입력해주세요.";
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "가입한 이메일 정보를 찾지 못했습니다.";
        }
        return "비밀번호 재설정 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveVerificationEmailMessage(Exception exception, boolean resend) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "계정 정보를 다시 확인해주세요.";
        }
        if (resend) {
            return "이메일 인증은 필요하지만 인증 메일을 다시 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
        }
        return "회원가입은 완료됐지만 인증 메일을 보내지 못했습니다. 로그인 화면에서 다시 시도해주세요.";
    }

    private String resolveGoogleRequestMessage(GetCredentialException exception) {
        if (exception instanceof NoCredentialException) {
            return "기기에서 선택할 수 있는 구글 계정을 찾지 못했습니다.";
        }

        String simpleName = exception.getClass().getSimpleName();
        if (simpleName.contains("Cancel") || simpleName.contains("Interrupt")) {
            return "구글 로그인 요청이 취소되었습니다.";
        }
        return "구글 로그인 창을 열지 못했습니다. 다시 시도해주세요.";
    }

    private String resolveGoogleFirebaseMessage(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "이미 다른 로그인 방식으로 가입된 계정입니다. 기존 방식으로 로그인해주세요.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "구글 인증 정보를 확인하지 못했습니다.";
        }
        return "구글 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }
}
