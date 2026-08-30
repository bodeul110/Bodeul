package com.bodeul.core.session;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/companion-sessions/{sessionId}/artifacts")
@Profile("database")
class CompanionSessionArtifactController {

    private final CompanionSessionArtifactService artifactService;

    CompanionSessionArtifactController(CompanionSessionArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CompanionSessionArtifactService.ArtifactListView> replace(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestPart("purpose") String purpose,
            @RequestPart("clientRequestId") UUID clientRequestId,
            @RequestPart("attachments") List<MultipartFile> attachments) {
        return noStore(artifactService.replace(
                appUser,
                sessionId,
                purpose,
                clientRequestId,
                attachments));
    }

    @DeleteMapping
    ResponseEntity<CompanionSessionArtifactService.ArtifactListView> clear(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestParam String purpose) {
        return noStore(artifactService.clear(appUser, sessionId, purpose));
    }

    @GetMapping("/{artifactId}")
    ResponseEntity<byte[]> download(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @PathVariable UUID artifactId) {
        CompanionSessionArtifactService.DownloadedArtifact artifact = artifactService.download(
                appUser,
                sessionId,
                artifactId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(artifact.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.content().length)
                .body(artifact.content());
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
