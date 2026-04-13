package com.example.bodeul.data.mock;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/**
 * Firebase 없이 로그인 흐름을 확인할 수 있도록 제공하는 목업 인증 저장소다.
 */
public class MockAuthRepository implements AuthRepository {
    private final MockBodeulRepository repository;
    @Nullable
    private User cachedUser;

    public MockAuthRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getCurrentUser(RepositoryCallback<User> callback) {
        if (cachedUser == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }
        callback.onSuccess(cachedUser);
    }

    @Override
    public void signIn(String email, String password, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 데모 계정의 이메일과 비밀번호를 확인해 실제 로그인처럼 동작시킨다.
        if (!repository.isPasswordValid(email, password)) {
            callback.onError("이메일 또는 비밀번호를 확인해주세요.");
            return;
        }

        User user = repository.findUserByEmail(email);
        if (user == null) {
            callback.onError("사용자 정보를 찾지 못했습니다.");
            return;
        }

        if (user.getRole() != expectedRole) {
            callback.onError("선택한 사용자 유형과 계정 유형이 일치하지 않습니다.");
            return;
        }

        cachedUser = user;
        callback.onSuccess(user);
    }

    @Override
    public void signInWithGoogle(Activity activity, UserRole expectedRole, RepositoryCallback<User> callback) {
        // 목업 모드에서는 실제 구글 계정 선택 UI를 띄울 수 없어서 안내 메시지만 반환한다.
        callback.onError("데모 모드에서는 구글 로그인을 사용할 수 없습니다.");
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
        // 회원가입 요청은 목업 저장소의 사용자 목록에 바로 반영한다.
        User user = repository.registerUser(name, email, phone, role, password);
        if (user == null) {
            callback.onError("이미 등록된 이메일입니다.");
            return;
        }

        cachedUser = user;
        callback.onSuccess(user);
    }

    @Override
    public void resetPassword(String email, RepositoryCallback<Void> callback) {
        // 데모 모드에서는 등록된 이메일 존재 여부만 확인하고 성공 응답을 돌려준다.
        if (repository.findUserByEmail(email) == null) {
            callback.onError("가입한 이메일 정보를 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(null);
    }

    @Override
    public void resendVerificationEmail(String email, String password, RepositoryCallback<Void> callback) {
        // 데모 모드에서는 실제 인증 메일을 보내지 않으므로 안내 메시지만 반환한다.
        callback.onError("데모 모드에서는 인증 메일 재발송을 지원하지 않습니다.");
    }

    @Override
    public void signOut() {
        cachedUser = null;
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }
}
