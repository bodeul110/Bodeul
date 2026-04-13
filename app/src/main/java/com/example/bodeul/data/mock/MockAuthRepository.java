package com.example.bodeul.data.mock;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/**
 * Firebase 없이 로그인 흐름을 점검할 수 있도록 제공하는 목업 인증 저장소다.
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
        // 데모 계정의 이메일과 비밀번호를 확인해 실제 로그인 흐름처럼 동작시킨다.
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
            callback.onError("선택한 사용자 유형과 계정 유형이 다릅니다.");
            return;
        }

        cachedUser = user;
        callback.onSuccess(user);
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
        // 데모 모드에서는 등록된 이메일 존재 여부만 확인해 재설정 흐름을 흉내 낸다.
        if (repository.findUserByEmail(email) == null) {
            callback.onError("가입된 이메일 정보를 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(null);
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
