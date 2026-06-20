package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 문의 응답 저장 규칙을 한 곳에서 관리한다.
 */
final class FirebaseAdminSupportStore {
    interface ArtifactAppender {
        void append(
                WriteBatch batch,
                String targetId,
                String normalizedResponse,
                String normalizedResponderName
        );
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;

    FirebaseAdminSupportStore(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    void respondSupportInquiry(
            User currentUser,
            String inquiryId,
            String response,
            ArtifactAppender artifactAppender,
            CompletionListener listener
    ) {
        String normalizedResponse = normalizeText(response);
        String normalizedResponderName = normalizeText(currentUser.getName());
        WriteBatch batch = firestore.batch();
        batch.update(
                firestore.collection("supportInquiries").document(inquiryId),
                buildSupportInquiryResponseUpdates(normalizedResponse, normalizedResponderName)
        );
        artifactAppender.append(batch, inquiryId, normalizedResponse, normalizedResponderName);
        batch.commit()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("문의 응답을 저장하지 못했습니다."));
    }

    void respondClientSupportRequest(
            User currentUser,
            String supportRequestId,
            String response,
            ArtifactAppender artifactAppender,
            CompletionListener listener
    ) {
        String normalizedResponse = normalizeText(response);
        String normalizedResponderName = normalizeText(currentUser.getName());
        WriteBatch batch = firestore.batch();
        batch.update(
                firestore.collection("clientSupportRequests").document(supportRequestId),
                buildClientSupportResponseUpdates(normalizedResponse, normalizedResponderName)
        );
        artifactAppender.append(batch, supportRequestId, normalizedResponse, normalizedResponderName);
        batch.commit()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("사용자 문의 응답을 저장하지 못했습니다."));
    }

    static String buildNotificationBody(String inquiryId) {
        return "문의 " + normalizeText(inquiryId) + "에 관리자 응답이 등록되었습니다.";
    }

    private Map<String, Object> buildSupportInquiryResponseUpdates(
            String normalizedResponse,
            String normalizedResponderName
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", SupportInquiryStatus.ANSWERED.name());
        updates.put("responseText", normalizedResponse);
        updates.put("respondedByName", normalizedResponderName);
        updates.put("respondedAt", FieldValue.serverTimestamp());
        return updates;
    }

    private Map<String, Object> buildClientSupportResponseUpdates(
            String normalizedResponse,
            String normalizedResponderName
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", ClientSupportStatus.ANSWERED.name());
        updates.put("responseText", normalizedResponse);
        updates.put("respondedByName", normalizedResponderName);
        updates.put("respondedAt", FieldValue.serverTimestamp());
        updates.put("responseReadByUser", false);
        updates.put("responseReadAt", FieldValue.delete());
        updates.put("responseReminderCount", 0);
        updates.put("responseReminderSentAt", FieldValue.delete());
        return updates;
    }

    private static String normalizeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
