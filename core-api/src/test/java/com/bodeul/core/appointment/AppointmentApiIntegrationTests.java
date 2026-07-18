package com.bodeul.core.appointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppCheckTokenVerifier;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bodeul.app-check.mode=observe")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "appointment-test"})
@Import(AppointmentApiIntegrationTests.AppointmentApiTestConfiguration.class)
class AppointmentApiIntegrationTests {

    private static final UUID USER_ID = UUID.fromString("aab5363c-4021-4390-af5d-4f9259796c77");
    private static final UUID APPOINTMENT_ID = UUID.fromString("df1f95fd-5558-41bc-99e9-c5a3981661b9");
    private static final UUID MANAGER_ID = UUID.fromString("3da53272-577e-4d5b-9c86-f1cf7a787e5b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableAppointmentService appointmentService;

    @BeforeEach
    void reset() {
        appointmentService.reset();
    }

    @Test
    void appointmentListRequiresFirebaseAuthentication() throws Exception {
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void authenticatedUserCanReadOwnAppointments() throws Exception {
        appointmentService.result = appointment();

        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointments[0].id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.appointments[0].managerName").value("매니저 사용자"))
                .andExpect(jsonPath("$.appointments[0].version").value(2));

        assertThat(appointmentService.lastUser.id()).isEqualTo(USER_ID);
    }

    @Test
    void createReturns201AndPassesTheIdempotencyKey() throws Exception {
        appointmentService.result = appointment();
        UUID clientRequestId = UUID.fromString("1462354f-7162-42c0-9e40-d66b6d73b0f4");

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(validCreateJson(clientRequestId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/appointments/" + APPOINTMENT_ID))
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()));

        assertThat(appointmentService.lastCreateCommand.clientRequestId()).isEqualTo(clientRequestId);
        assertThat(appointmentService.lastCreateCommand.draft().hospitalName()).isEqualTo("서울대학교병원");
    }

    @Test
    void updatePassesVersionAndPathId() throws Exception {
        appointmentService.result = appointment();

        mockMvc.perform(put("/api/appointments/{appointmentId}", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(validUpdateJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(appointmentService.lastAppointmentId).isEqualTo(APPOINTMENT_ID);
        assertThat(appointmentService.lastUpdateCommand.version()).isEqualTo(2);
    }

    @Test
    void invalidPathIdReturns400() throws Exception {
        mockMvc.perform(get("/api/appointments/not-a-uuid")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_appointment_request"));
    }

    @Test
    void servicePermissionFailureReturns403() throws Exception {
        appointmentService.failure = AppointmentException.permissionDenied();

        mockMvc.perform(get("/api/appointments/{appointmentId}", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("appointment_permission_denied"));
    }

    @Test
    void databaseFailureReturns503WithoutInternalMessage() throws Exception {
        appointmentService.failure = new DataAccessResourceFailureException("secret database detail");

        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("appointment_database_failure"))
                .andExpect(jsonPath("$.message").value("예약 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_appointment_request"));
    }

    @Test
    void authenticatedParticipantCanReadFollowUp() throws Exception {
        appointmentService.followUpResult = followUp();

        mockMvc.perform(get("/api/appointments/{appointmentId}/follow-up", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointmentRequestId").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.reviewRatingCode").value("good"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void followUpPatchPassesVersionAndPartialFields() throws Exception {
        appointmentService.followUpResult = followUp();

        mockMvc.perform(patch("/api/appointments/{appointmentId}/follow-up", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "version": 1,
                                  "settlementFollowUpStatus": "NEEDS_HELP",
                                  "settlementFollowUpNote": "결제 내역 확인 요청"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(appointmentService.lastAppointmentId).isEqualTo(APPOINTMENT_ID);
        assertThat(appointmentService.lastFollowUpCommand.version()).isEqualTo(1);
        assertThat(appointmentService.lastFollowUpCommand.settlementStatus())
                .isEqualTo("NEEDS_HELP");
    }

    private String validCreateJson(UUID clientRequestId) {
        return """
                {
                  "clientRequestId": "%s",
                  "linkedParticipantName": "보호자 사용자",
                  "linkedParticipantPhone": "010-9876-5432",
                  "linkedParticipantEmail": "guardian@example.com",
                  "patientConditionSummary": "휠체어 이동 지원 필요",
                  "medicationSummary": "아침 약 복용",
                  "hospitalName": "서울대학교병원",
                  "departmentName": "내과",
                  "hospitalLatitude": 37.5796,
                  "hospitalLongitude": 126.9990,
                  "appointmentAt": "2026-12-20 10:30",
                  "meetingPlace": "본관 1층",
                  "specialNotes": "진료 20분 전 도착",
                  "mobilitySupportCode": "WHEELCHAIR",
                  "tripTypeCode": "ROUND_TRIP",
                  "managerGenderPreferenceCode": "ANY",
                  "paymentMethodCode": "CARD",
                  "couponCode": "FAMILY"
                }
                """.formatted(clientRequestId);
    }

    private String validUpdateJson(long version) {
        return validCreateJson(UUID.randomUUID())
                .replaceFirst("\\{", "{\n  \"version\": " + version + ",")
                .replaceFirst("\\s*\"clientRequestId\"[^,]+,", "");
    }

    private AppointmentService.AppointmentView appointment() {
        return new AppointmentService.AppointmentView(
                APPOINTMENT_ID,
                "",
                USER_ID,
                null,
                MANAGER_ID,
                "매니저 사용자",
                "010-5555-7777",
                "manager@example.com",
                "환자 사용자",
                "010-1234-5678",
                "patient@example.com",
                "보호자 사용자",
                "010-9876-5432",
                "guardian@example.com",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "",
                "",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                "REQUESTED",
                69_000,
                0,
                0,
                69_000,
                "CARD",
                "NONE",
                "PENDING",
                "",
                "",
                "",
                2);
    }

    private AppointmentService.AppointmentFollowUpView followUp() {
        return new AppointmentService.AppointmentFollowUpView(
                APPOINTMENT_ID,
                "good",
                "2026-07-18T00:00:00Z",
                "NEEDS_HELP",
                "결제 내역 확인 요청",
                "2026-07-18T00:01:00Z",
                "",
                "",
                2);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AppointmentApiTestConfiguration {

        @Bean
        @Primary
        FirebaseTokenVerifier appointmentTestFirebaseTokenVerifier() {
            return idToken -> new FirebaseTokenVerifier.VerifiedToken("firebase-user-1");
        }

        @Bean
        @Primary
        AppCheckTokenVerifier appointmentTestAppCheckTokenVerifier() {
            return token -> new AppCheckTokenVerifier.VerifiedToken("test-app");
        }

        @Bean
        AppUserRepository appointmentTestAppUserRepository() {
            return firebaseUid -> Optional.of(new AppUserRepository.AppUser(
                    USER_ID,
                    firebaseUid,
                    AppUserRole.PATIENT));
        }

        @Bean
        MutableAppointmentService appointmentService() {
            return new MutableAppointmentService();
        }
    }

    static final class MutableAppointmentService implements AppointmentService {
        private AppointmentView result;
        private RuntimeException failure;
        private AppUserRepository.AppUser lastUser;
        private UUID lastAppointmentId;
        private CreateAppointmentCommand lastCreateCommand;
        private UpdateAppointmentCommand lastUpdateCommand;
        private AppointmentFollowUpView followUpResult;
        private UpdateAppointmentFollowUpCommand lastFollowUpCommand;

        @Override
        public List<AppointmentView> getMyAppointments(AppUserRepository.AppUser appUser) {
            recordCall(appUser, null);
            return List.of(result);
        }

        @Override
        public AppointmentView getAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId) {
            recordCall(appUser, appointmentId);
            return result;
        }

        @Override
        public AppointmentView createAppointment(
                AppUserRepository.AppUser appUser,
                CreateAppointmentCommand command) {
            recordCall(appUser, null);
            lastCreateCommand = command;
            return result;
        }

        @Override
        public AppointmentView updateAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                UpdateAppointmentCommand command) {
            recordCall(appUser, appointmentId);
            lastUpdateCommand = command;
            return result;
        }

        @Override
        public AppointmentView cancelAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                long version) {
            recordCall(appUser, appointmentId);
            return result;
        }

        @Override
        public AppointmentFollowUpView getAppointmentFollowUp(
                AppUserRepository.AppUser appUser,
                UUID appointmentId) {
            recordCall(appUser, appointmentId);
            return followUpResult;
        }

        @Override
        public AppointmentFollowUpView updateAppointmentFollowUp(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                UpdateAppointmentFollowUpCommand command) {
            recordCall(appUser, appointmentId);
            lastFollowUpCommand = command;
            return followUpResult;
        }

        void reset() {
            result = null;
            failure = null;
            lastUser = null;
            lastAppointmentId = null;
            lastCreateCommand = null;
            lastUpdateCommand = null;
            followUpResult = null;
            lastFollowUpCommand = null;
        }

        private void recordCall(AppUserRepository.AppUser appUser, UUID appointmentId) {
            lastUser = appUser;
            lastAppointmentId = appointmentId;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
