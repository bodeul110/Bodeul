package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.User;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 운영 후속 저장과 액션 아티팩트 생성을 전담한다.
 */
final class FirebaseAdminOperationsStore {
    interface OperationsMapper {
        String normalizeText(@Nullable String value);

        void appendAdminActionArtifacts(
                WriteBatch batch,
                String requestId,
                String inquiryId,
                AdminActionSourceType sourceType,
                AdminActionNotificationLevel level,
                String title,
                String body,
                String actionSummary,
                String note,
                String actorName
        );

        String buildSettlementNotificationTitle(AdminSettlementStatus status);

        String buildSettlementNotificationBody(String requestId, AdminSettlementStatus status);

        String buildSettlementAuditSummary(AdminSettlementStatus status);

        String buildEmergencyNotificationTitle(AdminEmergencyIssueStatus status);

        String buildEmergencyNotificationBody(String requestId, AdminEmergencyIssueStatus status);

        String buildEmergencyAuditSummary(AdminEmergencyIssueStatus status);
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final OperationsMapper mapper;

    FirebaseAdminOperationsStore(FirebaseFirestore firestore, OperationsMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    void saveSettlementRecord(
            User currentUser,
            String requestId,
            @Nullable AdminSettlementStatus status,
            String note,
            CompletionListener listener
    ) {
        AdminSettlementStatus resolvedStatus =
                status == null ? AdminSettlementStatus.PENDING : status;
        Map<String, Object> document = new HashMap<>();
        document.put("requestId", requestId);
        document.put("status", resolvedStatus.name());
        document.put("note", mapper.normalizeText(note));
        document.put("handledByName", mapper.normalizeText(currentUser.getName()));
        document.put("handledAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection("adminSettlementRecords").document(requestId), document);
        mapper.appendAdminActionArtifacts(
                batch,
                requestId,
                "",
                AdminActionSourceType.SETTLEMENT,
                resolvedStatus == AdminSettlementStatus.NEEDS_REVIEW
                        ? AdminActionNotificationLevel.WARNING
                        : AdminActionNotificationLevel.INFO,
                mapper.buildSettlementNotificationTitle(resolvedStatus),
                mapper.buildSettlementNotificationBody(requestId, resolvedStatus),
                mapper.buildSettlementAuditSummary(resolvedStatus),
                mapper.normalizeText(note),
                mapper.normalizeText(currentUser.getName())
        );

        batch.commit()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("정산 후속 상태를 저장하지 못했습니다."));
    }

    void saveEmergencyIssue(
            User currentUser,
            String requestId,
            @Nullable AdminEmergencyIssueStatus status,
            String note,
            CompletionListener listener
    ) {
        AdminEmergencyIssueStatus resolvedStatus =
                status == null ? AdminEmergencyIssueStatus.REPORTED : status;
        Map<String, Object> document = new HashMap<>();
        document.put("requestId", requestId);
        document.put("status", resolvedStatus.name());
        document.put("note", mapper.normalizeText(note));
        document.put("handledByName", mapper.normalizeText(currentUser.getName()));
        document.put("handledAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection("adminEmergencyIssues").document(requestId), document);
        mapper.appendAdminActionArtifacts(
                batch,
                requestId,
                "",
                AdminActionSourceType.EMERGENCY,
                resolvedStatus == AdminEmergencyIssueStatus.REPORTED
                        ? AdminActionNotificationLevel.WARNING
                        : AdminActionNotificationLevel.INFO,
                mapper.buildEmergencyNotificationTitle(resolvedStatus),
                mapper.buildEmergencyNotificationBody(requestId, resolvedStatus),
                mapper.buildEmergencyAuditSummary(resolvedStatus),
                mapper.normalizeText(note),
                mapper.normalizeText(currentUser.getName())
        );

        batch.commit()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("긴급 이슈 대응 상태를 저장하지 못했습니다."));
    }
}
