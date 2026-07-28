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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/companion-sessions/{sessionId}")
@Profile({"database", "companion-realtime-test"})
class CompanionRealtimeController {

    private final CompanionRealtimeService realtimeService;
    private final CompanionAttachmentService attachmentService;

    CompanionRealtimeController(
            CompanionRealtimeService realtimeService,
            CompanionAttachmentService attachmentService) {
        this.realtimeService = realtimeService;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/realtime")
    ResponseEntity<CompanionRealtimeService.RealtimeSnapshotView> getSnapshot(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId) {
        return noStore(realtimeService.getSnapshot(appUser, sessionId));
    }

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CompanionRealtimeService.ChatMessageView> postMessage(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody PostMessageRequest request) {
        return noStore(realtimeService.postMessage(
                appUser,
                sessionId,
                request == null ? null : request.toCommand()));
    }

    @PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CompanionRealtimeService.ChatMessageView> postMessageWithAttachments(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestParam UUID clientMessageId,
            @RequestParam(defaultValue = "") String body,
            @RequestPart(name = "attachments", required = false) List<MultipartFile> attachments) {
        return noStore(attachmentService.postMessage(
                appUser,
                sessionId,
                clientMessageId,
                body,
                attachments));
    }

    @GetMapping("/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadAttachment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @PathVariable UUID attachmentId) {
        CompanionAttachmentService.DownloadedAttachment attachment = attachmentService.download(
                appUser,
                sessionId,
                attachmentId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.content().length)
                .body(attachment.content());
    }

    @PutMapping("/read-receipt")
    ResponseEntity<CompanionRealtimeService.ReadReceiptView> updateReadReceipt(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody UpdateReadReceiptRequest request) {
        return noStore(realtimeService.updateReadReceipt(
                appUser,
                sessionId,
                request == null ? null : request.lastReadMessageId()));
    }

    @PostMapping("/locations")
    ResponseEntity<CompanionRealtimeService.LocationView> postLocation(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody PostLocationRequest request) {
        return noStore(realtimeService.postLocation(
                appUser,
                sessionId,
                request == null ? null : request.toCommand()));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record PostMessageRequest(
            UUID clientMessageId,
            String body,
            List<AttachmentRequest> attachments) {

        CompanionRealtimeService.PostMessageCommand toCommand() {
            return new CompanionRealtimeService.PostMessageCommand(
                    clientMessageId,
                    body,
                    attachments == null
                            ? List.of()
                            : attachments.stream().map(AttachmentRequest::toCommand).toList());
        }
    }

    record AttachmentRequest(
            String storagePath,
            String fileName,
            String contentType,
            Long sizeBytes) {

        CompanionRealtimeService.AttachmentCommand toCommand() {
            return new CompanionRealtimeService.AttachmentCommand(
                    storagePath,
                    fileName,
                    contentType,
                    sizeBytes);
        }
    }

    record UpdateReadReceiptRequest(UUID lastReadMessageId) {
    }

    record PostLocationRequest(
            UUID clientLocationId,
            Double latitude,
            Double longitude,
            String capturedAt) {

        CompanionRealtimeService.PostLocationCommand toCommand() {
            return new CompanionRealtimeService.PostLocationCommand(
                    clientLocationId,
                    latitude,
                    longitude,
                    capturedAt);
        }
    }
}
