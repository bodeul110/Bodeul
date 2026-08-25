package com.bodeul.core.account;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAccountDeletionReadinessServiceTests {

    private static final UUID USER_ID = UUID.fromString("89f9b085-0e02-4a22-9399-f2d019a5d1ba");

    @Test
    void successfulPostgresInventoryStillDoesNotDecideDeletion() {
        AccountDeletionImpactRepository repository = userId -> impact(1, 2, 1, 1);
        var service = new DefaultAccountDeletionReadinessService(Optional.of(repository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID);

        assertThat(result.readOnly()).isTrue();
        assertThat(result.deletionExecuted()).isFalse();
        assertThat(result.decision()).isEqualTo(AccountDeletionReadinessService.Decision.NOT_EVALUATED);
        assertThat(result.complete()).isFalse();
        assertThat(result.sources()).hasSize(5);
        assertThat(result.sources().getFirst().source())
                .isEqualTo(AccountDeletionReadinessService.Source.POSTGRESQL);
        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.COMPLETE);
        assertThat(result.sources().getFirst().counts())
                .containsEntry("appointments", 4L)
                .containsEntry("activeAppointments", 2L)
                .doesNotContainKey("activeLegalHolds");
        assertThat(result.observationCodes()).containsExactly(
                AccountDeletionReadinessService.ObservationCode.ACTIVE_APPOINTMENT_PRESENT,
                AccountDeletionReadinessService.ObservationCode.ACTIVE_SESSION_PRESENT);
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
    }

    @Test
    void missingRepositoryFailsClosedWithoutIdentifiers() {
        var service = new DefaultAccountDeletionReadinessService(Optional.empty());

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID);

        assertThat(result.complete()).isFalse();
        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.sources().getFirst().counts()).isEmpty();
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.observationCodes()).isEmpty();
        assertThat(result.toString()).doesNotContain(USER_ID.toString());
    }

    @Test
    void databaseFailureFailsClosedWithoutRawError() {
        AccountDeletionImpactRepository repository = userId -> {
            throw new DataAccessResourceFailureException("secret database endpoint");
        };
        var service = new DefaultAccountDeletionReadinessService(Optional.of(repository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID);

        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.blockerCodes()).contains(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.toString()).doesNotContain("secret database endpoint");
    }

    @Test
    void missingPostgresProfileIsAnObjectiveBlocker() {
        AccountDeletionImpactRepository repository = userId -> impact(0, 0, 0, 0);
        var service = new DefaultAccountDeletionReadinessService(Optional.of(repository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID);

        assertThat(result.observationCodes()).containsExactly(
                AccountDeletionReadinessService.ObservationCode.POSTGRES_PROFILE_MISSING);
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
    }

    private AccountDeletionImpactRepository.PostgreSqlImpact impact(
            long profileCount,
            long activeAppointmentCount,
            long activeSessionCount,
            long legalHoldCount) {
        return new AccountDeletionImpactRepository.PostgreSqlImpact(
                profileCount,
                4,
                activeAppointmentCount,
                3,
                activeSessionCount,
                2,
                2,
                1,
                8,
                3,
                2,
                1,
                4,
                legalHoldCount);
    }
}
