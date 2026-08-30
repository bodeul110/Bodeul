package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Test;

public class CoreApiCompanionSessionClientTest {
    private static final String[] PRODUCT_CODES = {
            "MEETING_CONFIRMATION",
            "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE",
            "VITALS_CHECK",
            "PRE_CONSULTATION",
            "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY",
            "PAYMENT_EVIDENCE",
            "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS",
            "MEDICATION_CONFIRMATION",
            "CARE_COMPLETION",
            "MANAGER_JOURNAL"
    };

    @Test
    public void formatInstantMillis_writesUtcIsoInstant() {
        assertEquals(
                "1970-01-01T00:00:01.234Z",
                CoreApiCompanionSessionClient.formatInstantMillis(1234L)
        );
    }

    @Test
    public void parseInstantMillis_acceptsUtcWithoutFraction() {
        assertEquals(
                1000L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01Z")
        );
    }

    @Test
    public void parseInstantMillis_truncatesLongFractionToMillis() {
        assertEquals(
                1123L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.123456Z")
        );
    }

    @Test
    public void parseInstantMillis_acceptsIsoOffset() {
        assertEquals(
                1000L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T09:00:01+09:00")
        );
    }

    @Test
    public void parseInstantMillis_returnsZeroForMissingOrInvalidValue() {
        assertEquals(0L, CoreApiCompanionSessionClient.parseInstantMillis(null));
        assertEquals(0L, CoreApiCompanionSessionClient.parseInstantMillis(" "));
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis("잘못된 시각")
        );
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.Z")
        );
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.123abcZ")
        );
    }

    @Test
    public void parseSessionSnapshot_preservesZeroOneSevenThirteenAndExtensionSteps()
            throws Exception {
        int[] counts = {0, 1, 7, 13, 14};
        for (int count : counts) {
            CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                    CoreApiCompanionSessionClient.parseSessionSnapshot(
                            createSessionJson(count));

            HospitalGuide guide = snapshot.toHospitalGuide("서울대학교병원", "신경과");

            assertNotNull("steps 키가 있는 응답은 빈 배열도 snapshot으로 보존해야 합니다.", guide);
            assertEquals(count, guide.getSteps().size());
            assertEquals(count, snapshot.getGuideSteps().size());
            for (int index = 0; index < count; index++) {
                GuideStep step = guide.getSteps().get(index);
                String expectedCode = index < PRODUCT_CODES.length
                        ? PRODUCT_CODES[index]
                        : "UNLISTED_EXTENSION";
                String expectedTitle = index < PRODUCT_CODES.length
                        ? "단계 " + (index + 1)
                        : "병원별 추가 단계";
                String expectedDescription = index < PRODUCT_CODES.length
                        ? "설명 " + (index + 1)
                        : "추가 안내";

                assertEquals(index + 1, step.getOrder());
                assertEquals(expectedCode, step.getCode());
                assertEquals(expectedTitle, step.getTitle());
                assertEquals(expectedDescription, step.getDescription());
            }
        }
    }

    @Test
    public void parseSessionSnapshot_preservesUnknownCodeAndServerDecision() throws Exception {
        JSONObject fixture = createSessionJson(14);
        fixture.put("currentStepOrder", 14);
        fixture.put("currentStepCode", "UNLISTED_EXTENSION");
        fixture.put("canAdvance", false);
        fixture.put("blockedReason", "LAST_STEP_REACHED");
        fixture.put("preConsultationConfirmed", true);

        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(fixture);
        HospitalGuide guide = snapshot.toHospitalGuide("서울대학교병원", "신경과");
        CompanionSession session = snapshot.merge(null, "legacy-appointment");

        assertNotNull(guide);
        GuideStep extension = guide.getSteps().get(13);
        assertEquals("UNLISTED_EXTENSION", extension.getCode());
        assertEquals("병원별 추가 단계", extension.getTitle());
        assertEquals("추가 안내", extension.getDescription());
        assertEquals("UNLISTED_EXTENSION", session.getCurrentStepCode());
        assertTrue(session.hasServerAdvanceDecision());
        assertFalse(session.isServerAdvanceAllowed());
        assertEquals("LAST_STEP_REACHED", session.getAdvanceBlockedReason());
        assertTrue(session.isPreConsultationConfirmed());
    }

    @Test
    public void parseSessionSnapshot_preservesLegacySevenStepTextAndNullableRevision()
            throws Exception {
        JSONObject fixture = createSessionJson(0);
        fixture.put("guideId", JSONObject.NULL);
        fixture.put("guideRevision", JSONObject.NULL);
        JSONArray steps = new JSONArray();
        String[] codes = {
                "LEGACY_CORE_PATIENT_CONTACT",
                "LEGACY_CORE_RECEPTION_PREPARATION",
                "LEGACY_CORE_RECEPTION",
                "LEGACY_CORE_CONSULTATION",
                "LEGACY_CORE_PAYMENT",
                "LEGACY_CORE_PHARMACY",
                "LEGACY_CORE_RETURN_AND_CLOSE"
        };
        for (int index = 0; index < codes.length; index++) {
            steps.put(new JSONObject()
                    .put("code", codes[index])
                    .put("order", index + 1)
                    .put("title", "원본 제목 " + (index + 1))
                    .put("description", "원본 설명 " + (index + 1)));
        }
        fixture.put("steps", steps);
        fixture.put("totalStepCount", 7);
        fixture.put("currentStepOrder", 3);
        fixture.put("currentStepCode", "LEGACY_CORE_RECEPTION");
        fixture.put("canAdvance", true);
        fixture.put("blockedReason", JSONObject.NULL);

        HospitalGuide guide = CoreApiCompanionSessionClient
                .parseSessionSnapshot(fixture)
                .toHospitalGuide("서울대학교병원", "신경과");

        assertNotNull(guide);
        assertNull(guide.getRevision());
        assertEquals("원본 제목 3", guide.getSteps().get(2).getTitle());
        assertEquals("원본 설명 3", guide.getSteps().get(2).getDescription());
    }

    @Test
    public void parseSessionSnapshot_usesLegacyFallbackOnlyWhenStepsKeyIsAbsent()
            throws Exception {
        JSONObject fixture = createSessionJson(7);
        fixture.remove("steps");
        fixture.remove("canAdvance");

        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(fixture);

        assertFalse(snapshot.hasGuideSnapshot());
        assertNull(snapshot.toHospitalGuide("서울대학교병원", "신경과"));
        assertFalse(snapshot.merge(null, "legacy-appointment").hasServerAdvanceDecision());
    }

    @Test
    public void managerDashboard_skipsRealtimeWhenCareBoundaryExistsInCompatibilityStatus()
            throws Exception {
        JSONObject fixture = createSessionJson(13)
                .put("currentStatus", "PAYMENT")
                .put("careEndedAt", "2026-08-29T02:00:00Z");

        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(fixture);

        assertTrue(snapshot.hasCareEnded());
        assertFalse(CoreApiManagerRepository.shouldEnrichDashboardWithRealtime(snapshot));
    }

    @Test
    public void managerDashboard_keepsRealtimeForActiveSession() throws Exception {
        JSONObject fixture = createSessionJson(12)
                .put("currentStatus", "PAYMENT")
                .put("careEndedAt", "");

        CoreApiCompanionSessionClient.SessionSnapshot snapshot =
                CoreApiCompanionSessionClient.parseSessionSnapshot(fixture);

        assertFalse(snapshot.hasCareEnded());
        assertTrue(CoreApiManagerRepository.shouldEnrichDashboardWithRealtime(snapshot));
    }

    private JSONObject createSessionJson(int stepCount) throws Exception {
        JSONArray steps = new JSONArray();
        for (int index = 0; index < stepCount; index++) {
            String code = index < PRODUCT_CODES.length
                    ? PRODUCT_CODES[index]
                    : "UNLISTED_EXTENSION";
            steps.put(new JSONObject()
                    .put("code", code)
                    .put("order", index + 1)
                    .put("title", index < PRODUCT_CODES.length
                            ? "단계 " + (index + 1)
                            : "병원별 추가 단계")
                    .put("description", index < PRODUCT_CODES.length
                            ? "설명 " + (index + 1)
                            : "추가 안내"));
        }

        return new JSONObject()
                .put("id", "ae9bcf19-58e4-4e61-8253-06913adbbeb9")
                .put("legacyFirestoreId", "legacy-session")
                .put("appointmentRequestId", "053c5d79-d5e8-4324-9907-a77ead090944")
                .put("managerUserId", "4b2e39de-12de-422c-b6a4-c57a805b1666")
                .put("currentStepOrder", 0)
                .put("totalStepCount", stepCount)
                .put("currentStatus", "MEETING")
                .put("guardianUpdate", "")
                .put("locationSummary", "")
                .put("fieldPhotoNote", "")
                .put("medicationNote", "")
                .put("pharmacySummary", "")
                .put("preConsultationConfirmed", false)
                .put("prescriptionCollected", false)
                .put("pharmacyCompleted", false)
                .put("medicationGuidanceCompleted", false)
                .put("liveLocationSharingActive", false)
                .put("liveLocationSharingStartedAt", "")
                .put("locationAlertStage", "none")
                .put("locationAlertSentAt", "")
                .put("version", 3)
                .put("guideId", "45bd0403-59a7-449a-90f6-fae10c79da30")
                .put("guideRevision", 4)
                .put("steps", steps)
                .put("currentStepCode", JSONObject.NULL)
                .put("canAdvance", stepCount > 0)
                .put("blockedReason", stepCount > 0 ? JSONObject.NULL : "GUIDE_NOT_READY");
    }
}
