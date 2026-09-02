package com.example.bodeul.ui.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;

import org.junit.Test;

import java.util.List;

public class AdminActionCenterCoordinatorTest {
    @Test
    public void legacyEmergencyNotification_exposesNoStateActions() {
        AdminActionNotification notification = createNotification(
                AdminActionSourceType.EMERGENCY,
                false,
                false
        );

        assertTrue(AdminActionCenterCoordinator
                .resolveNotificationActionTypes(notification)
                .isEmpty());
    }

    @Test
    public void supportedNotification_keepsReadAndResolutionActions() {
        AdminActionNotification unreadNotification = createNotification(
                AdminActionSourceType.SUPPORT,
                false,
                false
        );
        List<AdminActionCenterActionType> unreadActions = AdminActionCenterCoordinator
                .resolveNotificationActionTypes(unreadNotification);
        assertEquals(2, unreadActions.size());
        assertEquals(AdminActionCenterActionType.MARK_READ, unreadActions.get(0));
        assertEquals(AdminActionCenterActionType.MARK_RESOLVED, unreadActions.get(1));

        AdminActionNotification resolvedNotification = createNotification(
                AdminActionSourceType.SETTLEMENT,
                true,
                true
        );
        List<AdminActionCenterActionType> resolvedActions = AdminActionCenterCoordinator
                .resolveNotificationActionTypes(resolvedNotification);
        assertEquals(1, resolvedActions.size());
        assertEquals(AdminActionCenterActionType.REOPEN, resolvedActions.get(0));
    }

    private AdminActionNotification createNotification(
            AdminActionSourceType sourceType,
            boolean read,
            boolean resolved
    ) {
        return new AdminActionNotification(
                "notification",
                sourceType,
                AdminActionNotificationLevel.WARNING,
                "request",
                "",
                "후속 알림",
                "상태 확인",
                "관리자",
                1_000L,
                read,
                read ? 2_000L : 0L,
                resolved,
                resolved ? 3_000L : 0L,
                resolved ? "관리자" : ""
        );
    }
}
