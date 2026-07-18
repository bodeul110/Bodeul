package com.bodeul.core.session;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companion-sessions/{sessionId}")
@Profile({"database", "companion-realtime-test"})
class CompanionRealtimeController {

    private final CompanionRealtimeService realtimeService;

    CompanionRealtimeController(CompanionRealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @GetMapping("/realtime")
    ResponseEntity<CompanionRealtimeService.RealtimeSnapshotView> getSnapshot(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId) {
        return noStore(realtimeService.getSnapshot(appUser, sessionId));
    }

    @PostMapping("/messages")
    ResponseEntity<CompanionRealtimeService.ChatMessageView> postMessage(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID sessionId,
            @RequestBody PostMessageRequest request) {
        return noStore(realtimeService.postMessage(
                appUser,
                sessionId,
                request == null ? null : request.toCommand()));
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
