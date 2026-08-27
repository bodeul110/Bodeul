package com.bodeul.core.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    private static final List<CountQueryDefinition> COUNT_QUERY_DEFINITIONS = List.of(
            new CountQueryDefinition(
                    "clientSupportRequestCount", "clientSupportRequests", "userId", 4),
            new CountQueryDefinition(
                    "supportInquiryCount", "supportInquiries", "managerUserId", 2),
            new CountQueryDefinition(
                    "appointmentRequestPatientCount", "appointmentRequests", "patientUserId", 11),
            new CountQueryDefinition(
                    "appointmentRequestGuardianCount", "appointmentRequests", "guardianUserId", 12),
            new CountQueryDefinition(
                    "appointmentRequestManagerCount", "appointmentRequests", "managerUserId", 13),
            new CountQueryDefinition(
                    "appointmentRequestRequesterCount", "appointmentRequests", "requesterUserId", 14),
            new CountQueryDefinition(
                    "companionSessionPatientCount", "companionSessions", "patientUserId", 15),
            new CountQueryDefinition(
                    "companionSessionGuardianCount", "companionSessions", "guardianUserId", 16),
            new CountQueryDefinition(
                    "companionSessionManagerCount", "companionSessions", "managerUserId", 17));

    private Firestore firestore;
    private CollectionReference users;
    private DocumentReference userDocument;
    private DocumentSnapshot snapshot;
    private Map<String, CollectionReference> collections;
    private Map<String, CountQueryStub> countQueries;
    private FirestoreAccountDeletionImpactRepository repository;

    @BeforeEach
    void setUp() {
        firestore = mock(Firestore.class);
        users = mock(CollectionReference.class);
        userDocument = mock(DocumentReference.class);
        snapshot = mock(DocumentSnapshot.class);
        collections = new HashMap<>();
        countQueries = new LinkedHashMap<>();
        repository = new FirestoreAccountDeletionImpactRepository(firestore);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document(FIREBASE_UID)).thenReturn(userDocument);
        when(userDocument.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        COUNT_QUERY_DEFINITIONS.forEach(definition ->
                countQueries.put(definition.name(), stubCountQuery(definition)));
    }

    @Test
    void inspectReadsExactUidReferencesThroughAggregationQueriesAndReturnsOnlyCounts() {
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
        assertDirectReferenceCounts(impact.directReferences());
        assertThat(impact.toString())
                .doesNotContain(FIREBASE_UID)
                .doesNotContain(token)
                .doesNotContain("private.pdf")
                .doesNotContain(firstPath);

        verify(firestore).collection("users");
        verify(users).document(FIREBASE_UID);
        verify(userDocument).get();
        countQueries.values().forEach(stub -> {
            verify(stub.collection()).whereEqualTo(stub.definition().field(), FIREBASE_UID);
            verify(stub.query()).count();
            verify(stub.query(), never()).get();
            verify(stub.aggregate()).get();
        });
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
    void missingUserDocumentStillReturnsEveryDirectReferenceCount() {
        when(snapshot.exists()).thenReturn(false);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspect(FIREBASE_UID);

        assertThat(impact).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreImpact(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        4,
                        2,
                        directReferenceImpact()));
    }

    @Test
    void zeroDirectReferenceCountsRemainExplicitlyAvailable() {
        when(snapshot.exists()).thenReturn(false);
        countQueries.values().forEach(stub -> when(stub.snapshot().getCount()).thenReturn(0L));

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact = repository.inspect(FIREBASE_UID);

        assertThat(impact.clientSupportRequestCount()).isZero();
        assertThat(impact.supportInquiryCount()).isZero();
        assertThat(impact.directReferences()).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact(
                        0, 0, 0, 0, 0, 0, 0));
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

    @ParameterizedTest
    @MethodSource("countQueryNames")
    void anyAggregationFailureFailsTheWholeFirestoreInventoryClosed(String queryName) {
        CountQueryStub failingQuery = countQueries.get(queryName);
        when(failingQuery.aggregate().get()).thenReturn(ApiFutures.immediateFailedFuture(
                new IllegalStateException("secret aggregation detail")));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret aggregation detail");
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutCancelsEveryStartedFirestoreOperation() throws Exception {
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        Map<String, ApiFuture<AggregateQuerySnapshot>> aggregateFutures = new LinkedHashMap<>();
        when(userDocument.get()).thenReturn(userFuture);
        countQueries.forEach((name, stub) -> {
            ApiFuture<AggregateQuerySnapshot> future = mock(ApiFuture.class);
            aggregateFutures.put(name, future);
            when(stub.aggregate().get()).thenReturn(future);
        });
        when(userFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new TimeoutException("secret timeout detail"));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret timeout detail");

        verify(userFuture).cancel(true);
        aggregateFutures.values().forEach(future -> verify(future).cancel(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionCancelsEveryStartedOperationAndRestoresTheInterruptFlag() throws Exception {
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        Map<String, ApiFuture<AggregateQuerySnapshot>> aggregateFutures = new LinkedHashMap<>();
        when(userDocument.get()).thenReturn(userFuture);
        countQueries.forEach((name, stub) -> {
            ApiFuture<AggregateQuerySnapshot> future = mock(ApiFuture.class);
            aggregateFutures.put(name, future);
            when(stub.aggregate().get()).thenReturn(future);
        });
        when(userFuture.get(anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new InterruptedException("secret interruption detail"));

        try {
            assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                    .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                    .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                    .hasMessageNotContaining("secret interruption detail");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(userFuture).cancel(true);
            aggregateFutures.values().forEach(future -> verify(future).cancel(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void synchronousQuerySetupFailureCancelsOnlyTheOperationsThatWereStarted() {
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        ApiFuture<AggregateQuerySnapshot> clientSupportFuture = mock(ApiFuture.class);
        ApiFuture<AggregateQuerySnapshot> supportInquiryFuture = mock(ApiFuture.class);
        when(userDocument.get()).thenReturn(userFuture);
        when(countQueries.get("clientSupportRequestCount").aggregate().get())
                .thenReturn(clientSupportFuture);
        when(countQueries.get("supportInquiryCount").aggregate().get())
                .thenReturn(supportInquiryFuture);
        CountQueryStub failingQuery = countQueries.get("appointmentRequestPatientCount");
        when(failingQuery.collection().whereEqualTo(failingQuery.definition().field(), FIREBASE_UID))
                .thenThrow(new IllegalStateException("secret setup detail"));

        assertThatThrownBy(() -> repository.inspect(FIREBASE_UID))
                .isInstanceOf(FirebaseAccountDeletionImpactRepository.SourceAccessException.class)
                .hasMessage("Firestore 계정 영향도를 확인할 수 없습니다.")
                .hasMessageNotContaining("secret setup detail");

        verify(userFuture).cancel(true);
        verify(clientSupportFuture).cancel(true);
        verify(supportInquiryFuture).cancel(true);
        verify(countQueries.get("appointmentRequestGuardianCount").aggregate(), never()).get();
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

    private CountQueryStub stubCountQuery(CountQueryDefinition definition) {
        CollectionReference collection = collections.computeIfAbsent(
                definition.collection(),
                name -> {
                    CollectionReference reference = mock(CollectionReference.class);
                    when(firestore.collection(name)).thenReturn(reference);
                    return reference;
                });
        Query query = mock(Query.class);
        AggregateQuery aggregate = mock(AggregateQuery.class);
        AggregateQuerySnapshot aggregateSnapshot = mock(AggregateQuerySnapshot.class);
        when(collection.whereEqualTo(definition.field(), FIREBASE_UID)).thenReturn(query);
        when(query.count()).thenReturn(aggregate);
        when(aggregate.get()).thenReturn(ApiFutures.immediateFuture(aggregateSnapshot));
        when(aggregateSnapshot.getCount()).thenReturn(definition.count());
        return new CountQueryStub(definition, collection, query, aggregate, aggregateSnapshot);
    }

    private void assertDirectReferenceCounts(
            FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact impact) {
        assertThat(impact).isEqualTo(directReferenceImpact());
    }

    private FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact
            directReferenceImpact() {
        return new FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact(
                11, 12, 13, 14, 15, 16, 17);
    }

    private static Stream<String> countQueryNames() {
        return COUNT_QUERY_DEFINITIONS.stream().map(CountQueryDefinition::name);
    }

    private String tokenEntryKey(String token) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private record CountQueryDefinition(
            String name,
            String collection,
            String field,
            long count) {
    }

    private record CountQueryStub(
            CountQueryDefinition definition,
            CollectionReference collection,
            Query query,
            AggregateQuery aggregate,
            AggregateQuerySnapshot snapshot) {
    }
}
