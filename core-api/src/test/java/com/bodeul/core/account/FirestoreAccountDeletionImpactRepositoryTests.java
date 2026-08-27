package com.bodeul.core.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FirestoreAccountDeletionImpactRepositoryTests {

    private static final String FIREBASE_UID = "firebase-user-1";

    private Firestore firestore;
    private CollectionReference users;
    private DocumentReference userDocument;
    private DocumentSnapshot snapshot;
    private CollectionReference clientSupportRequests;
    private Query clientSupportQuery;
    private AggregateQuery clientSupportCount;
    private AggregateQuerySnapshot clientSupportCountSnapshot;
    private CollectionReference supportInquiries;
    private Query supportInquiryQuery;
    private AggregateQuery supportInquiryCount;
    private AggregateQuerySnapshot supportInquiryCountSnapshot;
    private FirestoreAccountDeletionImpactRepository repository;

    @BeforeEach
    void setUp() {
        firestore = mock(Firestore.class);
        users = mock(CollectionReference.class);
        userDocument = mock(DocumentReference.class);
        snapshot = mock(DocumentSnapshot.class);
        clientSupportRequests = mock(CollectionReference.class);
        clientSupportQuery = mock(Query.class);
        clientSupportCount = mock(AggregateQuery.class);
        clientSupportCountSnapshot = mock(AggregateQuerySnapshot.class);
        supportInquiries = mock(CollectionReference.class);
        supportInquiryQuery = mock(Query.class);
        supportInquiryCount = mock(AggregateQuery.class);
        supportInquiryCountSnapshot = mock(AggregateQuerySnapshot.class);
        repository = new FirestoreAccountDeletionImpactRepository(firestore);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document(FIREBASE_UID)).thenReturn(userDocument);
        when(userDocument.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(firestore.collection("clientSupportRequests")).thenReturn(clientSupportRequests);
        when(clientSupportRequests.whereEqualTo("userId", FIREBASE_UID)).thenReturn(clientSupportQuery);
        when(clientSupportQuery.count()).thenReturn(clientSupportCount);
        when(clientSupportCount.get()).thenReturn(ApiFutures.immediateFuture(clientSupportCountSnapshot));
        when(clientSupportCountSnapshot.getCount()).thenReturn(4L);
        when(firestore.collection("supportInquiries")).thenReturn(supportInquiries);
        when(supportInquiries.whereEqualTo("managerUserId", FIREBASE_UID)).thenReturn(supportInquiryQuery);
        when(supportInquiryQuery.count()).thenReturn(supportInquiryCount);
        when(supportInquiryCount.get()).thenReturn(ApiFutures.immediateFuture(supportInquiryCountSnapshot));
        when(supportInquiryCountSnapshot.getCount()).thenReturn(2L);
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
                repository.inspect(FIREBASE_UID);

        assertThat(impact.userDocumentCount()).isEqualTo(1L);
        assertThat(impact.notificationTokenCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryMismatchCount()).isZero();
        assertThat(impact.managerDocumentMetadataCount()).isEqualTo(2L);
        assertThat(impact.managerDocumentReferenceCount()).isEqualTo(2L);
        assertThat(impact.clientSupportRequestCount()).isEqualTo(4L);
        assertThat(impact.supportInquiryCount()).isEqualTo(2L);
        assertThat(impact.toString())
                .doesNotContain(FIREBASE_UID)
                .doesNotContain(token)
                .doesNotContain("private.pdf")
                .doesNotContain(firstPath);

        verify(firestore).collection("users");
        verify(users).document(FIREBASE_UID);
        verify(userDocument).get();
        verify(clientSupportRequests).whereEqualTo("userId", FIREBASE_UID);
        verify(clientSupportQuery).count();
        verify(clientSupportQuery, never()).get();
        verify(clientSupportCount).get();
        verify(supportInquiries).whereEqualTo("managerUserId", FIREBASE_UID);
        verify(supportInquiryQuery).count();
        verify(supportInquiryQuery, never()).get();
        verify(supportInquiryCount).get();
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
                repository.inspect(FIREBASE_UID);

        assertThat(impact.notificationTokenCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryCount()).isEqualTo(5L);
        assertThat(impact.notificationTokenEntryMismatchCount()).isEqualTo(6L);
        assertThat(impact.toString())
                .doesNotContain(token)
                .doesNotContain("orphan-token")
                .doesNotContain("wrong-duplicate-key");
    }

    @Test
    void missingUserDocumentStillReturnsSupportDocumentCounts() {
        when(snapshot.exists()).thenReturn(false);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspect(FIREBASE_UID);

        assertThat(impact).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreImpact(
                        0, 0, 0, 0, 0, 0, 4, 2));
    }

    @Test
    void zeroSupportCountsAreReturnedWithoutChangingThePartialInventoryContract() {
        when(snapshot.exists()).thenReturn(false);
        when(clientSupportCountSnapshot.getCount()).thenReturn(0L);
        when(supportInquiryCountSnapshot.getCount()).thenReturn(0L);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact = repository.inspect(FIREBASE_UID);

        assertThat(impact.clientSupportRequestCount()).isZero();
        assertThat(impact.supportInquiryCount()).isZero();
    }

    @Test
    void firestoreFailureIsWrappedWithoutAddingRawDataToTheStableMessage() {
        when(userDocument.get()).thenReturn(ApiFutures.immediateFailedFuture(
                new IllegalStateException("secret firestore endpoint")));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret firestore endpoint");
    }

    @Test
    void clientSupportCountFailureFailsTheWholeFirestoreInventoryClosed() {
        when(clientSupportCount.get()).thenReturn(ApiFutures.immediateFailedFuture(
                new IllegalStateException("secret support query")));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret support query");
    }

    @Test
    void managerSupportCountFailureFailsTheWholeFirestoreInventoryClosed() {
        when(supportInquiryCount.get()).thenReturn(ApiFutures.immediateFailedFuture(
                new IllegalStateException("secret manager query")));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret manager query");
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutCancelsEveryStartedFirestoreOperation() throws Exception {
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        ApiFuture<AggregateQuerySnapshot> clientSupportFuture = mock(ApiFuture.class);
        ApiFuture<AggregateQuerySnapshot> supportInquiryFuture = mock(ApiFuture.class);
        when(userDocument.get()).thenReturn(userFuture);
        when(clientSupportCount.get()).thenReturn(clientSupportFuture);
        when(supportInquiryCount.get()).thenReturn(supportInquiryFuture);
        when(userFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new TimeoutException("secret timeout detail"));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret timeout detail");

        verify(userFuture).cancel(true);
        verify(clientSupportFuture).cancel(true);
        verify(supportInquiryFuture).cancel(true);
    }

    @Test
    void blankAuthenticatedUidFailsBeforeFirestoreAccess() {
        assertThatThrownBy(() -> repository.inspect(null))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.");
        assertThatThrownBy(() -> repository.inspect("   "))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.");

        verifyNoInteractions(firestore);
    }

    private String tokenEntryKey(String token) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
