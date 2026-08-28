package com.example.bodeul.ui.navigation;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.bodeul.MainActivity;
import com.example.bodeul.ui.booking.ClientBookingHistoryActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.profile.ClientProfileActivity;

/**
 * 공통 하단 탭을 기존 최상위 Activity로 연결하고 불필요한 화면 중첩을 줄인다.
 */
public final class ClientBottomNavigationRouter {
    private ClientBottomNavigationRouter() {
    }

    public static void open(
            @NonNull Activity activity,
            @NonNull ClientBottomNavigationTab currentTab,
            @NonNull ClientBottomNavigationTab destinationTab
    ) {
        if (currentTab == destinationTab) {
            return;
        }

        Intent intent;
        switch (destinationTab) {
            case SCHEDULE_HISTORY:
                intent = new Intent(activity, ClientBookingHistoryActivity.class);
                break;
            case COMPANION_ROOM:
                intent = CompanionChatActivity.createIntent(activity);
                break;
            case PROFILE:
                intent = new Intent(activity, ClientProfileActivity.class);
                break;
            case HOME:
            default:
                intent = new Intent(activity, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                break;
        }

        activity.startActivity(intent);
        if (ClientBottomNavigationStackPolicy.shouldFinishCurrent(currentTab, destinationTab)) {
            activity.finish();
        }
    }
}
