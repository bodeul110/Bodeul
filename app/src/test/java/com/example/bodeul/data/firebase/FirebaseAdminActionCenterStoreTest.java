package com.example.bodeul.data.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;

import org.junit.Test;

public class FirebaseAdminActionCenterStoreTest {
    @Test
    public void legacyEmergencyNotification_failsClosedBeforeFirestoreWrites() {
        assertFalse(FirebaseAdminActionCenterStore.canMutateNotification(null));
        assertFalse(FirebaseAdminActionCenterStore.canMutateNotification(
                createNotification(AdminActionSourceType.EMERGENCY)
        ));
    }

    @Test
    public void supportedNotification_keepsCurrentStateWrites() {
        assertTrue(FirebaseAdminActionCenterStore.canMutateNotification(
                createNotification(AdminActionSourceType.SETTLEMENT)
        ));
        assertTrue(FirebaseAdminActionCenterStore.canMutateNotification(
                createNotification(AdminActionSourceType.SUPPORT)
        ));
    }

    private AdminActionNotification createNotification(AdminActionSourceType sourceType) {
        return new AdminActionNotification(
                "notification",
                sourceType,
                AdminActionNotificationLevel.WARNING,
                "request",
                "",
                "후속 알림",
                "상태 확인",
                "관리자",
                1_000L
        );
    }
}
