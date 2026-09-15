package com.example.bodeul.ui.manager;

/** 화면 체크는 임시 상태이고, 필수 세 항목 확인 후에만 서버의 완료 플래그를 저장한다. */
final class ManagerGuidePreConsultationChecklistPolicy {
    private ManagerGuidePreConsultationChecklistPolicy() {
    }

    static boolean canConfirm(
            boolean alreadyConfirmed,
            boolean inputsEnabled,
            boolean mutationInFlight,
            boolean medicationChecked,
            boolean guardianChecked,
            boolean documentsChecked
    ) {
        return !alreadyConfirmed && inputsEnabled && !mutationInFlight
                && medicationChecked && guardianChecked && documentsChecked;
    }
}
