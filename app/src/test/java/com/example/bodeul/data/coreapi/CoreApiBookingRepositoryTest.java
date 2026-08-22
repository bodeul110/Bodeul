package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.HospitalGuide;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CoreApiBookingRepositoryTest {
    @Test
    public void resolveHospitalGuide_usesFallbackForOldResponseWithoutLegacyGuide()
            throws Exception {
        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(createOldSessionJson());

        HospitalGuide guide = CoreApiBookingRepository.resolveHospitalGuide(
                createRequest(),
                createCoreOnlyDetail(),
                snapshot);

        assertFalse(snapshot.hasGuideSnapshot());
        assertEquals("default-guide", guide.getId());
        assertEquals(7, guide.getSteps().size());
        assertEquals("LEGACY_CORE_PATIENT_CONTACT", guide.getSteps().get(0).getCode());
    }

    @Test
    public void resolveHospitalGuide_preservesEmptyServerSnapshot() throws Exception {
        JSONObject fixture = createOldSessionJson().put("steps", new JSONArray());
        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(fixture);

        HospitalGuide guide = CoreApiBookingRepository.resolveHospitalGuide(
                createRequest(),
                createCoreOnlyDetail(),
                snapshot);

        assertTrue(snapshot.hasGuideSnapshot());
        assertTrue(guide.getSteps().isEmpty());
    }

    private JSONObject createOldSessionJson() throws Exception {
        return new JSONObject()
                .put("id", "ae9bcf19-58e4-4e61-8253-06913adbbeb9")
                .put("appointmentRequestId", "053c5d79-d5e8-4324-9907-a77ead090944")
                .put("managerUserId", "4b2e39de-12de-422c-b6a4-c57a805b1666")
                .put("currentStepOrder", 0)
                .put("currentStatus", "READY")
                .put("version", 1);
    }

    private AppointmentRequest createRequest() {
        return new AppointmentRequest(
                "appointment-id",
                "patient-id",
                "guardian-id",
                "서울대학교병원",
                "신경과",
                "2026-08-22T10:00:00Z",
                "병원 로비",
                "",
                AppointmentStatus.IN_PROGRESS,
                "manager-id");
    }

    private AppointmentRequestDetail createCoreOnlyDetail() {
        return new AppointmentRequestDetail(
                createRequest(),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
