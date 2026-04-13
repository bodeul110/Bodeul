package com.example.bodeul.data;

import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/**
 * 로그인, 회원가입, 현재 사용자 조회를 담당하는 인증 저장소 계약이다.
 */
public interface AuthRepository {
    // 앱 시작이나 화면 진입 시 현재 로그인된 사용자를 확인한다.
    void getCurrentUser(RepositoryCallback<User> callback);

    // 이메일, 비밀번호, 역할 조합이 맞는지 확인하고 로그인 결과를 반환한다.
    void signIn(String email, String password, UserRole expectedRole, RepositoryCallback<User> callback);

    // 새 사용자 계정을 만들고 바로 로그인된 상태로 넘긴다.
    void register(
            String name,
            String email,
            String phone,
            String password,
            UserRole role,
            RepositoryCallback<User> callback
    );

    // 입력한 이메일로 비밀번호 재설정 안내를 전송한다.
    void resetPassword(String email, RepositoryCallback<Void> callback);

    // 현재 세션을 종료해 다음 진입 시 다시 로그인하도록 만든다.
    void signOut();

    // 저장소가 실제 Firebase를 사용하는지 여부를 화면에서 구분할 때 사용한다.
    boolean isFirebaseBacked();
}
