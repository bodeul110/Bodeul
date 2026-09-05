package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;

import org.junit.Test;

public class MockBodeulRepositoryEmergencyActionTest {
    @Test
    public void legacyEmergencyNotification_rejectsReadResolveAndReopenWithoutArtifacts() {
        MockBodeulRepository repository = new MockBodeulRepository();
        long createdAtMillis = 1_000L;
        repository.appendAdminActionArtifacts(
                AdminActionSourceType.EMERGENCY,
                AdminActionNotificationLevel.WARNING,
                "legacy-request",
                "",
                "기존 긴급 알림",
                "이전 데이터 조회용",
                "기존 긴급 기록",
                "상태 변경 금지 검증",
                "관리자A",
                createdAtMillis
        );

        AdminActionNotification legacyNotification = repository.getAdminActionNotifications().get(0);
        int auditCount = repository.getAdminAuditLogs().size();
        int deliveryCount = repository.getAdminActionDeliveries().size();

        assertNull(repository.markAdminActionNotificationRead(legacyNotification.getId()));
        assertNull(repository.updateAdminActionNotificationResolved(
                legacyNotification.getId(),
                true,
                "관리자A"
        ));
        assertNull(repository.updateAdminActionNotificationResolved(
                legacyNotification.getId(),
                false,
                "관리자A"
        ));

        AdminActionNotification unchangedNotification = repository.getAdminActionNotifications().get(0);
        assertFalse(unchangedNotification.isRead());
        assertFalse(unchangedNotification.isResolved());
        assertEquals(auditCount, repository.getAdminAuditLogs().size());
        assertEquals(deliveryCount, repository.getAdminActionDeliveries().size());
    }
}
