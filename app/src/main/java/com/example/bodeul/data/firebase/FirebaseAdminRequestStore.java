package com.example.bodeul.data.firebase;

import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 요청 배정과 세션 초기화 저장을 전담한다.
 */
final class FirebaseAdminRequestStore {
    interface RequestMapper {
        AdminRequestOverview findRequestOverview(AdminDashboard dashboard, String requestId);

        boolean containsManager(List<User> managers, String managerUserId);
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final RequestMapper mapper;

    FirebaseAdminRequestStore(FirebaseFirestore firestore, RequestMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    void assignManager(
            AdminDashboard dashboard,
            String requestId,
            String managerUserId,
            CompletionListener listener
    ) {
        AdminRequestOverview targetOverview = mapper.findRequestOverview(dashboard, requestId);
        if (targetOverview == null) {
            listener.onError("배정할 요청을 찾지 못했습니다.");
            return;
        }
        if (!targetOverview.hasLinkedParticipants()) {
            listener.onError("환자와 보호자 계정 연결이 완료된 요청만 배정할 수 있습니다.");
            return;
        }
        if (!targetOverview.hasGuide()) {
            listener.onError("해당 병원과 진료과 가이드가 없어 먼저 가이드를 등록해야 합니다.");
            return;
        }
        if (!mapper.containsManager(dashboard.getAvailableManagers(), managerUserId)) {
            listener.onError("선택한 매니저는 현재 다른 동행을 진행 중입니다.");
            return;
        }

        WriteBatch batch = firestore.batch();
        batch.update(
                firestore.collection("appointmentRequests").document(requestId),
                "status",
                AppointmentStatus.MATCHED.name(),
                "managerUserId",
                managerUserId,
                "updatedAt",
                FieldValue.serverTimestamp()
        );

        DocumentReference sessionReference = firestore.collection("companionSessions")
                .document("session-" + requestId);
        Map<String, Object> sessionDocument = new HashMap<>();
        sessionDocument.put("appointmentRequestId", requestId);
        sessionDocument.put(
                "patientUserId",
                targetOverview.getAppointmentRequest().getPatientUserId()
        );
        sessionDocument.put(
                "guardianUserId",
                targetOverview.getAppointmentRequest().getGuardianUserId()
        );
        sessionDocument.put("managerUserId", managerUserId);
        sessionDocument.put("currentStepOrder", 1);
        sessionDocument.put("currentStatus", SessionStatus.READY.name());
        sessionDocument.put("guardianUpdate", "");
        sessionDocument.put("locationSummary", "");
        sessionDocument.put("fieldPhotoNote", "");
        sessionDocument.put("medicationNote", "");
        sessionDocument.put("pharmacySummary", "");
        sessionDocument.put("prescriptionCollected", false);
        sessionDocument.put("pharmacyCompleted", false);
        sessionDocument.put("medicationGuidanceCompleted", false);
        sessionDocument.put("liveLocationSharingActive", false);
        sessionDocument.put("sharedLocationHistory", new ArrayList<>());
        sessionDocument.put("createdAt", FieldValue.serverTimestamp());
        sessionDocument.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(sessionReference, sessionDocument);

        batch.commit()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("매니저 배정을 저장하지 못했습니다."));
    }
}
