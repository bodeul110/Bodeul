package com.example.bodeul.ui.navigation;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.UserRole;

/**
 * 환자·보호자용 하단 내비게이션을 표시할 계정 역할만 구분한다.
 */
public final class ClientBottomNavigationVisibility {
    private ClientBottomNavigationVisibility() {
    }

    public static boolean isVisibleFor(@Nullable UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }
}
