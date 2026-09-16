package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuidePreConsultationChecklistPolicyTest {
    @Test
    public void 세_항목을_모두_확인해야_검토_완료를_저장할_수_있다() {
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, true, false, false, true, true));
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, true, false, true, false, true));
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, true, false, true, true, false));
        assertTrue(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, true, false, true, true, true));
    }

    @Test
    public void 완료됐거나_저장중이거나_입력_불가면_중복_완료할_수_없다() {
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                true, true, false, true, true, true));
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, false, false, true, true, true));
        assertFalse(ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                false, true, true, true, true, true));
    }
}
