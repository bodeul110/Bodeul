package com.bodeul.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompanionSessionArtifactServiceTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID MANAGER_ID = UUID.fromString("fdb39fea-f2da-408e-bf46-77dbf2265a73");
    private static final UUID PATIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");
    private static final UUID GUARDIAN_ID = UUID.fromString("6b82d10f-8f20-4a77-b9b4-055a346b689d");

    private FakeSessionRepository sessionRepository;
    private FakeArtifactRepository artifactRepository;
    private FakeStorage storage;
    private DefaultCompanionSessionArtifactService service;
    private boolean guardianAttachmentAllowed;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository(session("PAYMENT_EVIDENCE", "IN_TREATMENT"));
        artifactRepository = new FakeArtifactRepository();
        storage = new FakeStorage();
        guardianAttachmentAllowed = true;
        service = new DefaultCompanionSessionArtifactService(
                sessionRepository,
                artifactRepository,
                storage,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) ->
                        scope != InformationScope.ATTACHMENT || guardianAttachmentAllowed);
    }

    @Test
    void paymentEvidenceIsOptionalButOneSelectedPdfCanBeReplacedIdempotently() {
        UUID requestId = UUID.randomUUID();
        MockMultipartFile pdf = new MockMultipartFile(
                "attachments",
                "영수증.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d, 0x31});

        var first = service.replace(manager(), SESSION_ID, "payment_evidence", requestId, List.of(pdf));
        var retry = service.replace(manager(), SESSION_ID, "PAYMENT_EVIDENCE", requestId, List.of(pdf));

        assertThat(first.artifacts()).hasSize(1);
        assertThat(retry.artifacts()).extracting(CompanionSessionService.ArtifactView::id)
                .containsExactly(first.artifacts().get(0).id());
        assertThat(storage.contents).hasSize(1);
    }

    @Test
    void prescriptionAcceptsUpToThreeImagesAndRejectsPdf() {
        sessionRepository.session = session("PRESCRIPTION_DOCUMENTS", "IN_TREATMENT");
        List<MockMultipartFile> images = List.of(
                jpeg("처방-1.jpg"),
                png("처방-2.png"),
                jpeg("처방-3.jpg"));

        var result = service.replace(
                manager(),
                SESSION_ID,
                "PRESCRIPTION_IMAGE",
                UUID.randomUUID(),
                new ArrayList<>(images));

        assertThat(result.artifacts()).hasSize(3);
        assertThatThrownBy(() -> service.replace(
                manager(),
                SESSION_ID,
                "PRESCRIPTION_IMAGE",
                UUID.randomUUID(),
                List.of(new MockMultipartFile(
                        "attachments",
                        "처방.pdf",
                        "application/pdf",
                        new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d}))))
                .isInstanceOf(CompanionSessionException.class)
                .hasMessageContaining("JPEG 또는 PNG");
    }

    @Test
    void artifactWritesAreBlockedAfterCareEnded() {
        sessionRepository.session = session("MANAGER_JOURNAL", "CARE_ENDED");

        assertThatThrownBy(() -> service.replace(
                manager(),
                SESSION_ID,
                "PAYMENT_EVIDENCE",
                UUID.randomUUID(),
                List.of(jpeg("영수증.jpg"))))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_state_conflict");
    }

    @Test
    void onlyAssignedManagerCanReplaceArtifacts() {
        AppUserRepository.AppUser otherManager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "firebase-other",
                AppUserRole.MANAGER);

        assertThatThrownBy(() -> service.replace(
                otherManager,
                SESSION_ID,
                "PAYMENT_EVIDENCE",
                UUID.randomUUID(),
                List.of(jpeg("영수증.jpg"))))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void conflictingFileNamesUseDifferentStoragePathsForSafeCompensation() {
        MockMultipartFile first = jpeg("영수증-원본.jpg");
        MockMultipartFile second = jpeg("영수증-수정.jpg");

        service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", UUID.randomUUID(), List.of(first));
        String firstPath = artifactRepository.artifacts.get(0).storagePath();
        service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", UUID.randomUUID(), List.of(second));
        String secondPath = artifactRepository.artifacts.get(0).storagePath();

        assertThat(firstPath).isNotEqualTo(secondPath);
        assertThat(storage.contents).containsOnlyKeys(secondPath);
    }

    @Test
    void delayedRetryCannotOverwriteAReplacementThatUsedANewerRequestId() {
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        MockMultipartFile first = jpeg("첫-영수증.jpg");
        MockMultipartFile second = jpeg("최신-영수증.jpg");

        service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", firstRequestId, List.of(first));
        service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", secondRequestId, List.of(second));
        String latestPath = artifactRepository.artifacts.get(0).storagePath();

        var delayedRetry = service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", firstRequestId, List.of(first));

        assertThat(delayedRetry.artifacts()).hasSize(1);
        assertThat(artifactRepository.artifacts.get(0).storagePath()).isEqualTo(latestPath);
        assertThat(storage.contents).containsKey(latestPath).hasSize(2);
    }

    @Test
    void failedUncommittedReplacementLeavesCreatedObjectForOrphanCleanup() {
        artifactRepository.failBeforeCommit = true;

        assertThatThrownBy(() -> service.replace(
                manager(),
                SESSION_ID,
                "PAYMENT_EVIDENCE",
                UUID.randomUUID(),
                List.of(jpeg("영수증.jpg"))))
                .isInstanceOf(CompanionSessionException.class);

        assertThat(artifactRepository.artifacts).isEmpty();
        assertThat(storage.contents).hasSize(1);
    }

    @Test
    void guardianDownloadRequiresCurrentAttachmentConsent() {
        var uploaded = service.replace(
                manager(), SESSION_ID, "PAYMENT_EVIDENCE", UUID.randomUUID(),
                List.of(jpeg("영수증.jpg")));
        UUID artifactId = uploaded.artifacts().get(0).id();
        AppUserRepository.AppUser guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID, "firebase-guardian", AppUserRole.GUARDIAN);

        guardianAttachmentAllowed = false;
        assertThatThrownBy(() -> service.download(guardian, SESSION_ID, artifactId))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");

        guardianAttachmentAllowed = true;
        assertThat(service.download(guardian, SESSION_ID, artifactId).content()).isNotEmpty();

        guardianAttachmentAllowed = false;
        assertThatThrownBy(() -> service.download(guardian, SESSION_ID, artifactId))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_permission_denied");
    }

    @Test
    void downloadRejectsSameSizeContentWithDifferentSha256() {
        var uploaded = service.replace(
                manager(),
                SESSION_ID,
                "PAYMENT_EVIDENCE",
                UUID.randomUUID(),
                List.of(jpeg("영수증.jpg")));
        String path = artifactRepository.artifacts.get(0).storagePath();
        storage.contents.put(path, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x02});

        assertThatThrownBy(() -> service.download(
                new AppUserRepository.AppUser(
                        PATIENT_ID,
                        "firebase-patient",
                        AppUserRole.PATIENT),
                SESSION_ID,
                uploaded.artifacts().get(0).id()))
                .isInstanceOf(CompanionSessionException.class)
                .extracting(exception -> ((CompanionSessionException) exception).error())
                .isEqualTo("companion_session_artifact_unavailable");
    }

    private AppUserRepository.AppUser manager() {
        return new AppUserRepository.AppUser(MANAGER_ID, "firebase-manager", AppUserRole.MANAGER);
    }

    private MockMultipartFile jpeg(String name) {
        return new MockMultipartFile(
                "attachments",
                name,
                "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile(
                "attachments",
                name,
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01});
    }

    private CompanionSessionRepository.SessionRecord session(String stepCode, String status) {
        return new CompanionSessionRepository.SessionRecord(
                SESSION_ID,
                "legacy-session",
                APPOINTMENT_ID,
                MANAGER_ID,
                PATIENT_ID,
                GUARDIAN_ID,
                1,
                1,
                new CompanionSessionRepository.GuideSnapshotRecord(
                        UUID.randomUUID(),
                        1L,
                        1,
                        "HOSPITAL_GUIDE_STEP_CODE_V1",
                        true,
                        List.of(new CompanionSessionRepository.GuideStepRecord(
                                stepCode,
                                1,
                                "현재 단계",
                                "현재 단계 설명"))),
                status,
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                null,
                "none",
                null,
                1L,
                Instant.parse("2026-07-18T00:00:00Z"),
                null,
                null);
    }

    private static final class FakeSessionRepository implements CompanionSessionRepository {
        private SessionRecord session;

        private FakeSessionRepository(SessionRecord session) {
            this.session = session;
        }

        @Override
        public List<SessionRecord> findAllForUser(UUID userId, AppUserRole role) {
            return List.of(session);
        }

        @Override
        public Optional<SessionRecord> findById(UUID sessionId) {
            return SESSION_ID.equals(sessionId) ? Optional.of(session) : Optional.empty();
        }

        @Override
        public Optional<ReportRecord> findReportBySessionId(UUID sessionId) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionRecord> updateDetails(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                SessionPatch patch) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionRecord> advance(
                UUID sessionId,
                UUID managerUserId,
                long expectedVersion,
                UUID appointmentRequestId) {
            return Optional.empty();
        }
    }

    private static final class FakeArtifactRepository
            implements CompanionSessionArtifactRepository {
        private final Map<UUID, String> operationFingerprints = new HashMap<>();
        private List<ArtifactRecord> artifacts = List.of();
        private boolean failBeforeCommit;

        @Override
        public ReplaceResult replace(
                UUID sessionId,
                String purpose,
                UUID clientRequestId,
                UUID uploadedByUserId,
                List<ArtifactMutation> mutations) {
            String fingerprint = mutations.toString();
            String recorded = operationFingerprints.get(clientRequestId);
            if (recorded != null) {
                if (!recorded.equals(fingerprint)) {
                    throw CompanionSessionException.artifactIdempotencyConflict();
                }
                return new ReplaceResult(List.of(), artifacts, false);
            }
            if (failBeforeCommit) {
                throw CompanionSessionException.artifactIdempotencyConflict();
            }
            List<String> replaced = artifacts.stream().map(ArtifactRecord::storagePath).toList();
            operationFingerprints.put(clientRequestId, fingerprint);
            artifacts = mutations.stream()
                    .map(mutation -> new ArtifactRecord(
                            UUID.randomUUID(),
                            sessionId,
                            purpose,
                            clientRequestId,
                            mutation.itemOrder(),
                            mutation.storagePath(),
                            mutation.fileName(),
                            mutation.contentType(),
                            mutation.sizeBytes(),
                            mutation.sha256(),
                            uploadedByUserId,
                            Instant.parse("2026-07-18T00:00:00Z")))
                    .toList();
            return new ReplaceResult(replaced, artifacts, true);
        }

        @Override
        public List<String> clear(UUID sessionId, String purpose) {
            List<String> paths = artifacts.stream().map(ArtifactRecord::storagePath).toList();
            artifacts = List.of();
            return paths;
        }

        @Override
        public Optional<ArtifactRecord> findById(UUID sessionId, UUID artifactId) {
            return artifacts.stream().filter(item -> item.id().equals(artifactId)).findFirst();
        }

    }

    private static final class FakeStorage implements CompanionAttachmentStorage {
        private final Map<String, byte[]> contents = new HashMap<>();

        @Override
        public StoreResult store(
                String storagePath,
                byte[] content,
                String contentType,
                String sha256) {
            return new StoreResult(contents.putIfAbsent(storagePath, content) == null);
        }

        @Override
        public byte[] read(String storagePath, long maxBytes) {
            return contents.get(storagePath);
        }

        @Override
        public void delete(String storagePath) {
            contents.remove(storagePath);
        }
    }
}
