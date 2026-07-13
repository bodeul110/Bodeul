package com.bodeul.core.auth;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @GetMapping("/me")
    ResponseEntity<AuthenticatedUserResponse> getAuthenticatedUser(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AuthenticatedUserResponse(appUser.id(), appUser.role()));
    }

    record AuthenticatedUserResponse(UUID userId, AppUserRole role) {
    }
}
