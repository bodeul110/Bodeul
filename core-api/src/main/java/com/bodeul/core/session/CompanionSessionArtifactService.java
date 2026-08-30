package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.web.multipart.MultipartFile;

interface CompanionSessionArtifactService {

    ArtifactListView replace(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            String purpose,
            UUID clientRequestId,
            List<MultipartFile> attachments);

    ArtifactListView clear(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            String purpose);

    DownloadedArtifact download(
            AppUserRepository.AppUser appUser,
            UUID sessionId,
            UUID artifactId);

    record ArtifactListView(List<CompanionSessionService.ArtifactView> artifacts) {
    }

    record DownloadedArtifact(String fileName, String contentType, byte[] content) {
    }
}
