package com.bodeul.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseCompanionNotificationListenerTests {

    private static final UUID SESSION_ID = UUID.fromString("1153394e-9106-4cd8-9339-c72ca0559485");
    private static final UUID APPOINTMENT_ID = UUID.fromString("a04cd0b6-4bda-4079-b663-85a8a8822609");
    private static final UUID RECIPIENT_ID = UUID.fromString("ac43f31b-5709-40b5-987e-449e9ed3baf8");

    @Test
    void disabledLegacyManagerLocationSkipsFcmRecipientResolution() {
        CountingAppUserRepository repository = new CountingAppUserRepository();
        FirebaseCompanionNotificationListener listener = listener(repository, false);

        listener.onLocationAlert(locationAlert());

        assertThat(repository.findByIdCount).isZero();
    }

    @Test
    void enabledLegacyManagerLocationKeepsLocationNotificationFlow() {
        CountingAppUserRepository repository = new CountingAppUserRepository();
        FirebaseCompanionNotificationListener listener = listener(repository, true);

        listener.onLocationAlert(locationAlert());

        assertThat(repository.findByIdCount).isEqualTo(1);
    }

    @Test
    void disabledLegacyManagerLocationDoesNotSuppressChatNotifications() {
        CountingAppUserRepository repository = new CountingAppUserRepository();
        FirebaseCompanionNotificationListener listener = listener(repository, false);

        listener.onChatMessage(new CompanionChatMessageCreatedEvent(
                SESSION_ID,
                APPOINTMENT_ID,
                "MANAGER",
                Instant.parse("2026-09-03T00:00:00Z"),
                List.of(RECIPIENT_ID)));

        assertThat(repository.findByIdCount).isEqualTo(1);
    }

    private FirebaseCompanionNotificationListener listener(
            AppUserRepository repository,
            boolean legacyManagerLocationEnabled) {
        CompanionSessionProperties properties = new CompanionSessionProperties();
        properties.setLegacyManagerLocationEnabled(legacyManagerLocationEnabled);
        return new FirebaseCompanionNotificationListener(repository, "bodeul-test", properties);
    }

    private CompanionLocationAlertChangedEvent locationAlert() {
        return new CompanionLocationAlertChangedEvent(
                SESSION_ID,
                APPOINTMENT_ID,
                "hospital_near",
                List.of(RECIPIENT_ID));
    }

    private static final class CountingAppUserRepository implements AppUserRepository {

        private int findByIdCount;

        @Override
        public Optional<AppUser> findByFirebaseUid(String firebaseUid) {
            return Optional.empty();
        }

        @Override
        public Optional<AppUser> findById(UUID id) {
            findByIdCount++;
            return Optional.empty();
        }
    }
}
