package com.bodeul.core.consent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcGuardianSharingConsentRepository implements GuardianSharingConsentRepository {

    private static final String RETURNING_COLUMNS = """
            returning id, appointment_request_id, patient_user_id, guardian_user_id,
                      scopes::text as scopes_json, policy_version, granted_by_user_id,
                      adult_self_declared_at, granted_at, expires_at,
                      care_ended_at, expiry_finalized,
                      revoked_by_user_id, revoked_at, version
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    JdbcGuardianSharingConsentRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsentSettings getSettings() {
        return jdbcClient.sql("""
                        select policy_version, location_sharing_enabled
                        from bodeul.guardian_sharing_consent_settings
                        where singleton
                        """)
                .query((resultSet, rowNumber) -> new ConsentSettings(
                        resultSet.getString("policy_version"),
                        resultSet.getBoolean("location_sharing_enabled")))
                .single();
    }

    @Override
    public Optional<AppointmentContext> findAppointment(UUID appointmentRequestId) {
        return jdbcClient.sql("""
                        select id, patient_user_id, guardian_user_id, appointment_at, status
                        from bodeul.appointment_requests
                        where id = :appointmentRequestId
                        """)
                .param("appointmentRequestId", appointmentRequestId)
                .query((resultSet, rowNumber) -> new AppointmentContext(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("patient_user_id", UUID.class),
                        resultSet.getObject("guardian_user_id", UUID.class),
                        resultSet.getTimestamp("appointment_at").toInstant(),
                        resultSet.getString("status")))
                .optional();
    }

    @Override
    public Optional<AdultPatientGuardianSharingPolicy.Grant> findByAppointmentId(
            UUID appointmentRequestId) {
        return jdbcClient.sql("""
                        select id, appointment_request_id, patient_user_id, guardian_user_id,
                               scopes::text as scopes_json, policy_version, granted_by_user_id,
                               adult_self_declared_at, granted_at, expires_at,
                               care_ended_at, expiry_finalized,
                               revoked_by_user_id, revoked_at, version
                        from bodeul.guardian_sharing_consents
                        where appointment_request_id = :appointmentRequestId
                        """)
                .param("appointmentRequestId", appointmentRequestId)
                .query(this::mapGrant)
                .optional();
    }

    @Override
    public boolean isExpiryFinalized(UUID appointmentRequestId) {
        return jdbcClient.sql("""
                        select expiry_finalized
                        from bodeul.guardian_sharing_consents
                        where appointment_request_id = :appointmentRequestId
                        """)
                .param("appointmentRequestId", appointmentRequestId)
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    @Override
    public AdultPatientGuardianSharingPolicy.Grant grant(
            AdultPatientGuardianSharingPolicy.Grant requestedGrant) {
        return jdbcClient.sql("""
                        insert into bodeul.guardian_sharing_consents (
                            id, appointment_request_id, patient_user_id, guardian_user_id,
                            scopes, policy_version, granted_by_user_id, adult_self_declared_at,
                            granted_at, expires_at, care_ended_at, expiry_finalized,
                            revoked_by_user_id, revoked_at, version
                        ) values (
                            :id, :appointmentRequestId, :patientUserId, :guardianUserId,
                            cast(:scopesJson as jsonb), :policyVersion, :grantedByUserId,
                            :adultSelfDeclaredAt, :grantedAt, :expiresAt, null, false,
                            null, null, 0
                        )
                        on conflict (appointment_request_id) do update
                        set patient_user_id = excluded.patient_user_id,
                            guardian_user_id = excluded.guardian_user_id,
                            scopes = excluded.scopes,
                            policy_version = excluded.policy_version,
                            granted_by_user_id = excluded.granted_by_user_id,
                            adult_self_declared_at = excluded.adult_self_declared_at,
                            granted_at = excluded.granted_at,
                            expires_at = excluded.expires_at,
                            care_ended_at = null,
                            expiry_finalized = false,
                            revoked_by_user_id = null,
                            revoked_at = null,
                            version = bodeul.guardian_sharing_consents.version + 1,
                            updated_at = now()
                        """ + RETURNING_COLUMNS)
                .param("id", requestedGrant.id())
                .param("appointmentRequestId", requestedGrant.appointmentRequestId())
                .param("patientUserId", requestedGrant.patientUserId())
                .param("guardianUserId", requestedGrant.guardianUserId())
                .param("scopesJson", writeScopes(requestedGrant.scopes()))
                .param("policyVersion", requestedGrant.policyVersion())
                .param("grantedByUserId", requestedGrant.grantedByUserId())
                .param("adultSelfDeclaredAt", requestedGrant.grantedAt())
                .param("grantedAt", requestedGrant.grantedAt())
                .param("expiresAt", requestedGrant.expiresAt())
                .query(this::mapGrant)
                .single();
    }

    @Override
    public Optional<AdultPatientGuardianSharingPolicy.Grant> revoke(
            UUID appointmentRequestId,
            UUID actorUserId,
            Instant revokedAt,
            long expectedVersion) {
        return jdbcClient.sql("""
                        update bodeul.guardian_sharing_consents
                        set revoked_by_user_id = :actorUserId,
                            revoked_at = :revokedAt,
                            version = version + 1,
                            updated_at = now()
                        where appointment_request_id = :appointmentRequestId
                          and patient_user_id = :actorUserId
                          and revoked_at is null
                          and version = :expectedVersion
                        """ + RETURNING_COLUMNS)
                .param("actorUserId", actorUserId)
                .param("revokedAt", revokedAt)
                .param("appointmentRequestId", appointmentRequestId)
                .param("expectedVersion", expectedVersion)
                .query(this::mapGrant)
                .optional();
    }

    @Override
    public void finalizeExpiryAfterCareBoundary(
            UUID appointmentRequestId,
            Instant careEndedAt) {
        jdbcClient.sql("""
                        update bodeul.guardian_sharing_consents
                        set care_ended_at = :careEndedAt,
                            expires_at = :careEndedAt + interval '7 days',
                            expiry_finalized = true,
                            version = version + 1,
                            updated_at = now()
                        where appointment_request_id = :appointmentRequestId
                          and not expiry_finalized
                        """)
                .param("appointmentRequestId", appointmentRequestId)
                .param("careEndedAt", careEndedAt)
                .update();
    }

    @Override
    public void appendEvent(
            AdultPatientGuardianSharingPolicy.Grant grant,
            EventAction action,
            UUID actorUserId,
            Instant occurredAt) {
        jdbcClient.sql("""
                        insert into bodeul.guardian_sharing_consent_events (
                            consent_id, appointment_request_id, patient_user_id, guardian_user_id,
                            action, scopes, policy_version, actor_user_id,
                            adult_self_declared_at, occurred_at, consent_version
                        ) values (
                            :consentId, :appointmentRequestId, :patientUserId, :guardianUserId,
                            :action, cast(:scopesJson as jsonb), :policyVersion, :actorUserId,
                            :adultSelfDeclaredAt, :occurredAt, :consentVersion
                        )
                        """)
                .param("consentId", grant.id())
                .param("appointmentRequestId", grant.appointmentRequestId())
                .param("patientUserId", grant.patientUserId())
                .param("guardianUserId", grant.guardianUserId())
                .param("action", action.name())
                .param("scopesJson", writeScopes(grant.scopes()))
                .param("policyVersion", grant.policyVersion())
                .param("actorUserId", actorUserId)
                .param("adultSelfDeclaredAt", grant.grantedAt())
                .param("occurredAt", occurredAt)
                .param("consentVersion", grant.version())
                .update();
    }

    private AdultPatientGuardianSharingPolicy.Grant mapGrant(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new AdultPatientGuardianSharingPolicy.Grant(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("appointment_request_id", UUID.class),
                resultSet.getObject("patient_user_id", UUID.class),
                resultSet.getObject("guardian_user_id", UUID.class),
                readScopes(resultSet.getString("scopes_json")),
                resultSet.getString("policy_version"),
                resultSet.getObject("granted_by_user_id", UUID.class),
                resultSet.getTimestamp("granted_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getObject("revoked_by_user_id", UUID.class),
                resultSet.getTimestamp("revoked_at") == null
                        ? null
                        : resultSet.getTimestamp("revoked_at").toInstant(),
                resultSet.getLong("version"));
    }

    private String writeScopes(Set<AdultPatientGuardianSharingPolicy.InformationScope> scopes) {
        try {
            return objectMapper.writeValueAsString(
                    scopes.stream().map(Enum::name).sorted().toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("동의 범위를 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    private Set<AdultPatientGuardianSharingPolicy.InformationScope> readScopes(String value) {
        try {
            String[] names = objectMapper.readValue(value, String[].class);
            EnumSet<AdultPatientGuardianSharingPolicy.InformationScope> scopes = EnumSet.noneOf(
                    AdultPatientGuardianSharingPolicy.InformationScope.class);
            Arrays.stream(names)
                    .map(AdultPatientGuardianSharingPolicy.InformationScope::valueOf)
                    .forEach(scopes::add);
            return Set.copyOf(scopes);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DataRetrievalFailureException("저장된 보호자 동의 범위를 해석하지 못했습니다.", exception);
        }
    }
}
