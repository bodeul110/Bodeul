package com.bodeul.core.auth;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    record AppUser(UUID id, String firebaseUid, AppUserRole role) {
    }
}
