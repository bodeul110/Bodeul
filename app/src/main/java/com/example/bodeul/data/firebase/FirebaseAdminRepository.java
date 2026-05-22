package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationContract;
import com.example.bodeul.domain.model.AdminActionNotificationFilterKey;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionNotificationPriority;
import com.example.bodeul.domain.model.AdminActionNotificationState;
import com.example.bodeul.domain.model.AdminActionDeliveryChannel;
import com.example.bodeul.domain.model.AdminActionDeliveryFilterKey;
import com.example.bodeul.domain.model.AdminActionDeliveryPriority;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionDeliverySlaStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryState;
import com.example.bodeul.domain.model.AdminActionDeliveryStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryTrigger;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.AdminAuditLogEntry;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminActionOverview;
import com.example.bodeul.domain.model.AdminEmergencyIssueRecord;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminRequestActionOverview;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AdminSettlementRecord;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore에 저장된 요청, 매니저, 가이드를 조합해 관리자 운영 화면에 연결한다.
 */
public class FirebaseAdminRepository implements AdminRepository {
    private final FirebaseFirestore firestore;

    public FirebaseAdminRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getAdminDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        loadDashboardWithArtifacts(currentUser, callback);
    }

    @Override
    public void assignManager(
            User currentUser,
            String requestId,
            String managerUserId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        loadDashboardWithActions(currentUser, new RepositoryCallback<AdminDashboard>() {
            @Override
            public void onSuccess(AdminDashboard dashboard) {
                AdminRequestOverview targetOverview = findRequestOverview(dashboard, requestId);
                if (targetOverview == null) {
                    callback.onError("배정할 요청을 찾지 못했습니다.");
                    return;
                }
                if (!targetOverview.hasLinkedParticipants()) {
                    callback.onError("환자와 보호자 계정 연결이 완료된 요청만 배정할 수 있습니다.");
                    return;
                }
                if (!targetOverview.hasGuide()) {
                    callback.onError("해당 병원과 진료과 가이드가 없어 먼저 가이드를 등록해야 합니다.");
                    return;
                }
                if (!containsManager(dashboard.getAvailableManagers(), managerUserId)) {
                    callback.onError("선택한 매니저는 현재 다른 동행을 진행 중입니다.");
                    return;
                }

                WriteBatch batch = firestore.batch();
                batch.update(
                        firestore.collection("appointmentRequests").document(requestId),
                        "status",
                        AppointmentStatus.MATCHED.name(),
                        "managerUserId",
                        managerUserId,
                        "updatedAt",
                        FieldValue.serverTimestamp()
                );

                DocumentReference sessionReference = firestore.collection("companionSessions")
                        .document("session-" + requestId);
                Map<String, Object> sessionDocument = new HashMap<>();
                sessionDocument.put("appointmentRequestId", requestId);
                sessionDocument.put("managerUserId", managerUserId);
                sessionDocument.put("currentStepOrder", 1);
                sessionDocument.put("currentStatus", SessionStatus.READY.name());
                sessionDocument.put("guardianUpdate", "");
                sessionDocument.put("locationSummary", "");
                sessionDocument.put("fieldPhotoNote", "");
                sessionDocument.put("medicationNote", "");
                sessionDocument.put("pharmacySummary", "");
                sessionDocument.put("pharmacyCompleted", false);
                sessionDocument.put("createdAt", FieldValue.serverTimestamp());
                sessionDocument.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(sessionReference, sessionDocument);

                batch.commit()
                        .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                                                                                                 .addOnFailureListener(exception ->
                                callback.onError("매니저 배정을 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        List<Map<String, Object>> steps = buildGuideStepDocuments(stepLines);
        if (steps.isEmpty()) {
            callback.onError("병원 가이드를 저장하지 못했습니다. 단계 내용을 다시 확인해주세요.");
            return;
        }

        String normalizedHospital = normalizeText(hospitalName);
        String normalizedDepartment = normalizeText(departmentName);
        firestore.collection("hospitalGuides")
                .whereEqualTo("hospitalName", normalizedHospital)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentReference targetReference = null;
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        String savedDepartment = documentSnapshot.getString("departmentName");
                        if (normalizedDepartment.equals(savedDepartment)) {
                            targetReference = documentSnapshot.getReference();
                            break;
                        }
                    }

                    Map<String, Object> guideDocument = new HashMap<>();
                    guideDocument.put("hospitalName", normalizedHospital);
                    guideDocument.put("departmentName", normalizedDepartment);
                    guideDocument.put("steps", steps);
                    guideDocument.put("updatedAt", FieldValue.serverTimestamp());

                    if (targetReference == null) {
                        guideDocument.put("createdAt", FieldValue.serverTimestamp());
                        firestore.collection("hospitalGuides")
                                .add(guideDocument)
                                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                                .addOnFailureListener(exception ->
                                        callback.onError("병원 가이드를 저장하지 못했습니다."));
                        return;
                    }

                    targetReference.update(guideDocument)
                            .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("병원 가이드를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("기존 병원 가이드를 확인하지 못했습니다."));
    }

    @Override
    public void deleteHospitalGuide(
            User currentUser,
            String guideId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        firestore.collection("hospitalGuides")
                .document(guideId)
                .delete()
                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("병원 가이드를 삭제하지 못했습니다."));
    }

    @Override
    public void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            callback.onError("서류 검토 상태가 올바르지 않습니다.");
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("매니저 계정을 찾지 못했습니다.");
                        return;
                    }

                    ManagerHomeProfile profile = toManagerHomeProfile(documentSnapshot);
                    if (normalizeText(profile.getDocumentSummary()).isEmpty()) {
                        callback.onError("검토할 서류 요약이 아직 등록되지 않았습니다.");
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    List<ManagerDocumentHistoryEntry> historyEntries = appendManagerDocumentHistory(
                            toManagerDocumentHistory(documentSnapshot),
                            new ManagerDocumentHistoryEntry(
                                    status == ManagerDocumentStatus.APPROVED
                                            ? ManagerDocumentHistoryEventType.APPROVED
                                            : ManagerDocumentHistoryEventType.REJECTED,
                                    System.currentTimeMillis(),
                                    normalizeText(currentUser.getName()),
                                    profile.getDocumentSummary(),
                                    normalizeText(reviewNote)
                            )
                    );
                    updates.put("managerDocumentStatus", status.name());
                    updates.put("managerDocumentReviewNote", normalizeText(reviewNote));
                    updates.put("managerDocumentReviewedAt", FieldValue.serverTimestamp());
                    updates.put("managerDocumentReviewedByName", normalizeText(currentUser.getName()));
                    updates.put("managerDocumentHistory", toManagerDocumentHistoryPayload(historyEntries));

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("매니저 서류 검토 상태를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 계정 정보를 불러오지 못했습니다."));
    }

    @Override
    public void saveSettlementRecord(
            User currentUser,
            String requestId,
            AdminSettlementStatus status,
            String note,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }

        AdminSettlementStatus resolvedStatus =
                status == null ? AdminSettlementStatus.PENDING : status;
        Map<String, Object> document = new HashMap<>();
        document.put("requestId", requestId);
        document.put("status", resolvedStatus.name());
        document.put("note", normalizeText(note));
        document.put("handledByName", normalizeText(currentUser.getName()));
        document.put("handledAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection("adminSettlementRecords").document(requestId), document);
        appendAdminActionArtifacts(
                batch,
                requestId,
                "",
                AdminActionSourceType.SETTLEMENT,
                resolvedStatus == AdminSettlementStatus.NEEDS_REVIEW
                        ? AdminActionNotificationLevel.WARNING
                        : AdminActionNotificationLevel.INFO,
                buildSettlementNotificationTitle(resolvedStatus),
                buildSettlementNotificationBody(requestId, resolvedStatus),
                buildSettlementAuditSummary(resolvedStatus),
                normalizeText(note),
                normalizeText(currentUser.getName())
        );

        batch.commit()
                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("정산 후속 상태를 저장하지 못했습니다."));
    }

    @Override
    public void saveEmergencyIssue(
            User currentUser,
            String requestId,
            AdminEmergencyIssueStatus status,
            String note,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }

        AdminEmergencyIssueStatus resolvedStatus =
                status == null ? AdminEmergencyIssueStatus.REPORTED : status;
        Map<String, Object> document = new HashMap<>();
        document.put("requestId", requestId);
        document.put("status", resolvedStatus.name());
        document.put("note", normalizeText(note));
        document.put("handledByName", normalizeText(currentUser.getName()));
        document.put("handledAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection("adminEmergencyIssues").document(requestId), document);
        appendAdminActionArtifacts(
                batch,
                requestId,
                "",
                AdminActionSourceType.EMERGENCY,
                resolvedStatus == AdminEmergencyIssueStatus.REPORTED
                        ? AdminActionNotificationLevel.WARNING
                        : AdminActionNotificationLevel.INFO,
                buildEmergencyNotificationTitle(resolvedStatus),
                buildEmergencyNotificationBody(requestId, resolvedStatus),
                buildEmergencyAuditSummary(resolvedStatus),
                normalizeText(note),
                normalizeText(currentUser.getName())
        );

        batch.commit()
                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("긴급 이슈 대응 상태를 저장하지 못했습니다."));
    }

    @Override
    public void respondSupportInquiry(
            User currentUser,
            String inquiryId,
            String response,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", SupportInquiryStatus.ANSWERED.name());
        updates.put("responseText", normalizeText(response));
        updates.put("respondedByName", normalizeText(currentUser.getName()));
        updates.put("respondedAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.update(firestore.collection("supportInquiries").document(inquiryId), updates);
        /*
        appendAdminActionArtifacts(
                batch,
                "",
                inquiryId,
                AdminActionSourceType.SUPPORT,
                AdminActionNotificationLevel.INFO,
                "문의 응답 저장",
                buildSupportNotificationBody(inquiryId),
                "문의 응답 저장",
                normalizeText(response),
                normalizeText(currentUser.getName())
        );
        */
        appendAdminActionArtifacts(
                batch,
                "",
                inquiryId,
                AdminActionSourceType.SUPPORT,
                AdminActionNotificationLevel.INFO,
                "문의 응답 저장",
                buildSupportNotificationBody(inquiryId),
                "문의 응답 저장",
                normalizeText(response),
                normalizeText(currentUser.getName())
        );

        batch.commit()
                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("문의 응답을 저장하지 못했습니다."));
    }

    @Override
    public void markActionNotificationRead(
            User currentUser,
            String notificationId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }

        boolean useActionDeliveryPipeline = true;
        if (useActionDeliveryPipeline) {
            DocumentReference notificationReference = firestore.collection("adminActionNotifications")
                    .document(notificationId);
            notificationReference.get()
                    .addOnSuccessListener(documentSnapshot -> {
                        AdminActionNotification notification = toAdminActionNotification(documentSnapshot);
                        if (notification == null) {
                            callback.onError("읽음 처리할 알림을 찾지 못했습니다.");
                            return;
                        }
                        if (notification.isRead()) {
                            loadDashboardWithArtifacts(currentUser, callback);
                            return;
                        }

                        WriteBatch batch = firestore.batch();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isRead", true);
                        updates.put("readAt", FieldValue.serverTimestamp());
                        updates.put("updatedAt", FieldValue.serverTimestamp());
                        updates.putAll(buildNotificationContractFields(
                                notification.getSourceType(),
                                notification.getLevel(),
                                true,
                                notification.isResolved()
                        ));
                        batch.update(notificationReference, updates);
                        appendActionDeliveryArtifacts(
                                batch,
                                notification,
                                AdminActionDeliveryTrigger.NOTIFICATION_READ,
                                AdminActionDeliveryStatus.CONFIRMED,
                                AdminActionDeliveryStatus.CONFIRMED,
                                "관리자 앱 푸시 기준 읽음 확인이 완료됐습니다.",
                                "운영 피드에 읽음 상태 반영을 완료했습니다."
                        );
                        batch.commit()
                                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                                .addOnFailureListener(exception ->
                                        callback.onError("읽음 처리할 알림을 저장하지 못했습니다."));
                    })
                    .addOnFailureListener(exception ->
                            callback.onError("알림 정보를 불러오지 못했습니다."));
            return;
        }

        firestore.collection("adminActionNotifications")
                .document(notificationId)
                .update(
                        "isRead",
                        true,
                        "readAt",
                        FieldValue.serverTimestamp(),
                        "state",
                        AdminActionNotificationState.READ.getValue(),
                        "priority",
                        AdminActionNotificationPriority.MONITORING.getValue(),
                        "filterKeys",
                        toFilterKeyValues(AdminActionNotificationContract.resolveFilterKeys(
                                AdminActionNotificationState.READ
                        )),
                        "updatedAt",
                        FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("읽음 처리할 알림을 저장하지 못했습니다."));
    }

    @Override
    public void updateActionNotificationResolved(
            User currentUser,
            String notificationId,
            boolean resolved,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }

        DocumentReference notificationReference = firestore.collection("adminActionNotifications")
                .document(notificationId);
        notificationReference.get()
                .addOnSuccessListener(documentSnapshot -> {
                    AdminActionNotification notification = toAdminActionNotification(documentSnapshot);
                    if (notification == null) {
                        callback.onError("상태를 변경할 알림을 찾지 못했습니다.");
                        return;
                    }

                    WriteBatch batch = firestore.batch();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isRead", true);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    if (!notification.isRead()) {
                        updates.put("readAt", FieldValue.serverTimestamp());
                    }
                    if (resolved) {
                        updates.put("isResolved", true);
                        updates.put("resolvedAt", FieldValue.serverTimestamp());
                        updates.put("resolvedByName", normalizeText(currentUser.getName()));
                    } else {
                        updates.put("isResolved", false);
                        updates.put("resolvedAt", FieldValue.delete());
                        updates.put("resolvedByName", "");
                    }
                    updates.putAll(buildNotificationContractFields(
                            notification.getSourceType(),
                            notification.getLevel(),
                            true,
                            resolved
                    ));
                    batch.update(notificationReference, updates);
                    appendActionResolutionAudit(
                            batch,
                            notification,
                            resolved,
                            normalizeText(currentUser.getName())
                    );
                    appendActionDeliveryArtifacts(
                            batch,
                            notification,
                            resolved
                                    ? AdminActionDeliveryTrigger.NOTIFICATION_RESOLVED
                                    : AdminActionDeliveryTrigger.NOTIFICATION_REOPENED,
                            resolved
                                    ? AdminActionDeliveryStatus.SKIPPED
                                    : AdminActionDeliveryStatus.SENT,
                            AdminActionDeliveryStatus.CONFIRMED,
                            resolved
                                    ? "관리자 처리 결과라 추가 푸시를 보내지 않았습니다."
                                    : "재오픈 알림을 관리자 앱 푸시 발송 대기열에 다시 등록했습니다.",
                            resolved
                                    ? "운영 피드에 해결 완료 반영을 마쳤습니다."
                                    : "운영 피드에 재오픈 상태 반영을 마쳤습니다."
                    );
                    batch.commit()
                            .addOnSuccessListener(unused -> loadDashboardWithArtifacts(currentUser, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("알림 상태를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("알림 정보를 불러오지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void loadDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("hospitalGuides")
                                                                .get()
                                                                .addOnSuccessListener(guideSnapshot -> callback.onSuccess(
                                                                        buildDashboard(
                                                                                currentUser,
                                                                                requestSnapshot,
                                                                                userSnapshot,
                                                                                sessionSnapshot,
                                                                                guideSnapshot
                                                                        )
                                                                ))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("병원 가이드 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다."));
    }

    private void loadDashboardWithReports(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("sessionReports")
                                                                .get()
                                                                .addOnSuccessListener(reportSnapshot ->
                                                                        firestore.collection("hospitalGuides")
                                                                                .get()
                                                                                 .addOnSuccessListener(guideSnapshot -> callback.onSuccess(
                                                                                         buildDashboard(
                                                                                                 currentUser,
                                                                                                 requestSnapshot,
                                                                                                 userSnapshot,
                                                                                                 sessionSnapshot,
                                                                                                 guideSnapshot
                                                                                         )
                                                                                 ))
                                                                                .addOnFailureListener(exception ->
                                                                                        callback.onError("병원 가이드 목록을 불러오지 못했습니다.")))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("최종 리포트 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다."));
    }

    private void loadDashboardWithActions(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        loadDashboardWithArtifacts(currentUser, callback);
        /*
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("sessionReports")
                                                                .get()
                                                                .addOnSuccessListener(reportSnapshot ->
                                                                        firestore.collection("adminSettlementRecords")
                                                                                .get()
                                                                                .addOnSuccessListener(settlementSnapshot ->
                                                                                        firestore.collection("adminEmergencyIssues")
                                                                                                .get()
                                                                                                .addOnSuccessListener(emergencySnapshot -> {
                                                                                                    firestore.collection("appointmentFollowUps")
                                                                                                            .get()
                                                                                                            .addOnSuccessListener(followUpSnapshot -> {
                                                                                                                firestore.collection("supportInquiries")
                                                                                                                        .get()
                                                                                                                        .addOnSuccessListener(supportSnapshot -> {
                                                                                                                            firestore.collection("adminActionNotifications")
                                                                                                                                    .get()
                                                                                                                                    .addOnSuccessListener(guideSnapshot ->
                                                                                                                                            callback.onSuccess(
                                                                                                                                                    buildDashboard(
                                                                                                                                                            currentUser,
                                                                                                                                                            requestSnapshot,
                                                                                                                                                            userSnapshot,
                                                                                                                                                            sessionSnapshot,
                                                                                                                                                            reportSnapshot,
                                                                                                                                                            settlementSnapshot,
                                                                                                                                                            emergencySnapshot,
                                                                                                                                                            followUpSnapshot,
                                                                                                                                                            supportSnapshot,
                                                                                                                                                            guideSnapshot
                                                                                                                                                    )
                                                                                                                                            ))
                                                                                                                                    .addOnFailureListener(exception ->
                                                                                                                                            callback.onError("병원 가이드 목록을 불러오지 못했습니다."));
                                                                                                                        })
                                                                                                                        .addOnFailureListener(exception ->
                                                                                                                                callback.onError("문의 내역을 불러오지 못했습니다."));
                                                                                                            })
                                                                                                            .addOnFailureListener(exception ->
                                                                                                                    callback.onError("후속 이력을 불러오지 못했습니다."));
                                                                                                })
                                                                                                .addOnFailureListener(exception ->
                                                                                                        callback.onError("긴급 이슈 이력을 불러오지 못했습니다.")))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("최종 리포트 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다.")));
    }

        */
    }

    /*
    private void loadDashboardWithArtifacts(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("sessionReports")
                                                                .get()
                                                                .addOnSuccessListener(reportSnapshot ->
                                                                        firestore.collection("adminSettlementRecords")
                                                                                .get()
                                                                                .addOnSuccessListener(settlementSnapshot ->
                                                                                        firestore.collection("adminEmergencyIssues")
                                                                                                .get()
                                                                                                .addOnSuccessListener(emergencySnapshot ->
                                                                                                        firestore.collection("appointmentFollowUps")
                                                                                                                .get()
                                                                                                                .addOnSuccessListener(followUpSnapshot ->
                                                                                                                        firestore.collection("supportInquiries")
                                                                                                                                .get()
                                                                                                                                .addOnSuccessListener(supportSnapshot ->
                                                                                                                                        firestore.collection("adminActionNotifications")
                                                                                                                                                .get()
                                                                                                                                                .addOnSuccessListener(actionNotificationSnapshot ->
                                                                                                                                                        firestore.collection("adminActionDeliveries")
                                                                                                                                                                .get()
                                                                                                                                                                .addOnSuccessListener(actionDeliverySnapshot ->
                                                                                                                                                                        firestore.collection("adminAuditLogs")
                                                                                                                                                                                .get()
                                                                                                                                                                                .addOnSuccessListener(auditLogSnapshot ->
                                                                                                                                                                        firestore.collection("hospitalGuides")
                                                                                                                                                                                .get()
                                                                                                                                                                                .addOnSuccessListener(guideSnapshot ->
                                                                                                                                                                                        callback.onSuccess(
                                                                                                                                                                                                buildDashboard(
                                                                                                                                                                                                        currentUser,
                                                                                                                                                                                                        requestSnapshot,
                                                                                                                                                                                                        userSnapshot,
                                                                                                                                                                                                        sessionSnapshot,
                                                                                                                                                                                                        reportSnapshot,
                                                                                                                                                                                                        settlementSnapshot,
                                                                                                                                                                                                        emergencySnapshot,
                                                                                                                                                                                                        followUpSnapshot,
                                                                                                                                                                                                        supportSnapshot,
                                                                                                                                                                                                        actionNotificationSnapshot,
                                                                                                                                                                                                        actionDeliverySnapshot,
                                                                                                                                                                                                        auditLogSnapshot,
                                                                                                                                                                                                        guideSnapshot
                                                                                                                                                                                                )
                                                                                                                                                                                        ))
                                                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                                                        callback.onError("병원 가이드 목록을 불러오지 못했습니다.")))
                                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                                        callback.onError("감사 로그 목록을 불러오지 못했습니다.")))
                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                        callback.onError("후속 알림 목록을 불러오지 못했습니다.")))
                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                        callback.onError("문의 이력을 불러오지 못했습니다.")))
                                                                                                                .addOnFailureListener(exception ->
                                                                                                                        callback.onError("후속 이력을 불러오지 못했습니다.")))
                                                                                                .addOnFailureListener(exception ->
                                                                                                        callback.onError("긴급 이슈 이력을 불러오지 못했습니다.")))
                                                                                .addOnFailureListener(exception ->
                                                                                        callback.onError("정산 후속 이력을 불러오지 못했습니다.")))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("최종 리포트 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다."));
    }

    */

    private void loadDashboardWithArtifacts(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("sessionReports")
                                                                .get()
                                                                .addOnSuccessListener(reportSnapshot ->
                                                                        firestore.collection("adminSettlementRecords")
                                                                                .get()
                                                                                .addOnSuccessListener(settlementSnapshot ->
                                                                                        firestore.collection("adminEmergencyIssues")
                                                                                                .get()
                                                                                                .addOnSuccessListener(emergencySnapshot ->
                                                                                                        firestore.collection("appointmentFollowUps")
                                                                                                                .get()
                                                                                                                .addOnSuccessListener(followUpSnapshot ->
                                                                                                                        firestore.collection("supportInquiries")
                                                                                                                                .get()
                                                                                                                                .addOnSuccessListener(supportSnapshot ->
                                                                                                                                        firestore.collection("adminActionNotifications")
                                                                                                                                                .get()
                                                                                                                                                .addOnSuccessListener(actionNotificationSnapshot ->
                                                                                                                                                        firestore.collection("adminActionDeliveries")
                                                                                                                                                                .get()
                                                                                                                                                                .addOnSuccessListener(actionDeliverySnapshot ->
                                                                                                                                                                        firestore.collection("adminAuditLogs")
                                                                                                                                                                                .get()
                                                                                                                                                                                .addOnSuccessListener(auditLogSnapshot ->
                                                                                                                                                                                        firestore.collection("hospitalGuides")
                                                                                                                                                                                                .get()
                                                                                                                                                                                                .addOnSuccessListener(guideSnapshot ->
                                                                                                                                                                                                        callback.onSuccess(
                                                                                                                                                                                                                buildDashboard(
                                                                                                                                                                                                                        currentUser,
                                                                                                                                                                                                                        requestSnapshot,
                                                                                                                                                                                                                        userSnapshot,
                                                                                                                                                                                                                        sessionSnapshot,
                                                                                                                                                                                                                        reportSnapshot,
                                                                                                                                                                                                                        settlementSnapshot,
                                                                                                                                                                                                                        emergencySnapshot,
                                                                                                                                                                                                                        followUpSnapshot,
                                                                                                                                                                                                                        supportSnapshot,
                                                                                                                                                                                                                        actionNotificationSnapshot,
                                                                                                                                                                                                                        actionDeliverySnapshot,
                                                                                                                                                                                                                        auditLogSnapshot,
                                                                                                                                                                                                                        guideSnapshot
                                                                                                                                                                                                                )
                                                                                                                                                                                                        ))
                                                                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                                                                        callback.onError("병원 가이드 목록을 불러오지 못했습니다.")))
                                                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                                                        callback.onError("감사 로그 목록을 불러오지 못했습니다.")))
                                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                                        callback.onError("후속 알림 전달 기록을 불러오지 못했습니다.")))
                                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                                        callback.onError("후속 알림 목록을 불러오지 못했습니다.")))
                                                                                                                                .addOnFailureListener(exception ->
                                                                                                                                        callback.onError("문의 이력을 불러오지 못했습니다.")))
                                                                                                                .addOnFailureListener(exception ->
                                                                                                                        callback.onError("후속 이력을 불러오지 못했습니다.")))
                                                                                                .addOnFailureListener(exception ->
                                                                                                        callback.onError("긴급 이슈 이력을 불러오지 못했습니다.")))
                                                                                .addOnFailureListener(exception ->
                                                                                        callback.onError("정산 후속 이력을 불러오지 못했습니다.")))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("최종 리포트 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다."));
    }

    private AdminDashboard buildDashboard(
            User currentUser,
            QuerySnapshot requestSnapshot,
            QuerySnapshot userSnapshot,
            QuerySnapshot sessionSnapshot,
            QuerySnapshot reportSnapshot,
            QuerySnapshot settlementSnapshot,
            QuerySnapshot emergencySnapshot,
            QuerySnapshot followUpSnapshot,
            QuerySnapshot supportSnapshot,
            QuerySnapshot actionNotificationSnapshot,
            QuerySnapshot actionDeliverySnapshot,
            QuerySnapshot auditLogSnapshot,
            QuerySnapshot guideSnapshot
    ) {
        List<AppointmentRequest> requests = toSortedRequests(requestSnapshot);

        Map<String, User> usersById = new HashMap<>();
        List<String> activeManagerIds = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null
                    && session.getStatus() != SessionStatus.COMPLETED
                    && session.getStatus() != SessionStatus.CANCELED) {
                activeManagerIds.add(session.getManagerUserId());
            }
        }

        List<User> availableManagers = new ArrayList<>();
        List<User> busyManagers = new ArrayList<>();
        List<ManagerDocumentOverview> managerDocumentOverviews = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : userSnapshot.getDocuments()) {
            User user = toUser(documentSnapshot);
            if (user == null) {
                continue;
            }
            usersById.put(user.getId(), user);
            if (user.getRole() != UserRole.MANAGER) {
                continue;
            }
            if (activeManagerIds.contains(user.getId())) {
                busyManagers.add(user);
            } else {
                availableManagers.add(user);
            }
            managerDocumentOverviews.add(new ManagerDocumentOverview(
                    user,
                    toManagerHomeProfile(documentSnapshot),
                    toManagerDocumentHistory(documentSnapshot)
            ));
        }

        Map<String, CompanionSession> sessionsByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null) {
                sessionsByRequestId.put(session.getAppointmentRequestId(), session);
            }
        }

        Map<String, SessionReport> reportsBySessionId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : reportSnapshot.getDocuments()) {
            SessionReport report = toReport(documentSnapshot);
            if (report != null) {
                reportsBySessionId.put(report.getSessionId(), report);
            }
        }

        List<HospitalGuide> guides = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : guideSnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null) {
                guides.add(guide);
            }
        }

        List<AdminRequestActionOverview> requestActionOverviews = toAdminRequestActionOverviews(
                settlementSnapshot,
                emergencySnapshot,
                followUpSnapshot
        );
        List<AdminActionNotification> actionNotifications =
                toAdminActionNotifications(actionNotificationSnapshot);
        List<AdminActionDeliveryRecord> actionDeliveries =
                toAdminActionDeliveries(actionDeliverySnapshot);
        List<AdminAuditLogEntry> auditLogs = toAdminAuditLogs(auditLogSnapshot);
        AdminActionOverview actionOverview = AdminActionContract.createOverview(
                actionNotifications,
                auditLogs,
                actionDeliveries
        );
        List<SupportInquiry> supportInquiries = toSupportInquiries(supportSnapshot);

        List<AdminRequestOverview> pendingRequests = new ArrayList<>();
        List<AdminRequestOverview> managedRequests = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            CompanionSession session = sessionsByRequestId.get(request.getId());
            AdminRequestOverview overview = new AdminRequestOverview(
                    request,
                    usersById.get(request.getPatientUserId()),
                    usersById.get(request.getGuardianUserId()),
                    usersById.get(request.getManagerUserId()),
                    session,
                    session == null ? null : reportsBySessionId.get(session.getId()),
                    hasGuide(guides, request.getHospitalName(), request.getDepartmentName()),
                    hasLinkedParticipants(request)
            );

            if (request.getStatus() == AppointmentStatus.REQUESTED) {
                pendingRequests.add(overview);
            } else {
                managedRequests.add(overview);
            }
        }

        return new AdminDashboard(
                currentUser,
                availableManagers,
                busyManagers,
                managerDocumentOverviews,
                pendingRequests,
                managedRequests,
                requestActionOverviews,
                actionNotifications,
                auditLogs,
                actionDeliveries,
                actionOverview,
                supportInquiries,
                guides
        );
    }

    private AdminDashboard buildDashboard(
            User currentUser,
            QuerySnapshot requestSnapshot,
            QuerySnapshot userSnapshot,
            QuerySnapshot sessionSnapshot,
            QuerySnapshot guideSnapshot
    ) {
        List<AppointmentRequest> requests = toSortedRequests(requestSnapshot);

        Map<String, User> usersById = new HashMap<>();
        List<String> activeManagerIds = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null
                    && session.getStatus() != SessionStatus.COMPLETED
                    && session.getStatus() != SessionStatus.CANCELED) {
                activeManagerIds.add(session.getManagerUserId());
            }
        }

        List<User> availableManagers = new ArrayList<>();
        List<User> busyManagers = new ArrayList<>();
        List<ManagerDocumentOverview> managerDocumentOverviews = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : userSnapshot.getDocuments()) {
            User user = toUser(documentSnapshot);
            if (user == null) {
                continue;
            }
            usersById.put(user.getId(), user);
            if (user.getRole() != UserRole.MANAGER) {
                continue;
            }
            if (activeManagerIds.contains(user.getId())) {
                busyManagers.add(user);
            } else {
                availableManagers.add(user);
            }
            managerDocumentOverviews.add(new ManagerDocumentOverview(
                    user,
                    toManagerHomeProfile(documentSnapshot),
                    toManagerDocumentHistory(documentSnapshot)
            ));
        }

        Map<String, CompanionSession> sessionsByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null) {
                sessionsByRequestId.put(session.getAppointmentRequestId(), session);
            }
        }

        List<HospitalGuide> guides = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : guideSnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null) {
                guides.add(guide);
            }
        }

        List<AdminRequestOverview> pendingRequests = new ArrayList<>();
        List<AdminRequestOverview> managedRequests = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            AdminRequestOverview overview = new AdminRequestOverview(
                    request,
                    usersById.get(request.getPatientUserId()),
                    usersById.get(request.getGuardianUserId()),
                    usersById.get(request.getManagerUserId()),
                    sessionsByRequestId.get(request.getId()),
                    null,
                    hasGuide(guides, request.getHospitalName(), request.getDepartmentName()),
                    hasLinkedParticipants(request)
            );

            if (request.getStatus() == AppointmentStatus.REQUESTED) {
                pendingRequests.add(overview);
            } else {
                managedRequests.add(overview);
            }
        }

        return new AdminDashboard(
                currentUser,
                availableManagers,
                busyManagers,
                managerDocumentOverviews,
                pendingRequests,
                managedRequests,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                AdminActionContract.createOverview(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                new ArrayList<>(),
                guides
        );
    }

    private List<AdminRequestActionOverview> toAdminRequestActionOverviews(
            QuerySnapshot settlementSnapshot,
            QuerySnapshot emergencySnapshot,
            QuerySnapshot followUpSnapshot
    ) {
        Map<String, AdminSettlementRecord> settlementByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : settlementSnapshot.getDocuments()) {
            AdminSettlementRecord record = toAdminSettlementRecord(documentSnapshot);
            if (record != null) {
                settlementByRequestId.put(record.getRequestId(), record);
            }
        }

        Map<String, AdminEmergencyIssueRecord> emergencyByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : emergencySnapshot.getDocuments()) {
            AdminEmergencyIssueRecord record = toAdminEmergencyIssueRecord(documentSnapshot);
            if (record != null) {
                emergencyByRequestId.put(record.getRequestId(), record);
            }
        }

        Map<String, AppointmentFollowUpRecord> followUpByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : followUpSnapshot.getDocuments()) {
            AppointmentFollowUpRecord record = toAppointmentFollowUpRecord(documentSnapshot);
            if (record != null) {
                followUpByRequestId.put(record.getRequestId(), record);
            }
        }

        List<AdminRequestActionOverview> overviews = new ArrayList<>();
        for (String requestId : settlementByRequestId.keySet()) {
            overviews.add(new AdminRequestActionOverview(
                    requestId,
                    settlementByRequestId.get(requestId),
                    emergencyByRequestId.remove(requestId),
                    followUpByRequestId.remove(requestId)
            ));
        }
        for (String requestId : emergencyByRequestId.keySet()) {
            overviews.add(new AdminRequestActionOverview(
                    requestId,
                    null,
                    emergencyByRequestId.get(requestId),
                    followUpByRequestId.remove(requestId)
            ));
        }
        for (String requestId : followUpByRequestId.keySet()) {
            overviews.add(new AdminRequestActionOverview(
                    requestId,
                    null,
                    null,
                    followUpByRequestId.get(requestId)
            ));
        }
        return overviews;
    }

    private List<SupportInquiry> toSupportInquiries(QuerySnapshot supportSnapshot) {
        List<SupportInquiry> inquiries = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : supportSnapshot.getDocuments()) {
            SupportInquiry inquiry = toSupportInquiry(documentSnapshot);
            if (inquiry != null) {
                inquiries.add(inquiry);
            }
        }
        inquiries.sort((left, right) -> Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
        return inquiries;
    }

    private List<AdminActionNotification> toAdminActionNotifications(
            QuerySnapshot actionNotificationSnapshot
    ) {
        List<AdminActionNotification> notifications = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : actionNotificationSnapshot.getDocuments()) {
            AdminActionNotification notification = toAdminActionNotification(documentSnapshot);
            if (notification != null) {
                notifications.add(notification);
            }
        }
        return new ArrayList<>(AdminActionContract.sortNotifications(notifications));
    }

    private List<AdminActionDeliveryRecord> toAdminActionDeliveries(
            QuerySnapshot actionDeliverySnapshot
    ) {
        List<AdminActionDeliveryRecord> deliveries = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : actionDeliverySnapshot.getDocuments()) {
            AdminActionDeliveryRecord delivery = toAdminActionDeliveryRecord(documentSnapshot);
            if (delivery != null) {
                deliveries.add(delivery);
            }
        }
        return new ArrayList<>(AdminActionContract.sortDeliveries(deliveries));
    }

    private List<AdminAuditLogEntry> toAdminAuditLogs(QuerySnapshot auditLogSnapshot) {
        List<AdminAuditLogEntry> auditLogs = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : auditLogSnapshot.getDocuments()) {
            AdminAuditLogEntry auditLog = toAdminAuditLogEntry(documentSnapshot);
            if (auditLog != null) {
                auditLogs.add(auditLog);
            }
        }
        return new ArrayList<>(AdminActionContract.sortAuditLogs(auditLogs));
    }

    private List<AppointmentRequest> toSortedRequests(QuerySnapshot querySnapshot) {
        List<DocumentSnapshot> documents = new ArrayList<>(querySnapshot.getDocuments());
        documents.sort((left, right) -> {
            long rightCreatedAt = resolveCreatedAt(right);
            long leftCreatedAt = resolveCreatedAt(left);
            if (rightCreatedAt != leftCreatedAt) {
                return Long.compare(rightCreatedAt, leftCreatedAt);
            }
            return right.getId().compareTo(left.getId());
        });

        List<AppointmentRequest> requests = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : documents) {
            AppointmentRequest request = toAppointmentRequest(documentSnapshot);
            if (request != null) {
                requests.add(request);
            }
        }
        return requests;
    }

    private long resolveCreatedAt(DocumentSnapshot documentSnapshot) {
        Object rawCreatedAt = documentSnapshot.get("createdAt");
        if (rawCreatedAt instanceof Timestamp) {
            return ((Timestamp) rawCreatedAt).toDate().getTime();
        }
        if (rawCreatedAt instanceof Number) {
            return ((Number) rawCreatedAt).longValue();
        }
        return 0L;
    }

    @Nullable
    private AdminRequestOverview findRequestOverview(AdminDashboard dashboard, String requestId) {
        for (AdminRequestOverview overview : dashboard.getPendingRequests()) {
            if (overview.getAppointmentRequest().getId().equals(requestId)) {
                return overview;
            }
        }
        for (AdminRequestOverview overview : dashboard.getManagedRequests()) {
            if (overview.getAppointmentRequest().getId().equals(requestId)) {
                return overview;
            }
        }
        return null;
    }

    private boolean containsManager(List<User> managers, String managerUserId) {
        for (User manager : managers) {
            if (manager.getId().equals(managerUserId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGuide(List<HospitalGuide> guides, String hospitalName, String departmentName) {
        for (HospitalGuide guide : guides) {
            if (guide.getHospitalName().equals(hospitalName)
                    && guide.getDepartmentName().equals(departmentName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLinkedParticipants(AppointmentRequest request) {
        return !normalizeText(request.getPatientUserId()).isEmpty()
                && !normalizeText(request.getGuardianUserId()).isEmpty();
    }

    private List<Map<String, Object>> buildGuideStepDocuments(List<String> stepLines) {
        List<Map<String, Object>> steps = new ArrayList<>();
        int order = 1;
        for (String rawLine : stepLines) {
            String line = normalizeText(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = splitGuideLine(line, order);
            Map<String, Object> stepDocument = new HashMap<>();
            stepDocument.put("order", order);
            stepDocument.put("title", parts[0]);
            stepDocument.put("description", parts[1]);
            steps.add(stepDocument);
            order++;
        }
        return steps;
    }

    private String[] splitGuideLine(String line, int order) {
        int separatorIndex = findGuideSeparatorIndex(line);
        if (separatorIndex < 0) {
            return new String[]{"단계 " + order, line};
        }
        String title = normalizeText(line.substring(0, separatorIndex));
        String description = normalizeText(line.substring(separatorIndex + 1));
        if (title.isEmpty() || description.isEmpty()) {
            return new String[]{"단계 " + order, line};
        }
        return new String[]{title, description};
    }

    private int findGuideSeparatorIndex(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0) {
            return colonIndex;
        }
        int barIndex = line.indexOf('|');
        if (barIndex >= 0) {
            return barIndex;
        }
        return line.indexOf('-');
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String roleValue = documentSnapshot.getString("role");
        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        if (roleValue == null || name == null || email == null) {
            return null;
        }

        return new User(
                documentSnapshot.getId(),
                UserRole.valueOf(roleValue),
                name,
                email,
                phone == null ? "" : phone
        );
    }

    private ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot) {
        String documentSummary = documentSnapshot.getString("managerDocumentSummary");
        String availabilitySummary = documentSnapshot.getString("managerAvailabilitySummary");
        String documentStatus = documentSnapshot.getString("managerDocumentStatus");
        String documentReviewNote = documentSnapshot.getString("managerDocumentReviewNote");
        String documentReviewedByName = documentSnapshot.getString("managerDocumentReviewedByName");
        long documentUpdatedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentUpdatedAt"));
        long documentReviewedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentReviewedAt"));
        return new ManagerHomeProfile(
                normalizeText(documentSummary),
                normalizeText(availabilitySummary),
                resolveManagerDocumentStatus(documentStatus, documentSummary),
                normalizeText(documentReviewNote),
                documentUpdatedAtMillis,
                documentReviewedAtMillis,
                normalizeText(documentReviewedByName),
                toManagerDocumentFiles(documentSnapshot)
        );
    }

    private List<ManagerDocumentFileMetadata> toManagerDocumentFiles(DocumentSnapshot documentSnapshot) {
        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileByType = new HashMap<>();

        Object rawMetadataMap = documentSnapshot.get("managerDocumentFiles");
        if (rawMetadataMap instanceof Map) {
            Map<?, ?> metadataMap = (Map<?, ?>) rawMetadataMap;
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadata(
                        fileType,
                        metadataMap.get(fileType.getStorageKey())
                );
                if (metadata != null) {
                    fileByType.put(fileType, metadata);
                }
            }
        }

        Object rawPathMap = documentSnapshot.get("managerDocumentFilePaths");
        if (rawPathMap instanceof Map) {
            Map<?, ?> pathMap = (Map<?, ?>) rawPathMap;
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                if (fileByType.containsKey(fileType)) {
                    continue;
                }
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                        fileType,
                        stringValue(pathMap.get(fileType.getStorageKey()))
                );
                if (metadata != null) {
                    fileByType.put(fileType, metadata);
                }
            }
        }

        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            if (fileByType.containsKey(fileType)) {
                continue;
            }
            ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                    fileType,
                    documentSnapshot.getString(resolveLegacyDocumentStoragePathKey(fileType))
            );
            if (metadata != null) {
                fileByType.put(fileType, metadata);
            }
        }

        List<ManagerDocumentFileMetadata> documentFiles = new ArrayList<>();
        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            ManagerDocumentFileMetadata metadata = fileByType.get(fileType);
            if (metadata != null) {
                documentFiles.add(metadata);
            }
        }
        return documentFiles;
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            @Nullable Object rawValue
    ) {
        if (!(rawValue instanceof Map)) {
            return null;
        }

        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = normalizeText(stringValue(valueMap.get("fullPath")));
        if (fullPath.isEmpty()) {
            return null;
        }

        String fileName = normalizeText(stringValue(valueMap.get("fileName")));
        if (fileName.isEmpty()) {
            fileName = resolveFileNameFromPath(fullPath);
        }

        return new ManagerDocumentFileMetadata(
                fileType,
                fullPath,
                fileName,
                normalizeText(stringValue(valueMap.get("contentType"))),
                resolveTimestampMillis(valueMap.get("uploadedAt")),
                normalizeText(stringValue(valueMap.get("previewUri")))
        );
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadataFromPath(
            ManagerDocumentFileType fileType,
            @Nullable String fullPath
    ) {
        String normalizedPath = normalizeText(fullPath);
        if (normalizedPath.isEmpty()) {
            return null;
        }
        return new ManagerDocumentFileMetadata(
                fileType,
                normalizedPath,
                resolveFileNameFromPath(normalizedPath),
                "",
                0L
        );
    }

    private List<ManagerDocumentHistoryEntry> toManagerDocumentHistory(DocumentSnapshot documentSnapshot) {
        Object rawHistory = documentSnapshot.get("managerDocumentHistory");
        if (!(rawHistory instanceof List)) {
            return new ArrayList<>();
        }

        List<ManagerDocumentHistoryEntry> historyEntries = new ArrayList<>();
        for (Object rawEntry : (List<?>) rawHistory) {
            if (!(rawEntry instanceof Map)) {
                continue;
            }
            Map<?, ?> entryMap = (Map<?, ?>) rawEntry;
            String eventTypeValue = normalizeText(stringValue(entryMap.get("eventType")));
            ManagerDocumentHistoryEventType eventType = resolveHistoryEventType(eventTypeValue);
            long happenedAtMillis = resolveTimestampMillis(entryMap.get("happenedAt"));
            historyEntries.add(new ManagerDocumentHistoryEntry(
                    eventType,
                    happenedAtMillis,
                    normalizeText(stringValue(entryMap.get("actorName"))),
                    normalizeText(stringValue(entryMap.get("summary"))),
                    normalizeText(stringValue(entryMap.get("reviewNote")))
            ));
        }
        historyEntries.sort((left, right) -> Long.compare(right.getHappenedAtMillis(), left.getHappenedAtMillis()));
        return historyEntries;
    }

    private List<ManagerDocumentHistoryEntry> appendManagerDocumentHistory(
            List<ManagerDocumentHistoryEntry> historyEntries,
            ManagerDocumentHistoryEntry historyEntry
    ) {
        List<ManagerDocumentHistoryEntry> updatedEntries = new ArrayList<>(historyEntries);
        updatedEntries.add(0, historyEntry);
        return updatedEntries;
    }

    private List<Map<String, Object>> toManagerDocumentHistoryPayload(
            List<ManagerDocumentHistoryEntry> historyEntries
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ManagerDocumentHistoryEntry historyEntry : historyEntries) {
            Map<String, Object> item = new HashMap<>();
            item.put("eventType", historyEntry.getEventType().name());
            item.put("happenedAt", historyEntry.getHappenedAtMillis());
            item.put("actorName", historyEntry.getActorName());
            item.put("summary", historyEntry.getSummary());
            item.put("reviewNote", historyEntry.getReviewNote());
            payload.add(item);
        }
        return payload;
    }

    @Nullable
    private AppointmentFollowUpRecord toAppointmentFollowUpRecord(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String requestId = documentSnapshot.getString("requestId");
        if (requestId == null) {
            requestId = documentSnapshot.getId();
        }
        return new AppointmentFollowUpRecord(
                requestId,
                AppointmentFollowUpReviewRating.fromValue(documentSnapshot.getString("reviewRatingCode")),
                resolveTimestampMillis(documentSnapshot.get("reviewSavedAt")),
                AppointmentFollowUpSettlementStatus.fromValue(
                        documentSnapshot.getString("settlementFollowUpStatus")
                ),
                normalizeText(documentSnapshot.getString("settlementFollowUpNote")),
                resolveTimestampMillis(documentSnapshot.get("settlementFollowUpSavedAt")),
                AppointmentFollowUpSupportEscalationStatus.fromValue(
                        documentSnapshot.getString("supportEscalationStatus")
                ),
                resolveTimestampMillis(documentSnapshot.get("supportEscalatedAt"))
        );
    }

    @Nullable
    private AdminSettlementRecord toAdminSettlementRecord(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String requestId = documentSnapshot.getString("requestId");
        if (requestId == null) {
            requestId = documentSnapshot.getId();
        }
        return new AdminSettlementRecord(
                requestId,
                resolveAdminSettlementStatus(documentSnapshot.getString("status")),
                normalizeText(documentSnapshot.getString("note")),
                normalizeText(documentSnapshot.getString("handledByName")),
                resolveTimestampMillis(documentSnapshot.get("handledAt"))
        );
    }

    @Nullable
    private AdminEmergencyIssueRecord toAdminEmergencyIssueRecord(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String requestId = documentSnapshot.getString("requestId");
        if (requestId == null) {
            requestId = documentSnapshot.getId();
        }
        return new AdminEmergencyIssueRecord(
                requestId,
                resolveAdminEmergencyIssueStatus(documentSnapshot.getString("status")),
                normalizeText(documentSnapshot.getString("note")),
                normalizeText(documentSnapshot.getString("handledByName")),
                resolveTimestampMillis(documentSnapshot.get("handledAt"))
        );
    }

    @Nullable
    private SupportInquiry toSupportInquiry(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String managerUserId = documentSnapshot.getString("managerUserId");
        String managerName = documentSnapshot.getString("managerName");
        String title = documentSnapshot.getString("title");
        String body = documentSnapshot.getString("body");
        if (managerUserId == null || managerName == null || title == null || body == null) {
            return null;
        }
        return new SupportInquiry(
                documentSnapshot.getId(),
                managerUserId,
                normalizeText(managerName),
                SupportInquiryCategory.fromValue(documentSnapshot.getString("category")),
                normalizeText(title),
                normalizeText(body),
                resolveSupportInquiryStatus(documentSnapshot.getString("status")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                normalizeText(documentSnapshot.getString("responseText")),
                resolveTimestampMillis(documentSnapshot.get("respondedAt")),
                normalizeText(documentSnapshot.getString("respondedByName"))
        );
    }

    @Nullable
    private AdminActionNotification toAdminActionNotification(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String stateValue = documentSnapshot.getString("state");
        AdminActionNotificationState state = stateValue == null
                ? null
                : AdminActionNotificationState.fromValue(stateValue);
        String priorityValue = documentSnapshot.getString("priority");
        AdminActionNotificationPriority priority = priorityValue == null
                ? null
                : AdminActionNotificationPriority.fromValue(priorityValue);
        return new AdminActionNotification(
                documentSnapshot.getId(),
                AdminActionSourceType.fromValue(documentSnapshot.getString("sourceType")),
                AdminActionNotificationLevel.fromValue(documentSnapshot.getString("level")),
                normalizeText(documentSnapshot.getString("requestId")),
                normalizeText(documentSnapshot.getString("inquiryId")),
                normalizeText(documentSnapshot.getString("title")),
                normalizeText(documentSnapshot.getString("body")),
                normalizeText(documentSnapshot.getString("actorName")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("isRead")),
                resolveTimestampMillis(documentSnapshot.get("readAt")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("isResolved")),
                resolveTimestampMillis(documentSnapshot.get("resolvedAt")),
                normalizeText(documentSnapshot.getString("resolvedByName")),
                state,
                priority,
                toAdminActionNotificationFilterKeys(documentSnapshot.get("filterKeys"))
        );
    }

    @Nullable
    private AdminAuditLogEntry toAdminAuditLogEntry(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        return new AdminAuditLogEntry(
                documentSnapshot.getId(),
                AdminActionSourceType.fromValue(documentSnapshot.getString("sourceType")),
                normalizeText(documentSnapshot.getString("requestId")),
                normalizeText(documentSnapshot.getString("inquiryId")),
                normalizeText(documentSnapshot.getString("actionSummary")),
                normalizeText(documentSnapshot.getString("note")),
                normalizeText(documentSnapshot.getString("actorName")),
                resolveTimestampMillis(documentSnapshot.get("createdAt"))
        );
    }

    @Nullable
    private AdminActionDeliveryRecord toAdminActionDeliveryRecord(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        return new AdminActionDeliveryRecord(
                documentSnapshot.getId(),
                normalizeText(documentSnapshot.getString("notificationId")),
                AdminActionSourceType.fromValue(documentSnapshot.getString("sourceType")),
                AdminActionDeliveryTrigger.fromValue(documentSnapshot.getString("trigger")),
                AdminActionDeliveryChannel.fromValue(documentSnapshot.getString("channel")),
                AdminActionDeliveryStatus.fromValue(documentSnapshot.getString("status")),
                normalizeText(documentSnapshot.getString("requestId")),
                normalizeText(documentSnapshot.getString("inquiryId")),
                normalizeText(documentSnapshot.getString("title")),
                normalizeText(documentSnapshot.getString("body")),
                normalizeText(documentSnapshot.getString("targetLabel")),
                normalizeText(documentSnapshot.getString("note")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                resolveTimestampMillis(documentSnapshot.get("processedAt")),
                Math.max(numberOrZero(documentSnapshot.get("attemptCount")), 1),
                Math.max(numberOrZero(documentSnapshot.get("maxAttemptCount")), 1),
                resolveTimestampMillis(documentSnapshot.get("confirmedAt")),
                resolveTimestampMillis(documentSnapshot.get("nextRetryAt")),
                resolveTimestampMillis(documentSnapshot.get("slaDueAt")),
                null,
                null,
                null,
                null
        );
    }

    @Nullable
    private AppointmentRequest toAppointmentRequest(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }

        String patientUserId = documentSnapshot.getString("patientUserId");
        String guardianUserId = documentSnapshot.getString("guardianUserId");
        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        String appointmentAt = stringifyDate(documentSnapshot.get("appointmentAt"));
        String meetingPlace = documentSnapshot.getString("meetingPlace");
        String specialNotes = documentSnapshot.getString("specialNotes");
        String statusValue = documentSnapshot.getString("status");
        String managerUserId = documentSnapshot.getString("managerUserId");
        if (patientUserId == null
                || guardianUserId == null
                || hospitalName == null
                || departmentName == null
                || appointmentAt == null
                || statusValue == null) {
            return null;
        }

        return new AppointmentRequest(
                documentSnapshot.getId(),
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                appointmentAt,
                meetingPlace == null ? "" : meetingPlace,
                specialNotes == null ? "" : specialNotes,
                AppointmentStatus.valueOf(statusValue),
                managerUserId,
                normalizeText(documentSnapshot.getString("patientName")),
                normalizeText(documentSnapshot.getString("patientPhone")),
                normalizeText(documentSnapshot.getString("patientEmail")),
                normalizeText(documentSnapshot.getString("guardianName")),
                normalizeText(documentSnapshot.getString("guardianPhone")),
                normalizeText(documentSnapshot.getString("guardianEmail")),
                normalizeText(documentSnapshot.getString("patientConditionSummary")),
                normalizeText(documentSnapshot.getString("medicationSummary")),
                normalizeText(documentSnapshot.getString("mobilitySupportCode")),
                normalizeText(documentSnapshot.getString("tripTypeCode")),
                normalizeText(documentSnapshot.getString("managerGenderPreferenceCode")),
                normalizeText(documentSnapshot.getString("paymentMethodCode")),
                normalizeText(documentSnapshot.getString("couponCode")),
                numberOrZero(documentSnapshot.get("basePrice")),
                numberOrZero(documentSnapshot.get("optionSurchargePrice")),
                numberOrZero(documentSnapshot.get("couponDiscountPrice")),
                numberOrZero(documentSnapshot.get("finalPrice")),
                normalizeText(documentSnapshot.getString("paymentStatusCode")),
                normalizeText(documentSnapshot.getString("paymentApprovalCode")),
                normalizeText(documentSnapshot.getString("paymentApprovedAt")),
                normalizeText(documentSnapshot.getString("paymentProviderLabel"))
        );
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String appointmentRequestId = documentSnapshot.getString("appointmentRequestId");
        String managerUserId = documentSnapshot.getString("managerUserId");
        Long currentStepOrder = documentSnapshot.getLong("currentStepOrder");
        String statusValue = documentSnapshot.getString("currentStatus");
        if (appointmentRequestId == null || managerUserId == null || currentStepOrder == null || statusValue == null) {
            return null;
        }

        return new CompanionSession(
                documentSnapshot.getId(),
                appointmentRequestId,
                managerUserId,
                currentStepOrder.intValue(),
                SessionStatus.valueOf(statusValue),
                documentSnapshot.getString("guardianUpdate") == null ? "" : documentSnapshot.getString("guardianUpdate"),
                documentSnapshot.getString("locationSummary") == null ? "" : documentSnapshot.getString("locationSummary"),
                documentSnapshot.getString("fieldPhotoNote") == null ? "" : documentSnapshot.getString("fieldPhotoNote"),
                documentSnapshot.getString("medicationNote") == null ? "" : documentSnapshot.getString("medicationNote"),
                documentSnapshot.getString("pharmacySummary") == null ? "" : documentSnapshot.getString("pharmacySummary"),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("pharmacyCompleted")),
                doubleOrNull(documentSnapshot.get("sharedLatitude")),
                doubleOrNull(documentSnapshot.get("sharedLongitude")),
                resolveTimestampMillis(documentSnapshot.get("sharedLocationUpdatedAt"))
        );
    }

    @Nullable
    private HospitalGuide toGuide(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        Object stepsValue = documentSnapshot.get("steps");
        if (hospitalName == null || departmentName == null || !(stepsValue instanceof List)) {
            return null;
        }

        List<GuideStep> steps = new ArrayList<>();
        for (Object rawStep : (List<?>) stepsValue) {
            if (!(rawStep instanceof Map)) {
                continue;
            }
            Map<?, ?> stepMap = (Map<?, ?>) rawStep;
            Object orderValue = stepMap.get("order");
            Object titleValue = stepMap.get("title");
            Object descriptionValue = stepMap.get("description");
            if (!(orderValue instanceof Number) || titleValue == null || descriptionValue == null) {
                continue;
            }
            steps.add(new GuideStep(
                    ((Number) orderValue).intValue(),
                    String.valueOf(titleValue),
                    String.valueOf(descriptionValue)
            ));
        }
        if (steps.isEmpty()) {
            return null;
        }
        return new HospitalGuide(documentSnapshot.getId(), hospitalName, departmentName, steps);
    }

    @Nullable
    private SessionReport toReport(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String sessionId = documentSnapshot.getString("sessionId");
        String summary = documentSnapshot.getString("summary");
        String treatmentNotes = documentSnapshot.getString("treatmentNotes");
        String medicationNotes = documentSnapshot.getString("medicationNotes");
        String nextVisitAt = stringifyDate(documentSnapshot.get("nextVisitAt"));
        if (sessionId == null || summary == null) {
            return null;
        }

        return new SessionReport(
                documentSnapshot.getId(),
                sessionId,
                summary,
                treatmentNotes == null ? "" : treatmentNotes,
                medicationNotes == null ? "" : medicationNotes,
                nextVisitAt == null ? "" : nextVisitAt
        );
    }

    @Nullable
    private String stringifyDate(@Nullable Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().toString();
        }
        return String.valueOf(rawValue);
    }

    private void appendAdminActionArtifacts(
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
    ) {
        DocumentReference notificationReference =
                firestore.collection("adminActionNotifications").document();
        Map<String, Object> notificationDocument = new HashMap<>();
        notificationDocument.put("sourceType", sourceType.getValue());
        notificationDocument.put("level", level.getValue());
        notificationDocument.put("requestId", normalizeText(requestId));
        notificationDocument.put("inquiryId", normalizeText(inquiryId));
        notificationDocument.put("title", normalizeText(title));
        notificationDocument.put("body", normalizeText(body));
        notificationDocument.put("actorName", normalizeText(actorName));
        notificationDocument.put("isRead", false);
        notificationDocument.put("isResolved", false);
        notificationDocument.put("resolvedByName", "");
        notificationDocument.putAll(buildNotificationContractFields(
                sourceType,
                level,
                false,
                false
        ));
        notificationDocument.put("createdAt", FieldValue.serverTimestamp());
        notificationDocument.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(notificationReference, notificationDocument);
        appendActionDeliveryArtifacts(
                batch,
                notificationReference.getId(),
                sourceType,
                normalizeText(requestId),
                normalizeText(inquiryId),
                normalizeText(title),
                normalizeText(body),
                AdminActionDeliveryTrigger.NOTIFICATION_CREATED,
                AdminActionDeliveryStatus.SENT,
                AdminActionDeliveryStatus.CONFIRMED,
                buildQueuedPushDeliveryNote(level),
                "운영 피드 타임라인 반영을 완료했습니다."
        );

        DocumentReference auditReference = firestore.collection("adminAuditLogs").document();
        Map<String, Object> auditDocument = new HashMap<>();
        auditDocument.put("sourceType", sourceType.getValue());
        auditDocument.put("requestId", normalizeText(requestId));
        auditDocument.put("inquiryId", normalizeText(inquiryId));
        auditDocument.put("actionSummary", normalizeText(actionSummary));
        auditDocument.put("note", normalizeText(note));
        auditDocument.put("actorName", normalizeText(actorName));
        auditDocument.put("createdAt", FieldValue.serverTimestamp());
        batch.set(auditReference, auditDocument);
    }

    private void appendActionResolutionAudit(
            WriteBatch batch,
            AdminActionNotification notification,
            boolean resolved,
            String actorName
    ) {
        DocumentReference auditReference = firestore.collection("adminAuditLogs").document();
        Map<String, Object> auditDocument = new HashMap<>();
        auditDocument.put("sourceType", notification.getSourceType().getValue());
        auditDocument.put("requestId", normalizeText(notification.getRequestId()));
        auditDocument.put("inquiryId", normalizeText(notification.getInquiryId()));
        auditDocument.put(
                "actionSummary",
                resolved ? "후속 알림 해결 처리" : "후속 알림 다시 열기"
        );
        auditDocument.put(
                "note",
                resolved
                        ? "관리자가 후속 알림을 해결 완료 상태로 정리했습니다."
                        : "관리자가 후속 알림을 다시 확인 대상으로 되돌렸습니다."
        );
        auditDocument.put("actorName", normalizeText(actorName));
        auditDocument.put("createdAt", FieldValue.serverTimestamp());
        batch.set(auditReference, auditDocument);
    }

    private void appendActionDeliveryArtifacts(
            WriteBatch batch,
            AdminActionNotification notification,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryStatus pushStatus,
            AdminActionDeliveryStatus feedStatus,
            String pushNote,
            String feedNote
    ) {
        appendActionDeliveryArtifacts(
                batch,
                notification.getId(),
                notification.getSourceType(),
                normalizeText(notification.getRequestId()),
                normalizeText(notification.getInquiryId()),
                normalizeText(notification.getTitle()),
                normalizeText(notification.getBody()),
                trigger,
                pushStatus,
                feedStatus,
                pushNote,
                feedNote
        );
    }

    private void appendActionDeliveryArtifacts(
            WriteBatch batch,
            String notificationId,
            AdminActionSourceType sourceType,
            String requestId,
            String inquiryId,
            String title,
            String body,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryStatus pushStatus,
            AdminActionDeliveryStatus feedStatus,
            String pushNote,
            String feedNote
    ) {
        appendActionDeliveryArtifact(
                batch,
                notificationId,
                sourceType,
                requestId,
                inquiryId,
                title,
                body,
                trigger,
                AdminActionDeliveryChannel.APP_PUSH,
                pushStatus,
                "관리자 앱 푸시",
                pushNote
        );
        appendActionDeliveryArtifact(
                batch,
                notificationId,
                sourceType,
                requestId,
                inquiryId,
                title,
                body,
                trigger,
                AdminActionDeliveryChannel.OPERATIONS_FEED,
                feedStatus,
                "운영 피드",
                feedNote
        );
    }

    private void appendActionDeliveryArtifact(
            WriteBatch batch,
            String notificationId,
            AdminActionSourceType sourceType,
            String requestId,
            String inquiryId,
            String title,
            String body,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryChannel channel,
            AdminActionDeliveryStatus status,
            String targetLabel,
            String note
    ) {
        DocumentReference deliveryReference = firestore.collection("adminActionDeliveries").document();
        boolean queueManagedPush = channel == AdminActionDeliveryChannel.APP_PUSH
                && status == AdminActionDeliveryStatus.SENT;
        long now = System.currentTimeMillis();
        AdminActionDeliveryRecord deliveryRecord = new AdminActionDeliveryRecord(
                deliveryReference.getId(),
                normalizeText(notificationId),
                sourceType,
                trigger,
                channel,
                status,
                normalizeText(requestId),
                normalizeText(inquiryId),
                normalizeText(title),
                normalizeText(body),
                normalizeText(targetLabel),
                normalizeText(note),
                now,
                queueManagedPush ? 0L : now
        );
        Map<String, Object> deliveryDocument = new HashMap<>();
        deliveryDocument.put("notificationId", deliveryRecord.getNotificationId());
        deliveryDocument.put("sourceType", deliveryRecord.getSourceType().getValue());
        deliveryDocument.put("trigger", trigger.getValue());
        deliveryDocument.put("channel", channel.getValue());
        deliveryDocument.put("status", deliveryRecord.getStatus().getValue());
        deliveryDocument.put("requestId", deliveryRecord.getRequestId());
        deliveryDocument.put("inquiryId", deliveryRecord.getInquiryId());
        deliveryDocument.put("title", deliveryRecord.getTitle());
        deliveryDocument.put("body", deliveryRecord.getBody());
        deliveryDocument.put("targetLabel", deliveryRecord.getTargetLabel());
        deliveryDocument.put("note", deliveryRecord.getNote());
        deliveryDocument.putAll(buildDeliveryContractFields(deliveryRecord));
        deliveryDocument.put("createdAt", FieldValue.serverTimestamp());
        if (!queueManagedPush) {
            deliveryDocument.put("processedAt", FieldValue.serverTimestamp());
        }
        batch.set(deliveryReference, deliveryDocument);
        if (queueManagedPush) {
            appendActionDeliveryJobArtifact(batch, deliveryRecord);
        }
    }

    private void appendActionDeliveryJobArtifact(
            WriteBatch batch,
            AdminActionDeliveryRecord deliveryRecord
    ) {
        DocumentReference jobReference = firestore.collection("adminActionDeliveryJobs").document();
        Map<String, Object> jobDocument = new HashMap<>();
        jobDocument.put("notificationId", deliveryRecord.getNotificationId());
        jobDocument.put("deliveryId", deliveryRecord.getId());
        jobDocument.put("sourceType", deliveryRecord.getSourceType().getValue());
        jobDocument.put("trigger", deliveryRecord.getTrigger().getValue());
        jobDocument.put("channel", deliveryRecord.getChannel().getValue());
        jobDocument.put("requestId", deliveryRecord.getRequestId());
        jobDocument.put("inquiryId", deliveryRecord.getInquiryId());
        jobDocument.put("title", deliveryRecord.getTitle());
        jobDocument.put("body", deliveryRecord.getBody());
        jobDocument.put("targetLabel", deliveryRecord.getTargetLabel());
        jobDocument.put("messagePreview", buildActionDeliveryMessagePreview(deliveryRecord));
        jobDocument.put("recipientRole", "ADMIN");
        jobDocument.put("recipientUserIds", Collections.emptyList());
        jobDocument.put("state", "PENDING");
        jobDocument.put("deliveryAttempts", 0);
        jobDocument.put("maxAttempts", deliveryRecord.getMaxAttemptCount());
        jobDocument.put("lastDeliverySource", "");
        jobDocument.put("lastError", "");
        if (deliveryRecord.getSlaDueAtMillis() > 0L) {
            jobDocument.put("slaDueAt", deliveryRecord.getSlaDueAtMillis());
        }
        jobDocument.put("queuedAt", FieldValue.serverTimestamp());
        jobDocument.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(jobReference, jobDocument);
    }

    private String buildQueuedPushDeliveryNote(AdminActionNotificationLevel level) {
        if (level == AdminActionNotificationLevel.WARNING) {
            return "관리자 앱 푸시 채널 우선 확인 알림을 발송 대기열에 등록했습니다.";
        }
        return "관리자 앱 푸시 채널 운영 확인 알림을 발송 대기열에 등록했습니다.";
    }

    private String buildActionDeliveryMessagePreview(AdminActionDeliveryRecord deliveryRecord) {
        if (!normalizeText(deliveryRecord.getBody()).isEmpty()) {
            return normalizeText(deliveryRecord.getTitle()) + " - "
                    + normalizeText(deliveryRecord.getBody());
        }
        return normalizeText(deliveryRecord.getTitle());
    }

    private String buildSettlementNotificationTitle(AdminSettlementStatus status) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return "정산 재확인 요청 저장";
        }
        return "정산 후속 확인 저장";
    }

    private String buildSettlementNotificationBody(
            String requestId,
            AdminSettlementStatus status
    ) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return "요청 " + normalizeText(requestId) + "를 정산 재확인 대상으로 표시했습니다.";
        }
        return "요청 " + normalizeText(requestId) + "의 정산 확인 결과를 저장했습니다.";
    }

    private String buildSettlementAuditSummary(AdminSettlementStatus status) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return "정산 후속 상태를 재확인으로 저장";
        }
        return "정산 후속 상태를 확인 완료로 저장";
    }

    private String buildEmergencyNotificationTitle(AdminEmergencyIssueStatus status) {
        if (status == AdminEmergencyIssueStatus.RESOLVED) {
            return "긴급 이슈 해결 기록 저장";
        }
        return "긴급 대응 기록 저장";
    }

    private String buildEmergencyNotificationBody(
            String requestId,
            AdminEmergencyIssueStatus status
    ) {
        if (status == AdminEmergencyIssueStatus.RESOLVED) {
            return "요청 " + normalizeText(requestId) + "의 긴급 대응을 해결 완료로 저장했습니다.";
        }
        return "요청 " + normalizeText(requestId) + "에 긴급 대응 기록을 남겼습니다.";
    }

    private String buildEmergencyAuditSummary(AdminEmergencyIssueStatus status) {
        if (status == AdminEmergencyIssueStatus.RESOLVED) {
            return "긴급 이슈를 해결 상태로 저장";
        }
        return "긴급 이슈를 보고 상태로 저장";
    }

    private String buildSupportNotificationBody(String inquiryId) {
        return "문의 " + normalizeText(inquiryId) + "에 관리자 응답을 남겼습니다.";
    }

    private Map<String, Object> buildNotificationContractFields(
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            boolean read,
            boolean resolved
    ) {
        Map<String, Object> fields = new HashMap<>();
        AdminActionNotificationState state = AdminActionNotificationContract.resolveState(read, resolved);
        fields.put("state", state.getValue());
        fields.put(
                "priority",
                AdminActionNotificationContract.resolvePriority(sourceType, level, state).getValue()
        );
        fields.put(
                "filterKeys",
                toFilterKeyValues(AdminActionNotificationContract.resolveFilterKeys(state))
        );
        return fields;
    }

    private Map<String, Object> buildDeliveryContractFields(
            AdminActionDeliveryRecord deliveryRecord
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("state", deliveryRecord.getState().getValue());
        fields.put("priority", deliveryRecord.getPriority().getValue());
        fields.put("filterKeys", toDeliveryFilterKeyValues(deliveryRecord.getFilterKeys()));
        fields.put("slaStatus", deliveryRecord.getSlaStatus().getValue());
        fields.put("attemptCount", deliveryRecord.getAttemptCount());
        fields.put("maxAttemptCount", deliveryRecord.getMaxAttemptCount());
        if (deliveryRecord.getConfirmedAtMillis() > 0L) {
            fields.put("confirmedAt", deliveryRecord.getConfirmedAtMillis());
        }
        if (deliveryRecord.getNextRetryAtMillis() > 0L) {
            fields.put("nextRetryAt", deliveryRecord.getNextRetryAtMillis());
        }
        if (deliveryRecord.getSlaDueAtMillis() > 0L) {
            fields.put("slaDueAt", deliveryRecord.getSlaDueAtMillis());
        }
        return fields;
    }

    private List<String> toFilterKeyValues(
            List<AdminActionNotificationFilterKey> filterKeys
    ) {
        if (filterKeys == null || filterKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (AdminActionNotificationFilterKey filterKey : filterKeys) {
            if (filterKey != null) {
                values.add(filterKey.getValue());
            }
        }
        return values;
    }

    private List<String> toDeliveryFilterKeyValues(
            List<AdminActionDeliveryFilterKey> filterKeys
    ) {
        if (filterKeys == null || filterKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (AdminActionDeliveryFilterKey filterKey : filterKeys) {
            if (filterKey != null) {
                values.add(filterKey.getValue());
            }
        }
        return values;
    }

    private List<AdminActionNotificationFilterKey> toAdminActionNotificationFilterKeys(
            @Nullable Object rawValue
    ) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }
        List<AdminActionNotificationFilterKey> filterKeys = new ArrayList<>();
        for (Object value : (List<?>) rawValue) {
            if (value != null) {
                filterKeys.add(AdminActionNotificationFilterKey.fromValue(String.valueOf(value)));
            }
        }
        return filterKeys;
    }

    private List<AdminActionDeliveryFilterKey> toAdminActionDeliveryFilterKeys(
            @Nullable Object rawValue
    ) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }
        List<AdminActionDeliveryFilterKey> filterKeys = new ArrayList<>();
        for (Object value : (List<?>) rawValue) {
            if (value != null) {
                filterKeys.add(AdminActionDeliveryFilterKey.fromValue(String.valueOf(value)));
            }
        }
        return filterKeys;
    }

    private String normalizeText(@Nullable String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }

    private String resolveLegacyDocumentStoragePathKey(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return "managerIdCardStoragePath";
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return "managerLicenseStoragePath";
        }
        return "managerCriminalRecordStoragePath";
    }

    private String resolveFileNameFromPath(String fullPath) {
        int separatorIndex = fullPath.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex >= fullPath.length() - 1) {
            return fullPath;
        }
        return fullPath.substring(separatorIndex + 1);
    }

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @Nullable
    private Double doubleOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    @Nullable
    private String stringValue(@Nullable Object rawValue) {
        return rawValue instanceof String ? (String) rawValue : null;
    }

    private long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private AdminSettlementStatus resolveAdminSettlementStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return AdminSettlementStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 상태 값은 기본 대기 상태로 보정한다.
            }
        }
        return AdminSettlementStatus.PENDING;
    }

    private AdminEmergencyIssueStatus resolveAdminEmergencyIssueStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return AdminEmergencyIssueStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 상태 값은 기본 접수 상태로 보정한다.
            }
        }
        return AdminEmergencyIssueStatus.REPORTED;
    }

    private SupportInquiryStatus resolveSupportInquiryStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return SupportInquiryStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 상태 값은 접수 상태로 보정한다.
            }
        }
        return SupportInquiryStatus.RECEIVED;
    }

    private ManagerDocumentHistoryEventType resolveHistoryEventType(String rawValue) {
        if (rawValue.isEmpty()) {
            return ManagerDocumentHistoryEventType.SUBMITTED;
        }
        try {
            return ManagerDocumentHistoryEventType.valueOf(rawValue);
        } catch (IllegalArgumentException exception) {
            return ManagerDocumentHistoryEventType.SUBMITTED;
        }
    }

    private ManagerDocumentStatus resolveManagerDocumentStatus(
            @Nullable String rawStatus,
            @Nullable String documentSummary
    ) {
        if (rawStatus != null) {
            try {
                return ManagerDocumentStatus.valueOf(rawStatus);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 기본 규칙으로 보정한다.
            }
        }
        if (normalizeText(documentSummary).isEmpty()) {
            return ManagerDocumentStatus.NOT_SUBMITTED;
        }
        return ManagerDocumentStatus.PENDING_REVIEW;
    }
}
