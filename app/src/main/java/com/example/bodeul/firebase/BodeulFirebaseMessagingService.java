package com.example.bodeul.firebase;

import com.example.bodeul.data.NotificationTokenRegistrar;
import com.example.bodeul.data.ServiceLocator;
import com.google.firebase.messaging.FirebaseMessagingService;

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
}
