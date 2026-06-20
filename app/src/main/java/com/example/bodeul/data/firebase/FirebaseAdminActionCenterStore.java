package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AdminActionDeliveryStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryTrigger;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 액션 센터의 읽음/해결 저장 규칙을 한 곳에서 관리한다.
 */
final class FirebaseAdminActionCenterStore {
    interface NotificationMapper {
        @Nullable
        AdminActionNotification toAdminActionNotification(DocumentSnapshot documentSnapshot);

        Map<String, Object> buildNotificationContractFields(
                AdminActionSourceType sourceType,
                AdminActionNotificationLevel level,
                boolean read,
                boolean resolved
        );

        void appendActionDeliveryArtifacts(
                WriteBatch batch,
                AdminActionNotification notification,
                AdminActionDeliveryTrigger trigger,
                AdminActionDeliveryStatus pushStatus,
                AdminActionDeliveryStatus feedStatus,
                String pushNote,
                String feedNote
        );

        void appendActionResolutionAudit(
                WriteBatch batch,
                AdminActionNotification notification,
                boolean resolved,
                String actorName
        );
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final NotificationMapper mapper;

    FirebaseAdminActionCenterStore(
            FirebaseFirestore firestore,
            NotificationMapper mapper
    ) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    void markActionNotificationRead(
            User currentUser,
            String notificationId,
            CompletionListener listener
    ) {
        DocumentReference notificationReference = firestore.collection("adminActionNotifications")
                .document(notificationId);
        notificationReference.get()
                .addOnSuccessListener(documentSnapshot -> {
                    AdminActionNotification notification = mapper.toAdminActionNotification(documentSnapshot);
                    if (notification == null) {
                        listener.onError("읽음 처리할 알림을 찾지 못했습니다.");
                        return;
                    }
                    if (notification.isRead()) {
                        listener.onSuccess();
                        return;
                    }

                    WriteBatch batch = firestore.batch();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isRead", true);
                    updates.put("readAt", FieldValue.serverTimestamp());
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    updates.putAll(mapper.buildNotificationContractFields(
                            notification.getSourceType(),
                            notification.getLevel(),
                            true,
                            notification.isResolved()
                    ));
                    batch.update(notificationReference, updates);
                    mapper.appendActionDeliveryArtifacts(
                            batch,
                            notification,
                            AdminActionDeliveryTrigger.NOTIFICATION_READ,
                            AdminActionDeliveryStatus.CONFIRMED,
                            AdminActionDeliveryStatus.CONFIRMED,
                            "관리자 앱에서 읽음 확인을 완료했습니다.",
                            "운영 피드에 읽음 상태를 반영했습니다."
                    );
                    batch.commit()
                            .addOnSuccessListener(unused -> listener.onSuccess())
                            .addOnFailureListener(exception ->
                                    listener.onError("읽음 처리 알림을 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        listener.onError("알림 정보를 불러오지 못했습니다."));
    }

    void updateActionNotificationResolved(
            User currentUser,
            String notificationId,
            boolean resolved,
            CompletionListener listener
    ) {
        DocumentReference notificationReference = firestore.collection("adminActionNotifications")
                .document(notificationId);
        notificationReference.get()
                .addOnSuccessListener(documentSnapshot -> {
                    AdminActionNotification notification = mapper.toAdminActionNotification(documentSnapshot);
                    if (notification == null) {
                        listener.onError("상태를 변경할 알림을 찾지 못했습니다.");
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
                    updates.putAll(mapper.buildNotificationContractFields(
                            notification.getSourceType(),
                            notification.getLevel(),
                            true,
                            resolved
                    ));
                    batch.update(notificationReference, updates);
                    mapper.appendActionResolutionAudit(
                            batch,
                            notification,
                            resolved,
                            normalizeText(currentUser.getName())
                    );
                    mapper.appendActionDeliveryArtifacts(
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
                                    ? "관리자 처리 결과에 대한 추가 푸시는 보내지 않습니다."
                                    : "재오픈 알림을 관리자 앱 푸시 대기열에 다시 등록했습니다.",
                            resolved
                                    ? "운영 피드에 해결 완료 상태를 반영했습니다."
                                    : "운영 피드에 재오픈 상태를 반영했습니다."
                    );
                    batch.commit()
                            .addOnSuccessListener(unused -> listener.onSuccess())
                            .addOnFailureListener(exception ->
                                    listener.onError("알림 상태를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        listener.onError("알림 정보를 불러오지 못했습니다."));
    }

    private static String normalizeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
