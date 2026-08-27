package com.bodeul.core.account;

public interface FirebaseAccountDeletionImpactRepository {

    FirestoreImpact inspect(String firebaseUid);

    record FirestoreImpact(
            long userDocumentCount,
            long notificationTokenCount,
            long notificationTokenEntryCount,
            long notificationTokenEntryMismatchCount,
            long managerDocumentMetadataCount,
            long managerDocumentReferenceCount,
            long clientSupportRequestCount,
            long supportInquiryCount) {
    }

    final class SourceAccessException extends RuntimeException {

        SourceAccessException(Throwable cause) {
            super("Firestore 계정 영향도를 확인할 수 없습니다.", cause);
        }
    }
}
