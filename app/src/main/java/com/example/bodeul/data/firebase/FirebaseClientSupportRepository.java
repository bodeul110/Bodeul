package com.example.bodeul.data.firebase;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 문의 접수와 최근 문의 이력을 Firebase에 연결한다.
 */
public final class FirebaseClientSupportRepository implements ClientSupportRepository {
    private final FirebaseFirestore firestore;

    public FirebaseClientSupportRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getClientSupportRequests(
            User currentUser,
            RepositoryCallback<List<ClientSupportRequest>> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("환자 또는 보호자 계정으로 다시 확인해 주세요.");
            return;
        }
        loadRequests(currentUser.getId(), callback);
    }

    @Override
    public void submitClientSupportRequest(
            User currentUser,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body,
            RepositoryCallback<List<ClientSupportRequest>> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("환자 또는 보호자 계정만 문의를 접수할 수 있습니다.");
            return;
        }

        Map<String, Object> document = new HashMap<>();
        document.put("userId", currentUser.getId());
        document.put("userName", normalizeText(currentUser.getName()));
        document.put("userRole", currentUser.getRole().name());
        document.put("appointmentRequestId", normalizeText(appointmentRequestId));
        document.put(
                "category",
                category == null ? ClientSupportCategory.RESERVATION.getValue() : category.getValue()
        );
        document.put("title", normalizeText(title));
        document.put("body", normalizeText(body));
        document.put("status", ClientSupportStatus.RECEIVED.name());
        document.put("responseText", "");
        document.put("respondedByName", "");
        document.put("respondedAt", 0L);
        document.put("responseReadByUser", false);
        document.put("responseReadAt", 0L);
        document.put("responseReminderCount", 0);
        document.put("responseReminderSentAt", 0L);
        document.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("clientSupportRequests")
                .add(document)
                .addOnSuccessListener(unused -> getClientSupportRequests(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("문의 내용을 저장하지 못했습니다."));
    }

    @Override
    public void markClientSupportResponsesRead(
            User currentUser,
            RepositoryCallback<Void> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("환자 또는 보호자 계정으로 다시 확인해 주세요.");
            return;
        }

        firestore.collection("clientSupportRequests")
                .whereEqualTo("userId", currentUser.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DocumentSnapshot> unreadAnsweredDocuments = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        ClientSupportRequest request = toClientSupportRequest(documentSnapshot);
                        if (request != null && request.hasUnreadResponse()) {
                            unreadAnsweredDocuments.add(documentSnapshot);
                        }
                    }

                    if (unreadAnsweredDocuments.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }

                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot documentSnapshot : unreadAnsweredDocuments) {
                        batch.update(documentSnapshot.getReference(), "responseReadByUser", true);
                        batch.update(documentSnapshot.getReference(), "responseReadAt", FieldValue.serverTimestamp());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(exception ->
                                    callback.onError("문의 답변 확인 상태를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("문의 답변 상태를 확인하지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    @Nullable
    private ClientSupportRequest toClientSupportRequest(DocumentSnapshot documentSnapshot) {
        String userId = normalizeText(documentSnapshot.getString("userId"));
        if (userId.isEmpty()) {
            return null;
        }
        return new ClientSupportRequest(
                documentSnapshot.getId(),
                userId,
                normalizeText(documentSnapshot.getString("userName")),
                resolveUserRole(documentSnapshot.getString("userRole")),
                normalizeText(documentSnapshot.getString("appointmentRequestId")),
                ClientSupportCategory.fromValue(documentSnapshot.getString("category")),
                normalizeText(documentSnapshot.getString("title")),
                normalizeText(documentSnapshot.getString("body")),
                resolveStatus(documentSnapshot.getString("status")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                normalizeText(documentSnapshot.getString("responseText")),
                resolveTimestampMillis(documentSnapshot.get("respondedAt")),
                normalizeText(documentSnapshot.getString("respondedByName")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("responseReadByUser")),
                resolveTimestampMillis(documentSnapshot.get("responseReadAt")),
                toInt(documentSnapshot.getLong("responseReminderCount")),
                resolveTimestampMillis(documentSnapshot.get("responseReminderSentAt"))
        );
    }

    private void loadRequests(
            String userId,
            RepositoryCallback<List<ClientSupportRequest>> callback
    ) {
        firestore.collection("clientSupportRequests")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ClientSupportRequest> requests = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        ClientSupportRequest request = toClientSupportRequest(documentSnapshot);
                        if (request != null) {
                            requests.add(request);
                        }
                    }
                    requests.sort((left, right) ->
                            Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
                    callback.onSuccess(requests);
                })
                .addOnFailureListener(exception ->
                        callback.onError("문의 이력을 불러오지 못했습니다."));
    }

    private boolean supportsRole(User currentUser) {
        return currentUser != null
                && (currentUser.getRole() == UserRole.PATIENT
                || currentUser.getRole() == UserRole.GUARDIAN);
    }

    private UserRole resolveUserRole(@Nullable String rawValue) {
        if (rawValue == null) {
            return UserRole.PATIENT;
        }
        try {
            return UserRole.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return UserRole.PATIENT;
        }
    }

    private ClientSupportStatus resolveStatus(@Nullable String rawValue) {
        if (rawValue == null) {
            return ClientSupportStatus.RECEIVED;
        }
        try {
            return ClientSupportStatus.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return ClientSupportStatus.RECEIVED;
        }
    }

    private long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Date) {
            return ((Date) rawValue).getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private int toInt(@Nullable Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String normalizeText(@Nullable String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim();
    }
}
