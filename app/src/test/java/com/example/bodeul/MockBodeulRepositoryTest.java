package com.example.bodeul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 목업 저장소가 신청 생성과 운영 기능을 현재 요구사항대로 처리하는지 검증한다.
 */
public class MockBodeulRepositoryTest {
    @Test
    public void createAppointmentRequest_patientLinksExistingGuardianByEmail() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        assertNotNull(patient);
        assertNotNull(guardian);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                "safe-hospital",
                "orthopedics",
                "2026-04-18 09:30",
                "main-lobby",
                "call-guardian-before-arrival",
                guardian.getName(),
                guardian.getPhone(),
                guardian.getEmail()
        );

        assertNotNull(created);
        assertEquals(AppointmentStatus.REQUESTED, created.getStatus());
        assertEquals(patient.getId(), created.getPatientUserId());
        assertEquals(guardian.getId(), created.getGuardianUserId());
        assertEquals(guardian.getName(), created.getGuardianName());
        assertEquals(guardian.getPhone(), created.getGuardianPhone());

        List<AppointmentRequest> requests = repository.getAppointmentRequestsForUser(
                patient.getId(),
                UserRole.PATIENT
        );
        assertEquals(created.getId(), requests.get(0).getId());
    }

    @Test
    public void createAppointmentRequest_guardianKeepsPatientSnapshotWhenAccountIsMissing() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        assertNotNull(guardian);

        AppointmentRequest created = repository.createAppointmentRequest(
                guardian,
                "safe-hospital",
                "orthopedics",
                "2026-04-19 11:00",
                "front-gate",
                "walk-slowly",
                "new patient",
                "01012341234",
                ""
        );

        assertNotNull(created);
        assertEquals("", created.getPatientUserId());
        assertEquals("new patient", created.getPatientName());
        assertEquals("010-1234-1234", created.getPatientPhone());
        assertEquals(guardian.getId(), created.getGuardianUserId());
        assertEquals(guardian.getName(), created.getGuardianName());
    }

    @Test
    public void getAppointmentRequestsForUser_guardianReturnsGuardianRequestOnly() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        assertNotNull(guardian);

        List<AppointmentRequest> requests = repository.getAppointmentRequestsForUser(
                guardian.getId(),
                guardian.getRole()
        );

        assertEquals(1, requests.size());
        assertEquals("guardian-1", requests.get(0).getGuardianUserId());
    }

    @Test
    public void assignManagerToRequest_createsMatchedRequestAndSession() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User newManager = repository.registerUser(
                "manager-two",
                "new-manager@bodeul.app",
                "010-9999-0001",
                UserRole.MANAGER,
                "bodeul1234"
        );
        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(newManager);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "seoul-internal",
                "neurology",
                "2026-04-20 14:00",
                "main-lobby",
                "manual-match-test"
        );

        assertNotNull(linkedRequest);

        AppointmentRequest assigned = repository.assignManagerToRequest(linkedRequest.getId(), newManager.getId());
        assertNotNull(assigned);
        assertEquals(AppointmentStatus.MATCHED, assigned.getStatus());
        assertEquals(newManager.getId(), assigned.getManagerUserId());
        assertNotNull(repository.findSessionByRequestId(linkedRequest.getId()));
    }

    @Test
    public void saveHospitalGuide_createsGuideFromStepLines() {
        MockBodeulRepository repository = new MockBodeulRepository();

        HospitalGuide savedGuide = repository.saveHospitalGuide(
                "safe-hospital",
                "orthopedics",
                Arrays.asList(
                        "check-id: verify patient and guardian ids",
                        "front-desk: confirm reservation info"
                )
        );

        assertNotNull(savedGuide);
        assertEquals(2, savedGuide.getSteps().size());
        assertEquals("check-id", savedGuide.getSteps().get(0).getTitle());
        assertEquals("orthopedics", repository.getHospitalGuide("safe-hospital", "orthopedics").getDepartmentName());
    }
}
