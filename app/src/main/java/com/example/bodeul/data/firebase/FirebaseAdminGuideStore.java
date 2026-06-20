package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 병원 가이드 저장/삭제 흐름을 전담한다.
 */
final class FirebaseAdminGuideStore {
    interface GuideMapper {
        List<Map<String, Object>> buildGuideStepDocuments(List<String> stepLines);

        String normalizeText(@Nullable String value);
    }

    interface CompletionListener {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore firestore;
    private final GuideMapper mapper;

    FirebaseAdminGuideStore(FirebaseFirestore firestore, GuideMapper mapper) {
        this.firestore = firestore;
        this.mapper = mapper;
    }

    void saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines,
            CompletionListener listener
    ) {
        List<Map<String, Object>> steps = mapper.buildGuideStepDocuments(stepLines);
        if (steps.isEmpty()) {
            listener.onError("병원 가이드를 저장하지 못했습니다. 단계 내용을 다시 확인해 주세요.");
            return;
        }

        String normalizedHospital = mapper.normalizeText(hospitalName);
        String normalizedDepartment = mapper.normalizeText(departmentName);
        firestore.collection("hospitalGuides")
                .whereEqualTo("hospitalName", normalizedHospital)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentReference targetReference = null;
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        String savedDepartment = documentSnapshot.getString("departmentName");
                        if (normalizedDepartment.equals(savedDepartment)) {
                            targetReference = documentSnapshot.getReference();
                            break;
                        }
                    }

                    Map<String, Object> guideDocument = new HashMap<>();
                    guideDocument.put("hospitalName", normalizedHospital);
                    guideDocument.put("departmentName", normalizedDepartment);
                    guideDocument.put("steps", steps);
                    guideDocument.put("updatedAt", FieldValue.serverTimestamp());

                    if (targetReference == null) {
                        guideDocument.put("createdAt", FieldValue.serverTimestamp());
                        firestore.collection("hospitalGuides")
                                .add(guideDocument)
                                .addOnSuccessListener(unused -> listener.onSuccess())
                                .addOnFailureListener(exception ->
                                        listener.onError("병원 가이드를 저장하지 못했습니다."));
                        return;
                    }

                    targetReference.update(guideDocument)
                            .addOnSuccessListener(unused -> listener.onSuccess())
                            .addOnFailureListener(exception ->
                                    listener.onError("병원 가이드를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        listener.onError("기존 병원 가이드를 확인하지 못했습니다."));
    }

    void deleteHospitalGuide(String guideId, CompletionListener listener) {
        firestore.collection("hospitalGuides")
                .document(guideId)
                .delete()
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(exception ->
                        listener.onError("병원 가이드를 삭제하지 못했습니다."));
    }
}
