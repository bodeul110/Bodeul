package com.example.bodeul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionStatus;
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
    public void updateAppointmentRequest_requestedOwnerCanRewriteScheduleAndContact() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        assertNotNull(patient);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                "처음병원",
                "안과",
                "2026-04-24 11:20",
                "본관 1층",
                "초기 메모",
                "새 보호자",
                "01011112222",
                ""
        );
        assertNotNull(created);

        AppointmentRequest updated = repository.updateAppointmentRequest(
                patient,
                created.getId(),
                "서울튼튼병원",
                "정형외과",
                "2026-04-25 15:40",
                "본관 2층 안내데스크",
                "수정 테스트 메모",
                "새 보호자",
                "01022223333",
                ""
        );

        assertNotNull(updated);
        assertEquals("서울튼튼병원", updated.getHospitalName());
        assertEquals("정형외과", updated.getDepartmentName());
        assertEquals("2026-04-25 15:40", updated.getAppointmentAt());
        assertEquals("본관 2층 안내데스크", updated.getMeetingPlace());
        assertEquals("새 보호자", updated.getGuardianName());
        assertEquals("010-2222-3333", updated.getGuardianPhone());
        assertEquals(AppointmentStatus.REQUESTED, updated.getStatus());
    }

    @Test
    public void cancelAppointmentRequest_requestedOwnerCanCancelOwnRequest() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        assertNotNull(patient);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                "취소병원",
                "내과",
                "2026-04-26 10:10",
                "본관 3층",
                "",
                "새 보호자",
                "01033334444",
                ""
        );
        assertNotNull(created);

        AppointmentRequest canceled = repository.cancelAppointmentRequest(patient, created.getId());

        assertNotNull(canceled);
        assertEquals(AppointmentStatus.CANCELED, canceled.getStatus());
    }

    @Test
    public void cancelAppointmentRequest_matchedOwnerCancelsLinkedSessionTogether() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "manager-cancel-test",
                "manager-cancel-test@bodeul.app",
                "010-9999-0002",
                UserRole.MANAGER,
                "bodeul1234"
        );
        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "취소연결병원",
                "재활의학과",
                "2026-04-27 13:20",
                "본관 1층 로비",
                "배정 후 취소 테스트"
        );
        assertNotNull(linkedRequest);

        AppointmentRequest matched = repository.assignManagerToRequest(linkedRequest.getId(), manager.getId());
        assertNotNull(matched);
        assertEquals(AppointmentStatus.MATCHED, matched.getStatus());

        AppointmentRequest canceled = repository.cancelAppointmentRequest(patient, linkedRequest.getId());
        CompanionSession session = repository.findSessionByRequestId(linkedRequest.getId());

        assertNotNull(canceled);
        assertNotNull(session);
        assertEquals(AppointmentStatus.CANCELED, canceled.getStatus());
        assertEquals(SessionStatus.CANCELED, session.getStatus());
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

    @Test
    public void deleteHospitalGuide_existingGuideRemovesItFromRepository() {
        MockBodeulRepository repository = new MockBodeulRepository();

        HospitalGuide savedGuide = repository.saveHospitalGuide(
                "삭제병원",
                "이비인후과",
                Arrays.asList(
                        "접수: 예약 정보를 확인합니다.",
                        "진료: 보호자 공유 내용을 남깁니다."
                )
        );

        assertNotNull(savedGuide);
        assertEquals(true, repository.deleteHospitalGuide(savedGuide.getId()));
        assertEquals(null, repository.getHospitalGuide("삭제병원", "이비인후과"));
    }
}
