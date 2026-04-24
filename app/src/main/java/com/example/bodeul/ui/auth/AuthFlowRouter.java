package com.example.bodeul.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.bodeul.MainActivity;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.admin.AdminActivity;
import com.example.bodeul.ui.manager.ManagerActivity;

/**
 * 로그인 이후 이동 규칙과 프로필 보완 필요 여부를 한곳에서 판단하는 보조 클래스다.
 */
public final class AuthFlowRouter {
    private AuthFlowRouter() {
    }

    public static boolean requiresProfileCompletion(User user) {
        // 동행 안내에 꼭 필요한 이름과 연락처가 비어 있으면 먼저 보완 화면으로 보낸다.
        return TextUtils.isEmpty(user.getName()) || TextUtils.isEmpty(user.getPhone());
    }

    @NonNull
    public static Intent createPostAuthIntent(Context context, User user) {
        if (requiresProfileCompletion(user)) {
            return ProfileCompletionActivity.createIntent(context);
        }

        Class<?> target;
        if (user.getRole() == UserRole.MANAGER) {
            target = ManagerActivity.class;
        } else if (user.getRole() == UserRole.ADMIN) {
            target = AdminActivity.class;
        } else {
            target = MainActivity.class;
        }
        return new Intent(context, target);
    }
}
