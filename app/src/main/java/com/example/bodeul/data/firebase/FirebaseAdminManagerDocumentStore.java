package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 서류 심사 저장 규칙과 검증을 한 곳에서 관리한다.
 */
final class FirebaseAdminManagerDocumentStore {
    interface ManagerDocumentMapper {
        @Nullable
        User toUser(DocumentSnapshot documentSnapshot);

        ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot);

        List<ManagerDocumentHistoryEntry> toManagerDocumentHistory(DocumentSnapshot documentSnapshot);

        List<ManagerDocumentHistoryEntry> appendManagerDocumentHistory(
                List<ManagerDocumentHistoryEntry> historyEntries,
                ManagerDocumentHistoryEntry historyEntry
        );

        List<Map<String, Object>> toManagerDocumentHistoryPayload(
                List<ManagerDocumentHistoryEntry> historyEntries
        );
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final ManagerDocumentMapper mapper;

    FirebaseAdminManagerDocumentStore(
            FirebaseFirestore firestore,
            ManagerDocumentMapper mapper
    ) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            CompletionListener listener
    ) {
        DocumentReference managerRef = firestore.collection("users").document(managerUserId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot documentSnapshot = transaction.get(managerRef);

            User manager = mapper.toUser(documentSnapshot);
            if (manager == null || manager.getRole() != UserRole.MANAGER) {
                throw new FirebaseFirestoreException(
                        "매니저 계정을 찾지 못했습니다.",
                        FirebaseFirestoreException.Code.ABORTED
                );
            }

            ManagerHomeProfile profile = mapper.toManagerHomeProfile(documentSnapshot);
            if (normalizeText(profile.getDocumentSummary()).isEmpty()) {
                throw new FirebaseFirestoreException(
                        "검토할 서류 요약이 아직 등록되지 않았습니다.",
                        FirebaseFirestoreException.Code.ABORTED
                );
            }

            List<ManagerDocumentHistoryEntry> historyEntries = mapper.appendManagerDocumentHistory(
                    mapper.toManagerDocumentHistory(documentSnapshot),
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

            Map<String, Object> updates = new HashMap<>();
            updates.put("managerDocumentStatus", status.name());
            updates.put("managerDocumentReviewNote", normalizeText(reviewNote));
            updates.put("managerDocumentReviewedAt", FieldValue.serverTimestamp());
            updates.put("managerDocumentReviewedByName", normalizeText(currentUser.getName()));
            updates.put("managerDocumentHistory", mapper.toManagerDocumentHistoryPayload(historyEntries));

            transaction.update(managerRef, updates);
            return null;
        }).addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError(
                                "매니저 서류 검토 상태를 저장하지 못했습니다. 원인: "
                                        + exception.getMessage()
                        ));
    }

    private static String normalizeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
