package com.bodeul.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("database")
class DefaultCompanionSessionArtifactService implements CompanionSessionArtifactService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DefaultCompanionSessionArtifactService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String PAYMENT_EVIDENCE = "PAYMENT_EVIDENCE";
    private static final String PRESCRIPTION_IMAGE = "PRESCRIPTION_IMAGE";
    private static final Set<String> PURPOSES = Set.of(PAYMENT_EVIDENCE, PRESCRIPTION_IMAGE);
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2d};

    private final CompanionSessionRepository sessionRepository;
    private final CompanionSessionArtifactRepository artifactRepository;
    private final CompanionAttachmentStorage attachmentStorage;
    private final GuardianSharingConsentAccess consentAccess;

    DefaultCompanionSessionArtifactService(
            CompanionSessionRepository sessionRepository,
            CompanionSessionArtifactRepository artifactRepository,
            CompanionAttachmentStorage attachmentStorage,
            GuardianSharingConsentAccess consentAccess) {
        this.sessionRepository = sessionRepository;
        this.artifactRepository = artifactRepository;
        this.attachmentStorage = attachmentStorage;
        this.consentAccess = consentAccess;
    }

    @Override
    public ArtifactListView replace(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            String purpose,
            UUID clientRequestId,
            List<MultipartFile> attachments) {
        String normalizedPurpose = normalizePurpose(purpose);
        CompanionSessionRepository.SessionRecord session = requireWritableSession(
                appUser,
                sessionId,
                normalizedPurpose);
        if (clientRequestId == null) {
            throw CompanionSessionException.invalidRequest("첨부 교체 요청 식별자가 필요합니다.");
        }
        List<MultipartFile> files = attachments == null ? List.of() : List.copyOf(attachments);
        int limit = PAYMENT_EVIDENCE.equals(normalizedPurpose) ? 1 : 3;
        if (files.isEmpty() || files.size() > limit) {
            throw CompanionSessionException.invalidRequest(
                    PAYMENT_EVIDENCE.equals(normalizedPurpose)
                            ? "결제 증빙은 한 번에 1개까지 선택할 수 있습니다."
                            : "처방 이미지는 1개부터 3개까지 선택할 수 있습니다.");
        }

        List<PreparedArtifact> prepared = new ArrayList<>();
        List<String> createdPaths = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                PreparedArtifact artifact = prepare(
                        session.id(),
                        normalizedPurpose,
                        clientRequestId,
                        index,
                        files.get(index));
                CompanionAttachmentStorage.StoreResult stored = store(artifact);
                if (stored.created()) {
                    createdPaths.add(artifact.mutation().storagePath());
                }
                prepared.add(artifact);
            }
            CompanionSessionArtifactRepository.ReplaceResult result = artifactRepository.replace(
                    session.id(),
                    normalizedPurpose,
                    clientRequestId,
                    appUser.id(),
                    prepared.stream().map(PreparedArtifact::mutation).toList());
            if (result.applied()) {
                cleanupReplaced(result.replacedStoragePaths(), createdPaths);
            } else {
                preserveOrphans(createdPaths, "IDEMPOTENT_REPLAY_NOT_APPLIED");
            }
            return toView(result.artifacts());
        } catch (RuntimeException exception) {
            preserveOrphans(createdPaths, exception);
            throw exception;
        }
    }

    @Override
    public ArtifactListView clear(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        CompanionSessionRepository.SessionRecord session = requireWritableSession(
                appUser,
                sessionId,
                normalizedPurpose);
        cleanupBestEffort(artifactRepository.clear(session.id(), normalizedPurpose));
        return new ArtifactListView(List.of());
    }

    @Override
    public DownloadedArtifact download(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID artifactId) {
        CompanionSessionRepository.SessionRecord session = requireReadableSession(appUser, sessionId);
        CompanionSessionArtifactRepository.ArtifactRecord artifact = artifactRepository
                .findById(session.id(), artifactId)
                .orElseThrow(CompanionSessionException::artifactNotFound);
        byte[] content = read(artifact.storagePath());
        if (content == null
                || content.length != artifact.sizeBytes()
                || !sha256(content).equalsIgnoreCase(artifact.sha256())) {
            throw CompanionSessionException.artifactUnavailable();
        }
        return new DownloadedArtifact(
                artifact.fileName(),
                artifact.contentType(),
                content);
    }

    private CompanionSessionRepository.SessionRecord requireWritableSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            String purpose) {
        if (appUser == null || appUser.role() != AppUserRole.MANAGER) {
            throw CompanionSessionException.managerRequired();
        }
        CompanionSessionRepository.SessionRecord session = requireSession(sessionId);
        if (!appUser.id().equals(session.managerUserId())) {
            throw CompanionSessionException.permissionDenied();
        }
        if (Set.of("CARE_ENDED", "COMPLETED", "CANCELED").contains(session.currentStatus())
                || session.careEndedAt() != null) {
            throw CompanionSessionException.stateConflict();
        }
        String expectedStep = PAYMENT_EVIDENCE.equals(purpose)
                ? PAYMENT_EVIDENCE
                : "PRESCRIPTION_DOCUMENTS";
        if (session.currentStepOrder() <= 0
                || session.guideSnapshot() == null
                || session.currentStepOrder() > session.guideSnapshot().steps().size()
                || !expectedStep.equals(
                        session.guideSnapshot().steps().get(session.currentStepOrder() - 1).code())) {
            throw CompanionSessionException.stateConflict();
        }
        return session;
    }

    private CompanionSessionRepository.SessionRecord requireReadableSession(
            AppUserRepository.AppUser appUser,
            UUID sessionId) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT
                && appUser.role() != AppUserRole.GUARDIAN
                && appUser.role() != AppUserRole.MANAGER)) {
            throw CompanionSessionException.roleNotSupported();
        }
        CompanionSessionRepository.SessionRecord session = requireSession(sessionId);
        UUID allowed = switch (appUser.role()) {
            case PATIENT -> session.patientUserId();
            case GUARDIAN -> session.guardianUserId();
            case MANAGER -> session.managerUserId();
            default -> null;
        };
        if (!appUser.id().equals(allowed)) {
            throw CompanionSessionException.permissionDenied();
        }
        if (appUser.role() == AppUserRole.GUARDIAN
                && !consentAccess.isAllowed(
                appUser,
                session.appointmentRequestId(),
                session.patientUserId(),
                session.guardianUserId(),
                InformationScope.ATTACHMENT)) {
            throw CompanionSessionException.permissionDenied();
        }
        return session;
    }

    private CompanionSessionRepository.SessionRecord requireSession(UUID sessionId) {
        if (sessionId == null) {
            throw CompanionSessionException.invalidRequest("동행 세션 ID가 필요합니다.");
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(CompanionSessionException::notFound);
    }

    private PreparedArtifact prepare(
            UUID sessionId,
            String purpose,
            UUID clientRequestId,
            int itemOrder,
            MultipartFile file) {
        if (file == null || file.isEmpty()
                || file.getSize() <= 0L
                || file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw CompanionSessionException.invalidRequest("첨부 파일 크기는 10MiB 이하여야 합니다.");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!isAllowedContentType(purpose, contentType)) {
            throw CompanionSessionException.invalidRequest(
                    PAYMENT_EVIDENCE.equals(purpose)
                            ? "결제 증빙은 JPEG, PNG 또는 PDF만 등록할 수 있습니다."
                            : "처방 이미지는 JPEG 또는 PNG만 등록할 수 있습니다.");
        }
        byte[] content = read(file);
        if (!hasExpectedSignature(contentType, content)) {
            throw CompanionSessionException.invalidRequest("첨부 파일 형식과 내용이 일치하지 않습니다.");
        }
        String sha256 = sha256(content);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileNameFingerprint = sha256(fileName.getBytes(StandardCharsets.UTF_8))
                .substring(0, 16);
        String storagePath = "companion-session-artifacts/" + sessionId + "/"
                + purpose.toLowerCase(Locale.ROOT) + "/" + clientRequestId + "-"
                + itemOrder + "-" + fileNameFingerprint + "-" + sha256
                + "." + extension(contentType);
        return new PreparedArtifact(
                new CompanionSessionArtifactRepository.ArtifactMutation(
                        itemOrder,
                        storagePath,
                        fileName,
                        contentType,
                        content.length,
                        sha256),
                content,
                sha256);
    }

    private ArtifactListView toView(
            List<CompanionSessionArtifactRepository.ArtifactRecord> artifacts) {
        return new ArtifactListView(artifacts.stream()
                .map(artifact -> new CompanionSessionService.ArtifactView(
                        artifact.id(),
                        artifact.purpose(),
                        artifact.fileName(),
                        artifact.contentType(),
                        artifact.sizeBytes(),
                        artifact.createdAt() == null ? "" : artifact.createdAt().toString()))
                .toList());
    }

    private CompanionAttachmentStorage.StoreResult store(PreparedArtifact artifact) {
        try {
            return attachmentStorage.store(
                    artifact.mutation().storagePath(),
                    artifact.content(),
                    artifact.mutation().contentType(),
                    artifact.sha256());
        } catch (RuntimeException exception) {
            throw CompanionSessionException.artifactUnavailable();
        }
    }

    private byte[] read(String storagePath) {
        try {
            return attachmentStorage.read(storagePath, MAX_FILE_SIZE_BYTES);
        } catch (RuntimeException exception) {
            throw CompanionSessionException.artifactUnavailable();
        }
    }

    private String normalizePurpose(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!PURPOSES.contains(normalized)) {
            throw CompanionSessionException.invalidRequest("동행 첨부 용도를 확인해 주세요.");
        }
        return normalized;
    }

    private String normalizeContentType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private boolean isAllowedContentType(String purpose, String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || (PAYMENT_EVIDENCE.equals(purpose) && "application/pdf".equals(contentType));
    }

    private byte[] read(MultipartFile file) {
        try {
            byte[] content = file.getBytes();
            if (content.length != file.getSize() || content.length > MAX_FILE_SIZE_BYTES) {
                throw CompanionSessionException.invalidRequest("첨부 파일 크기가 전송 중 변경되었습니다.");
            }
            return content;
        } catch (IOException exception) {
            throw CompanionSessionException.artifactUnavailable();
        }
    }

    private String sanitizeFileName(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        normalized = normalized.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_");
        if (normalized.isBlank()) {
            normalized = "companion-session-artifact";
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private boolean hasExpectedSignature(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(content, JPEG_SIGNATURE);
            case "image/png" -> startsWith(content, PNG_SIGNATURE);
            case "application/pdf" -> startsWith(content, PDF_SIGNATURE);
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            default -> throw CompanionSessionException.invalidRequest("첨부 파일 형식을 확인해 주세요.");
        };
    }

    private void cleanupReplaced(List<String> replacedPaths, List<String> newPaths) {
        cleanupBestEffort(replacedPaths.stream().filter(path -> !newPaths.contains(path)).toList());
    }

    private void cleanupBestEffort(List<String> storagePaths) {
        for (String storagePath : storagePaths) {
            try {
                attachmentStorage.delete(storagePath);
            } catch (RuntimeException exception) {
                LOGGER.warn("교체된 동행 첨부 파일을 정리하지 못했습니다. path={}", storagePath);
            }
        }
    }

    private void preserveOrphans(List<String> storagePaths, RuntimeException cause) {
        preserveOrphans(storagePaths, cause.getClass().getSimpleName());
    }

    private void preserveOrphans(List<String> storagePaths, String cause) {
        if (!storagePaths.isEmpty()) {
            LOGGER.warn(
                    "동행 첨부 DB 반영 실패로 생성 객체를 orphan 정리 대상으로 보존합니다. paths={}, cause={}",
                    storagePaths,
                    cause);
        }
    }

    private record PreparedArtifact(
            CompanionSessionArtifactRepository.ArtifactMutation mutation,
            byte[] content,
            String sha256) {
    }
}
