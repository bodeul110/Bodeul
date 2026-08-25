package com.bodeul.core.account;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
class DefaultAccountDeletionReadinessService implements AccountDeletionReadinessService {

    private final Optional<AccountDeletionImpactRepository> impactRepository;

    DefaultAccountDeletionReadinessService(Optional<AccountDeletionImpactRepository> impactRepository) {
        this.impactRepository = impactRepository;
    }

    @Override
    public ReadinessResult inspect(UUID userId) {
        Set<ObservationCode> observationCodes = new LinkedHashSet<>();
        Set<BlockerCode> blockerCodes = new LinkedHashSet<>();
        SourceInventory postgres = inspectPostgres(userId, observationCodes, blockerCodes);
        List<SourceInventory> sources = List.of(
                postgres,
                notEvaluated(Source.FIRESTORE),
                notEvaluated(Source.STORAGE),
                notEvaluated(Source.FIREBASE_AUTH),
                notEvaluated(Source.BACKUP));

        blockerCodes.add(BlockerCode.INVENTORY_INCOMPLETE);
        return new ReadinessResult(
                true,
                false,
                Decision.NOT_EVALUATED,
                false,
                sources,
                List.copyOf(observationCodes),
                List.copyOf(blockerCodes));
    }

    private SourceInventory inspectPostgres(
            UUID userId,
            Set<ObservationCode> observationCodes,
            Set<BlockerCode> blockerCodes) {
        if (impactRepository.isEmpty()) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.POSTGRESQL, SourceStatus.ERROR, Map.of());
        }

        final AccountDeletionImpactRepository.PostgreSqlImpact impact;
        try {
            impact = impactRepository.orElseThrow().inspect(userId);
        } catch (DataAccessException exception) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.POSTGRESQL, SourceStatus.ERROR, Map.of());
        }

        addPostgresObservations(impact, observationCodes);
        return new SourceInventory(Source.POSTGRESQL, SourceStatus.COMPLETE, counts(impact));
    }

    private void addPostgresObservations(
            AccountDeletionImpactRepository.PostgreSqlImpact impact,
            Set<ObservationCode> observationCodes) {
        if (impact.profileCount() == 0) {
            observationCodes.add(ObservationCode.POSTGRES_PROFILE_MISSING);
        }
        if (impact.activeAppointmentCount() > 0) {
            observationCodes.add(ObservationCode.ACTIVE_APPOINTMENT_PRESENT);
        }
        if (impact.activeCompanionSessionCount() > 0) {
            observationCodes.add(ObservationCode.ACTIVE_SESSION_PRESENT);
        }
    }

    private Map<String, Long> counts(AccountDeletionImpactRepository.PostgreSqlImpact impact) {
        return Map.ofEntries(
                Map.entry("profiles", impact.profileCount()),
                Map.entry("appointments", impact.appointmentCount()),
                Map.entry("activeAppointments", impact.activeAppointmentCount()),
                Map.entry("companionSessions", impact.companionSessionCount()),
                Map.entry("activeCompanionSessions", impact.activeCompanionSessionCount()),
                Map.entry("sessionReports", impact.sessionReportCount()),
                Map.entry("appointmentFollowUps", impact.appointmentFollowUpCount()),
                Map.entry("assignmentAudits", impact.assignmentAuditCount()),
                Map.entry("relatedChatMessages", impact.relatedChatMessageCount()),
                Map.entry("sentChatMessages", impact.sentChatMessageCount()),
                Map.entry("relatedChatAttachments", impact.relatedChatAttachmentCount()),
                Map.entry("relatedChatReadReceipts", impact.relatedChatReadReceiptCount()),
                Map.entry("relatedLocations", impact.relatedLocationCount()));
    }

    private SourceInventory notEvaluated(Source source) {
        return new SourceInventory(source, SourceStatus.NOT_EVALUATED, Map.of());
    }
}
