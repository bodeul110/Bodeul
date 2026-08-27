package com.bodeul.core.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirestoreAccountDeletionImpactRepositoryTests {

    private static final String FIREBASE_UID = "firebase-user-1";

    private Firestore firestore;
    private CollectionReference users;
    private DocumentReference userDocument;
    private DocumentSnapshot snapshot;
    private FirestoreAccountDeletionImpactRepository repository;

    @BeforeEach
    void setUp() {
        firestore = mock(Firestore.class);
        users = mock(CollectionReference.class);
        userDocument = mock(DocumentReference.class);
        snapshot = mock(DocumentSnapshot.class);
        repository = new FirestoreAccountDeletionImpactRepository(firestore);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document(FIREBASE_UID)).thenReturn(userDocument);
        when(userDocument.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
    }

    @Test
    void inspectReadsExactlyTheAuthenticatedUserDocumentAndReturnsOnlyCounts() {
        String token = "sensitive-fcm-token";
        String firstPath = "manager-documents/firebase-user-1/idCard/private.pdf";
        String secondPath = "manager-documents/firebase-user-1/license/private.pdf";
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getData()).thenReturn(Map.of(
                "notificationTokens", List.of(token, "second-token"),
                "notificationTokenEntries", Map.of(
                        tokenEntryKey(token), Map.of("token", token),
                        tokenEntryKey("second-token"), Map.of("token", "second-token")),
                "managerDocumentFiles", Map.of(
                        "idCard", Map.of("fullPath", firstPath, "fileName", "private.pdf"),
                        "license", Map.of("fullPath", secondPath)),
                "managerDocumentFilePaths", Map.of("idCard", firstPath),
                "managerIdCardStoragePath", firstPath));

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspectUserDocument(FIREBASE_UID);

        assertThat(impact.userDocumentCount()).isEqualTo(1L);
        assertThat(impact.notificationTokenCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryMismatchCount()).isZero();
        assertThat(impact.managerDocumentMetadataCount()).isEqualTo(2L);
        assertThat(impact.managerDocumentReferenceCount()).isEqualTo(2L);
        assertThat(impact.toString())
                .doesNotContain(FIREBASE_UID)
                .doesNotContain(token)
                .doesNotContain("private.pdf")
                .doesNotContain(firstPath);

        verify(firestore).collection("users");
        verify(users).document(FIREBASE_UID);
        verify(userDocument).get();
    }

    @Test
    void tokenInventoryNormalizesValuesAndCountsEveryMetadataMismatchCategory() {
        String token = "sensitive-fcm-token";
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getData()).thenReturn(Map.of(
                "notificationTokens", List.of(
                        token,
                        "  " + token + "  ",
                        "second-token",
                        "",
                        "   ",
                        42),
                "notificationTokenEntries", Map.of(
                        tokenEntryKey(token), Map.of("token", token),
                        "wrong-duplicate-key", Map.of("token", "  " + token + "  "),
                        tokenEntryKey("orphan-token"), Map.of("token", "orphan-token"),
                        "malformed-map", Map.of("platform", "android"),
                        "malformed-value", "not-a-map")));

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspectUserDocument(FIREBASE_UID);

        assertThat(impact.notificationTokenCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryCount()).isEqualTo(5L);
        assertThat(impact.notificationTokenEntryMismatchCount()).isEqualTo(6L);
        assertThat(impact.toString())
                .doesNotContain(token)
                .doesNotContain("orphan-token")
                .doesNotContain("wrong-duplicate-key");
    }

    @Test
    void missingUserDocumentReturnsZeroCounts() {
        when(snapshot.exists()).thenReturn(false);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspectUserDocument(FIREBASE_UID);

        assertThat(impact).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreImpact(0, 0, 0, 0, 0, 0));
    }

    @Test
    void firestoreFailureIsWrappedWithoutAddingRawDataToTheStableMessage() {
        when(userDocument.get()).thenReturn(ApiFutures.immediateFailedFuture(
                new IllegalStateException("secret firestore endpoint")));

        assertThatThrownBy(() -> repository.inspectUserDocument(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret firestore endpoint");
    }

    private String tokenEntryKey(String token) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
