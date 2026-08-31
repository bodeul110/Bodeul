package com.example.bodeul.ui.admin;

/**
 * 운영 관리자 데이터는 별도 관리자 웹에서만 접근하도록 Android 진입 경계를 고정한다.
 */
final class AdminDashboardAccessPolicy {
    private AdminDashboardAccessPolicy() {
    }

    static boolean canLoadLegacyDashboard(boolean firebaseBacked) {
        return !firebaseBacked;
    }
}
