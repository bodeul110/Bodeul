package com.example.bodeul.firebase;

import com.example.bodeul.data.NotificationTokenRegistrar;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.ui.booking.BookingLiveLocationActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.util.AppActivityTracker;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * FCM 토큰이 갱신되면 현재 사용자 문서에도 같은 토큰을 다시 반영한다.
 */
public class BodeulFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        NotificationTokenRegistrar registrar = ServiceLocator.provideNotificationTokenRegistrar(this);
        registrar.syncCurrentUserToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        CompanionChatPushPayload chatPayload = CompanionChatPushPayload.from(remoteMessage);
        if (chatPayload != null) {
            sendBroadcast(CompanionChatPushContract.createRefreshIntent(getPackageName(), chatPayload));
            if (AppActivityTracker.isCurrentActivity(CompanionChatActivity.class)) {
                return;
            }
            CompanionChatPushNotifier.showMessageNotification(this, chatPayload);
            return;
        }

        CompanionLocationAlertPushPayload locationAlertPayload = CompanionLocationAlertPushPayload.from(remoteMessage);
        if (locationAlertPayload != null) {
            if (AppActivityTracker.isCurrentActivity(BookingLiveLocationActivity.class)) {
                return;
            }
            CompanionLocationAlertPushNotifier.showNotification(this, locationAlertPayload);
            return;
        }

        ClientSupportPushPayload payload = ClientSupportPushPayload.from(remoteMessage);
        if (payload == null) {
            return;
        }

        sendBroadcast(ClientSupportPushContract.createRefreshIntent(getPackageName(), payload));
        if (AppActivityTracker.isCurrentActivity(ClientSupportActivity.class)
                || AppActivityTracker.isCurrentActivity(HealthInfoActivity.class)) {
            return;
        }
        ClientSupportPushNotifier.showAnsweredNotification(this, payload);
    }
}
