package com.example.bodeul.data;

import android.app.Activity;

import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/**
 * 로그인, 회원가입, 현재 사용자 조회를 담당하는 인증 저장소 계약이다.
 */
public interface AuthRepository {
    // 앱 시작이나 화면 진입 시 현재 로그인한 사용자를 확인한다.
    void getCurrentUser(RepositoryCallback<User> callback);

    // 이메일, 비밀번호, 역할 조합이 맞는지 확인하고 로그인 결과를 반환한다.
    void signIn(String email, String password, UserRole expectedRole, RepositoryCallback<User> callback);

    // 구글 계정 선택 UI를 띄워 Firebase 인증과 사용자 프로필 검증을 함께 처리한다.
    void signInWithGoogle(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback);

    // 새 사용자 계정을 만들고 바로 로그인 가능한 상태로 준비한다.
    void register(
            String name,
            String email,
            String phone,
            String password,
            UserRole role,
            RepositoryCallback<User> callback
    );

    // 입력한 이메일로 비밀번호 재설정 안내를 보낸다.
    void resetPassword(String email, RepositoryCallback<Void> callback);

    // 로그인 전에 이메일 인증 메일을 다시 보내야 할 때 사용한다.
    void resendVerificationEmail(String email, String password, RepositoryCallback<Void> callback);

    // 현재 세션을 종료하고 다음 진입에서 다시 로그인하도록 만든다.
    void signOut();

    // 저장소가 실제 Firebase를 사용하는지 화면에서 구분할 때 사용한다.
    boolean isFirebaseBacked();
}
