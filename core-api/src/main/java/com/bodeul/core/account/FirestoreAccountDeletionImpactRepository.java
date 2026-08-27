package com.bodeul.core.account;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "FIREBASE_PROJECT_ID")
class FirestoreAccountDeletionImpactRepository implements FirebaseAccountDeletionImpactRepository {

    private static final String FIREBASE_APP_NAME = "bodeul-core-account-inventory";
    private static final int FIRESTORE_OPERATION_TIMEOUT_SECONDS = 12;
    private static final String USERS_COLLECTION = "users";
    private static final String CLIENT_SUPPORT_REQUESTS_COLLECTION = "clientSupportRequests";
    private static final String CLIENT_SUPPORT_REQUEST_USER_FIELD = "userId";
    private static final String SUPPORT_INQUIRIES_COLLECTION = "supportInquiries";
    private static final String SUPPORT_INQUIRY_MANAGER_FIELD = "managerUserId";
    private static final List<String> LEGACY_MANAGER_DOCUMENT_PATH_FIELDS = List.of(
            "managerIdCardStoragePath",
            "managerLicenseStoragePath",
            "managerCriminalRecordStoragePath");

    private final String firebaseProjectId;
    private volatile Firestore firestore;

    @Autowired
    FirestoreAccountDeletionImpactRepository(
            @Value("${FIREBASE_PROJECT_ID:}") String firebaseProjectId) {
        this.firebaseProjectId = firebaseProjectId == null ? "" : firebaseProjectId.trim();
    }

    FirestoreAccountDeletionImpactRepository(Firestore firestore) {
        this.firebaseProjectId = "";
        this.firestore = firestore;
    }

    @Override
    public FirestoreImpact inspect(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new SourceAccessException(
                    new IllegalArgumentException("인증 principal의 Firebase UID가 비어 있습니다."));
        }

        List<ApiFuture<?>> operations = new ArrayList<>();
        try {
            Firestore currentFirestore = requireFirestore();
            long deadlineNanos = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(FIRESTORE_OPERATION_TIMEOUT_SECONDS);

            ApiFuture<DocumentSnapshot> userDocumentFuture = currentFirestore
                    .collection(USERS_COLLECTION)
                    .document(firebaseUid)
                    .get();
            operations.add(userDocumentFuture);
            ApiFuture<AggregateQuerySnapshot> clientSupportCountFuture = currentFirestore
                    .collection(CLIENT_SUPPORT_REQUESTS_COLLECTION)
                    .whereEqualTo(CLIENT_SUPPORT_REQUEST_USER_FIELD, firebaseUid)
                    .count()
                    .get();
            operations.add(clientSupportCountFuture);
            ApiFuture<AggregateQuerySnapshot> supportInquiryCountFuture = currentFirestore
                    .collection(SUPPORT_INQUIRIES_COLLECTION)
                    .whereEqualTo(SUPPORT_INQUIRY_MANAGER_FIELD, firebaseUid)
                    .count()
                    .get();
            operations.add(supportInquiryCountFuture);

            DocumentSnapshot userDocument = await(userDocumentFuture, deadlineNanos);
            long clientSupportRequestCount = await(clientSupportCountFuture, deadlineNanos).getCount();
            long supportInquiryCount = await(supportInquiryCountFuture, deadlineNanos).getCount();
            return summarize(userDocument, clientSupportRequestCount, supportInquiryCount);
        } catch (InterruptedException exception) {
            cancel(operations);
            Thread.currentThread().interrupt();
            throw new SourceAccessException(exception);
        } catch (Exception exception) {
            cancel(operations);
            throw new SourceAccessException(exception);
        }
    }

    private <T> T await(ApiFuture<T> future, long deadlineNanos) throws Exception {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new TimeoutException("Firestore 계정 영향도 조회 시간이 초과됐습니다.");
        }
        return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private void cancel(List<ApiFuture<?>> operations) {
        operations.forEach(operation -> operation.cancel(true));
    }

    private Firestore requireFirestore() throws Exception {
        Firestore current = firestore;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (firestore == null) {
                if (firebaseProjectId.isBlank()) {
                    throw new IllegalStateException("Firebase project 설정이 비어 있습니다.");
                }
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(candidate -> firebaseProjectId.equals(
                                candidate.getOptions().getProjectId()))
                        .findFirst()
                        .orElseGet(this::initializeFirebaseApp);
                firestore = FirestoreClient.getFirestore(app);
            }
            return firestore;
        }
    }

    private FirebaseApp initializeFirebaseApp() {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(firebaseProjectId)
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (Exception exception) {
            throw new IllegalStateException("Firebase Admin SDK를 초기화하지 못했습니다.", exception);
        }
    }

    private FirestoreImpact summarize(
            DocumentSnapshot snapshot,
            long clientSupportRequestCount,
            long supportInquiryCount) {
        if (!snapshot.exists()) {
            return new FirestoreImpact(
                    0, 0, 0, 0, 0, 0,
                    clientSupportRequestCount, supportInquiryCount);
        }

        Map<String, Object> data = snapshot.getData();
        if (data == null) {
            return new FirestoreImpact(
                    1, 0, 0, 0, 0, 0,
                    clientSupportRequestCount, supportInquiryCount);
        }
        NotificationTokenInventory tokenInventory = summarizeNotificationTokens(data);
        Map<?, ?> documentMetadata = asMap(data.get("managerDocumentFiles"));
        return new FirestoreImpact(
                1,
                tokenInventory.tokenCount(),
                tokenInventory.entryCount(),
                tokenInventory.mismatchCount(),
                documentMetadata.size(),
                countManagerDocumentReferences(data, documentMetadata),
                clientSupportRequestCount,
                supportInquiryCount);
    }

    private NotificationTokenInventory summarizeNotificationTokens(Map<String, Object> data) {
        Set<String> notificationTokens = normalizedStrings(data.get("notificationTokens"));
        Map<?, ?> entries = asMap(data.get("notificationTokenEntries"));
        Map<String, Long> metadataTokenCounts = new HashMap<>();
        long malformedMetadataCount = 0;
        long metadataKeyMismatchCount = 0;

        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String metadataToken = normalizedString(asMap(entry.getValue()).get("token"));
            if (metadataToken == null) {
                malformedMetadataCount++;
                continue;
            }
            metadataTokenCounts.merge(metadataToken, 1L, Long::sum);
            if (!(entry.getKey() instanceof String metadataKey)
                    || !metadataKey.equals(encodeNotificationTokenEntryKey(metadataToken))) {
                metadataKeyMismatchCount++;
            }
        }

        long missingMetadataCount = notificationTokens.stream()
                .filter(token -> !metadataTokenCounts.containsKey(token))
                .count();
        long orphanMetadataCount = metadataTokenCounts.keySet().stream()
                .filter(token -> !notificationTokens.contains(token))
                .count();
        long duplicateMetadataCount = metadataTokenCounts.values().stream()
                .mapToLong(count -> Math.max(0, count - 1))
                .sum();

        return new NotificationTokenInventory(
                notificationTokens.size(),
                entries.size(),
                missingMetadataCount
                        + orphanMetadataCount
                        + malformedMetadataCount
                        + duplicateMetadataCount
                        + metadataKeyMismatchCount);
    }

    private String encodeNotificationTokenEntryKey(String token) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private long countManagerDocumentReferences(
            Map<String, Object> data,
            Map<?, ?> documentMetadata) {
        Set<String> references = new HashSet<>();
        for (Object value : documentMetadata.values()) {
            Map<?, ?> metadata = asMap(value);
            addNonBlankReference(references, metadata.get("fullPath"));
        }
        for (Object value : asMap(data.get("managerDocumentFilePaths")).values()) {
            addNonBlankReference(references, value);
        }
        for (String field : LEGACY_MANAGER_DOCUMENT_PATH_FIELDS) {
            addNonBlankReference(references, data.get(field));
        }
        return references.size();
    }

    private void addNonBlankReference(Set<String> references, Object value) {
        if (value instanceof String reference && !reference.isBlank()) {
            references.add(reference.trim());
        }
    }

    private Set<String> normalizedStrings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (Object item : collection) {
            String text = normalizedString(item);
            if (text != null) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private String normalizedString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private record NotificationTokenInventory(
            long tokenCount,
            long entryCount,
            long mismatchCount) {
    }
}
