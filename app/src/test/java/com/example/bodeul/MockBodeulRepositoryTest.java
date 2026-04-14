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

import java.util.List;

/**
 * 목업 신청 저장소가 환자와 보호자 요청을 올바르게 분기하는지 검증한다.
 */
public class MockBodeulRepositoryTest {
    @Test
    public void createAppointmentRequest_patientAddsRequestedItemToTop() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        assertNotNull(patient);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                "보들안심병원",
                "정형외과",
                "2026-04-18 09:30",
                "본관 1층 로비",
                "보호자 연락처는 010-1111-2222입니다."
        );

        assertNotNull(created);
        assertEquals(AppointmentStatus.REQUESTED, created.getStatus());

        List<AppointmentRequest> requests = repository.getAppointmentRequestsForUser(
                patient.getId(),
                UserRole.PATIENT
        );
        assertEquals(created.getId(), requests.get(0).getId());
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
                "신규매니저",
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
                "서울내과병원",
                "신경과",
                "2026-04-20 14:00",
                "본관 1층 로비",
                "수동 매칭 테스트"
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
                "보들안심병원",
                "정형외과",
                java.util.Arrays.asList(
                        "도착 확인: 환자와 보호자 도착 여부를 먼저 확인합니다.",
                        "접수 진행: 창구에서 예약 정보를 확인합니다."
                )
        );

        assertNotNull(savedGuide);
        assertEquals(2, savedGuide.getSteps().size());
        assertEquals("도착 확인", savedGuide.getSteps().get(0).getTitle());
        assertEquals("정형외과", repository.getHospitalGuide("보들안심병원", "정형외과").getDepartmentName());
    }
}
