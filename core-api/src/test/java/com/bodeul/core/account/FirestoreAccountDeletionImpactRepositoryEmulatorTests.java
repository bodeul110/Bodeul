package com.bodeul.core.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteBatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("firestore-emulator")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirestoreAccountDeletionImpactRepositoryEmulatorTests {

    private static final String TARGET_UID = "synthetic-target-user";
    private static final String OTHER_UID = "synthetic-other-user";
    private static final UUID TARGET_USER_ID =
            UUID.fromString("89f9b085-0e02-4a22-9399-f2d019a5d1ba");
    private static final String PROJECT_ID = "demo-bodeul-account-deletion";

    private Firestore firestore;

    @BeforeAll
    void setUpFixture() throws Exception {
        firestore = newEmulatorClient();
        WriteBatch batch = firestore.batch();

        String firstToken = "synthetic-token-one";
        String secondToken = "synthetic-token-two";
        batch.set(firestore.collection("users").document(TARGET_UID), Map.of(
                "notificationTokens", List.of(firstToken, secondToken),
                "notificationTokenEntries", Map.of(
                        tokenEntryKey(firstToken), Map.of("token", firstToken),
                        tokenEntryKey(secondToken), Map.of("token", secondToken)),
                "managerDocumentFiles", Map.of(
                        "idCard", Map.of("fullPath", "synthetic/id-card.pdf"),
                        "license", Map.of("fullPath", "synthetic/license.pdf")),
                "managerDocumentFilePaths", Map.of("idCard", "synthetic/id-card.pdf")));

        addDocuments(batch, "clientSupportRequests", List.of(
                document("support-target-1", "userId", TARGET_UID),
                document("support-target-2", "userId", TARGET_UID),
                document("support-other-1", "userId", OTHER_UID)));
        addDocuments(batch, "supportInquiries", List.of(
                document("inquiry-target-1", "managerUserId", TARGET_UID),
                document("inquiry-other-1", "managerUserId", OTHER_UID),
                document("inquiry-other-2", "managerUserId", OTHER_UID)));
        addDocuments(batch, "appointmentRequests", List.of(
                appointment("appointment-1", TARGET_UID, OTHER_UID, OTHER_UID, TARGET_UID),
                appointment("appointment-2", OTHER_UID, TARGET_UID, TARGET_UID, OTHER_UID),
                appointment("appointment-3", OTHER_UID, TARGET_UID, OTHER_UID, TARGET_UID),
                appointment("appointment-4", OTHER_UID, OTHER_UID, OTHER_UID, OTHER_UID)));
        addDocuments(batch, "companionSessions", List.of(
                session("session-1", TARGET_UID, OTHER_UID, TARGET_UID),
                session("session-2", OTHER_UID, TARGET_UID, OTHER_UID),
                session("session-3", TARGET_UID, TARGET_UID, OTHER_UID),
                session("session-4", OTHER_UID, OTHER_UID, OTHER_UID)));

        batch.commit().get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    void closeClient() throws Exception {
        if (firestore != null) {
            firestore.close();
        }
    }

    @Test
    void emulatorReturnsExactFieldLevelAggregationCounts() {
        var repository = new FirestoreAccountDeletionImpactRepository(firestore);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspect(TARGET_UID);

        assertThat(impact.userDocumentCount()).isEqualTo(1L);
        assertThat(impact.notificationTokenCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryCount()).isEqualTo(2L);
        assertThat(impact.notificationTokenEntryMismatchCount()).isZero();
        assertThat(impact.managerDocumentMetadataCount()).isEqualTo(2L);
        assertThat(impact.managerDocumentReferenceCount()).isEqualTo(2L);
        assertThat(impact.clientSupportRequestCount()).isEqualTo(2L);
        assertThat(impact.supportInquiryCount()).isEqualTo(1L);
        assertThat(impact.directReferences()).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact(
                        1, 2, 1, 2, 2, 2, 1));
        assertThat(impact.toString())
                .doesNotContain(TARGET_UID)
                .doesNotContain("synthetic-token")
                .doesNotContain("synthetic/id-card.pdf");
    }

    @Test
    void emulatorKeepsAnotherUidCountsIsolated() {
        var repository = new FirestoreAccountDeletionImpactRepository(firestore);

        FirebaseAccountDeletionImpactRepository.FirestoreImpact impact =
                repository.inspect(OTHER_UID);

        assertThat(impact.userDocumentCount()).isZero();
        assertThat(impact.clientSupportRequestCount()).isEqualTo(1L);
        assertThat(impact.supportInquiryCount()).isEqualTo(2L);
        assertThat(impact.directReferences()).isEqualTo(
                new FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact(
                        3, 2, 3, 2, 2, 2, 3));
        assertThat(impact.toString()).doesNotContain(OTHER_UID);
    }

    @Test
    void unavailableEmulatorSourceFailsClosedWithEmptyFirestoreCounts() throws Exception {
        Firestore unavailableFirestore = newEmulatorClient();
        unavailableFirestore.close();
        var firebaseRepository = new FirestoreAccountDeletionImpactRepository(unavailableFirestore);
        AccountDeletionImpactRepository postgresRepository = userId -> emptyPostgresImpact();
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(postgresRepository),
                Optional.of(firebaseRepository));

        AccountDeletionReadinessService.ReadinessResult result =
                service.inspect(TARGET_USER_ID, TARGET_UID);
        AccountDeletionReadinessService.SourceInventory firestoreSource = result.sources().stream()
                .filter(source -> source.source() == AccountDeletionReadinessService.Source.FIRESTORE)
                .findFirst()
                .orElseThrow();

        assertThat(firestoreSource.status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(firestoreSource.counts()).isEmpty();
        assertThat(result.complete()).isFalse();
        assertThat(result.blockerCodes()).contains(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.toString())
                .doesNotContain(TARGET_UID)
                .doesNotContain(TARGET_USER_ID.toString());
    }

    private Firestore newEmulatorClient() {
        String emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (emulatorHost == null
                || !emulatorHost.matches("^(localhost|127\\.0\\.0\\.1):[0-9]{1,5}$")) {
            throw new IllegalStateException(
                    "FIRESTORE_EMULATOR_HOST가 localhost 계열로 설정된 격리 환경이 필요합니다.");
        }
        return FirestoreOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setEmulatorHost(emulatorHost)
                .build()
                .getService();
    }

    private void addDocuments(
            WriteBatch batch,
            String collection,
            List<FixtureDocument> documents) {
        documents.forEach(document -> batch.set(
                firestore.collection(collection).document(document.id()),
                document.data()));
    }

    private FixtureDocument document(String id, String field, String value) {
        return new FixtureDocument(id, Map.of(field, value));
    }

    private FixtureDocument appointment(
            String id,
            String patientUid,
            String guardianUid,
            String managerUid,
            String requesterUid) {
        return new FixtureDocument(id, Map.of(
                "patientUserId", patientUid,
                "guardianUserId", guardianUid,
                "managerUserId", managerUid,
                "requesterUserId", requesterUid));
    }

    private FixtureDocument session(
            String id,
            String patientUid,
            String guardianUid,
            String managerUid) {
        return new FixtureDocument(id, Map.of(
                "patientUserId", patientUid,
                "guardianUserId", guardianUid,
                "managerUserId", managerUid));
    }

    private String tokenEntryKey(String token) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private AccountDeletionImpactRepository.PostgreSqlImpact emptyPostgresImpact() {
        return new AccountDeletionImpactRepository.PostgreSqlImpact(
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private record FixtureDocument(String id, Map<String, Object> data) {
    }
}
