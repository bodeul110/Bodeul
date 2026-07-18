package com.bodeul.core.appointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

interface AppUserProfileRepository {

    Optional<AppUserProfile> findById(UUID userId);

    List<AppUserProfile> findByEmail(AppUserRole role, String email);

    List<AppUserProfile> findByPhone(AppUserRole role, String phone);

    record AppUserProfile(
            UUID id,
            AppUserRole role,
            String name,
            String email,
            String phone) {
    }
}
