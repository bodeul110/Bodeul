package com.bodeul.core.consent;

import java.util.Set;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/{appointmentId}/guardian-sharing-consent")
@Profile({"database", "guardian-sharing-consent-test"})
class GuardianSharingConsentController {

    private final GuardianSharingConsentService consentService;

    GuardianSharingConsentController(GuardianSharingConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    ResponseEntity<GuardianSharingConsentService.ConsentView> get(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId) {
        return noStore(consentService.get(appUser, appointmentId));
    }

    @PutMapping
    ResponseEntity<GuardianSharingConsentService.ConsentView> grant(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId,
            @RequestBody GrantConsentRequest request) {
        return noStore(consentService.grant(
                appUser,
                appointmentId,
                request == null ? null : request.scopes(),
                request != null && request.adultPatientConfirmed()));
    }

    @DeleteMapping
    ResponseEntity<GuardianSharingConsentService.ConsentView> revoke(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId) {
        return noStore(consentService.revoke(appUser, appointmentId));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record GrantConsentRequest(
            Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes,
            boolean adultPatientConfirmed) {
    }
}
