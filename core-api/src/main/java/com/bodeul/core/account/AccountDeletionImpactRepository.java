package com.bodeul.core.account;

import java.util.UUID;

interface AccountDeletionImpactRepository {

    PostgreSqlImpact inspect(UUID userId);

    record PostgreSqlImpact(
            long profileCount,
            long appointmentCount,
            long activeAppointmentCount,
            long companionSessionCount,
            long activeCompanionSessionCount,
            long sessionReportCount,
            long appointmentFollowUpCount,
            long assignmentAuditCount,
            long relatedChatMessageCount,
            long sentChatMessageCount,
            long relatedChatAttachmentCount,
            long relatedChatReadReceiptCount,
            long relatedLocationCount,
            long activeLegalHoldCount) {
    }
}
