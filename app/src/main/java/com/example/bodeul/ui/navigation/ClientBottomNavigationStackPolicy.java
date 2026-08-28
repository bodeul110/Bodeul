package com.example.bodeul.ui.navigation;

import androidx.annotation.NonNull;

/**
 * Activity 기반 최상위 탭 전환에서 현재 화면을 back stack에 남길지 결정한다.
 */
public final class ClientBottomNavigationStackPolicy {
    private ClientBottomNavigationStackPolicy() {
    }

    public static boolean shouldFinishCurrent(
            @NonNull ClientBottomNavigationTab currentTab,
            @NonNull ClientBottomNavigationTab destinationTab
    ) {
        return currentTab != destinationTab && currentTab != ClientBottomNavigationTab.HOME;
    }
}
