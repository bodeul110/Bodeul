package com.bodeul.core.auth;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    default Optional<AppUser> findById(UUID id) {
        return Optional.empty();
    }

    record AppUser(UUID id, String firebaseUid, AppUserRole role) {
    }
}
