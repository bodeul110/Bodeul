package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CompanionSessionArtifactRepository {

    ReplaceResult replace(
            UUID sessionId,
            String purpose,
            UUID clientRequestId,
            UUID uploadedByUserId,
            List<ArtifactMutation> artifacts);

    List<String> clear(UUID sessionId, String purpose);

    Optional<ArtifactRecord> findById(UUID sessionId, UUID artifactId);

    record ArtifactMutation(
            int itemOrder,
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256) {
    }

    record ArtifactRecord(
            UUID id,
            UUID companionSessionId,
            String purpose,
            UUID clientRequestId,
            int itemOrder,
            String storagePath,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256,
            UUID uploadedByUserId,
            Instant createdAt) {
    }

    record ReplaceResult(
            List<String> replacedStoragePaths,
            List<ArtifactRecord> artifacts,
            boolean applied) {
    }
}
