package com.bodeul.core.account;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
class AccountDeletionReadinessController {

    private final AccountDeletionReadinessService readinessService;

    AccountDeletionReadinessController(AccountDeletionReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/deletion-readiness")
    ResponseEntity<AccountDeletionReadinessService.ReadinessResult> getReadiness(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(readinessService.inspect(appUser.id()));
    }
}
