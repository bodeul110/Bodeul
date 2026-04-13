package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Authentication과 Firestore를 이용해 로그인과 회원가입을 처리한다.
 */
public class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    // 같은 세션 안에서는 불필요한 users 조회를 줄이기 위해 사용자 정보를 캐시한다.
    @Nullable
    private User cachedUser;

    public FirebaseAuthRepository(FirebaseAuth firebaseAuth, FirebaseFirestore firestore) {
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
        this.firebaseAuth.setLanguageCode("ko");
    }

    @Override
    public void getCurrentUser(RepositoryCallback<User> callback) {
        // 이미 읽어둔 프로필이 있으면 네트워크 호출 없이 바로 반환한다.
        if (cachedUser != null) {
            callback.onSuccess(cachedUser);
            return;
        }

        // Auth 세션이 있더라도 화면에 필요한 role/name/phone은 users 문서에서 다시 읽어야 한다.
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }

        // 이메일 인증 여부를 최신 상태로 반영하기 위해 현재 사용자 정보를 새로 고친다.
        currentUser.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser reloadedUser = firebaseAuth.getCurrentUser();
                    if (reloadedUser == null) {
                        callback.onError("로그인이 필요합니다.");
                        return;
                    }
                    if (!reloadedUser.isEmailVerified()) {
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
                        // 미인증 계정은 로그인 상태를 유지하지 않고 인증 메일을 다시 보낸다.
                        firebaseUser.sendEmailVerification()
                                .addOnSuccessListener(unused -> {
                                    signOut();
                                    callback.onError("이메일 인증이 필요합니다. 인증 메일을 다시 보냈습니다.");
                                })
                                .addOnFailureListener(exception -> {
                                    signOut();
                                    callback.onError(resolveVerificationEmailMessage(exception, true));
                                });
                        return;
                    }
                    loadUserProfile(firebaseUser.getUid(), expectedRole, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveSignInMessage(exception)));
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
        // Auth 계정 생성과 Firestore 프로필 저장을 연속으로 처리한다.
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

                                // 회원가입 직후 인증 메일을 보내고, 다음 로그인은 인증 완료 후에만 허용한다.
                                firebaseUser.sendEmailVerification()
                                        .addOnSuccessListener(ignored -> {
                                            signOut();
                                            callback.onSuccess(user);
                                        })
                                        .addOnFailureListener(exception -> {
                                            signOut();
                                            callback.onError(resolveVerificationEmailMessage(exception, false));
                                        });
                            })
                            .addOnFailureListener(exception -> {
                                // 프로필 저장이 실패하면 Auth 계정만 남지 않도록 삭제를 시도한다.
                                firebaseUser.delete().addOnCompleteListener(task -> signOut());
                                callback.onError("사용자 프로필을 저장하지 못했습니다.");
                            });
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveRegisterMessage(exception)));
    }

    @Override
    public void resetPassword(String email, RepositoryCallback<Void> callback) {
        // Firebase가 제공하는 비밀번호 재설정 메일 발송 기능을 그대로 사용한다.
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(exception ->
                        callback.onError(resolveResetPasswordMessage(exception)));
    }

    @Override
    public void signOut() {
        // 로컬 캐시와 Firebase 세션을 함께 비워 다음 진입에서 인증 상태가 초기화되도록 한다.
        cachedUser = null;
        firebaseAuth.signOut();
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
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
                    // Auth uid를 기준으로 users 컬렉션의 앱 전용 프로필을 역직렬화한다.
                    User user = toUser(documentSnapshot);
                    if (user == null) {
                        signOut();
                        callback.onError("users 컬렉션에서 계정 정보를 찾지 못했습니다.");
                        return;
                    }

                    if (expectedRole != null && user.getRole() != expectedRole) {
                        signOut();
                        callback.onError("선택한 사용자 유형과 계정 유형이 다릅니다.");
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

        // phone은 선택값으로 보고 누락 시 빈 문자열로 정규화한다.
        UserRole role = UserRole.valueOf(roleValue);
        return new User(documentSnapshot.getId(), role, name, email, phone == null ? "" : phone);
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
            return "가입된 이메일 정보를 찾지 못했습니다.";
        }
        return "비밀번호 재설정 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String resolveVerificationEmailMessage(Exception exception, boolean resend) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "계정 정보를 다시 확인해주세요.";
        }
        if (resend) {
            return "이메일 인증이 필요하지만 인증 메일을 다시 보내지 못했습니다. 잠시 후 다시 시도해주세요.";
        }
        return "회원가입은 완료됐지만 인증 메일을 보내지 못했습니다. 로그인 화면에서 다시 시도해주세요.";
    }
}
