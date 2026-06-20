package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore????λ맂 ?몄뀡, ?붿껌, 媛?대뱶, 由ы룷?몃? 議고빀??留ㅻ땲? ?붾㈃??援ъ꽦?쒕떎.
 */
public class FirebaseManagerRepository implements ManagerRepository {
    private final FirebaseFirestore firestore;

    public FirebaseManagerRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // ?몄뀡 臾몄꽌瑜?湲곗??쇰줈 愿???ъ슜?? ?붿껌, 媛?대뱶, 由ы룷?몃? 蹂묐젹濡??쎌뼱 ??쒕낫?쒕? ?꾩꽦?쒕떎.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("companionSessions ?곗씠???뺤떇???뺤씤?댁＜?몄슂.");
                    return;
                }

                firestore.collection("appointmentRequests")
                        .document(session.getAppointmentRequestId())
                        .get()
                        .addOnSuccessListener(requestSnapshot -> {
                            AppointmentRequest request = toAppointmentRequest(requestSnapshot);
                            if (request == null) {
                                callback.onError("appointmentRequests ?곗씠?곕? 李얠? 紐삵뻽?듬땲??");
                                return;
                            }

                            Task<DocumentSnapshot> managerTask = firestore.collection("users")
                                    .document(managerUserId)
                                    .get();
                            Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                                    .whereEqualTo("hospitalName", request.getHospitalName())
                                    .get();
                            Task<QuerySnapshot> reportTask = firestore.collection("sessionReports")
                                    .whereEqualTo("sessionId", session.getId())
                                    .limit(1)
                                    .get();

                            List<Task<?>> tasks = Arrays.asList(
                                    managerTask,
                                    guideTask,
                                    reportTask
                            );

                            Tasks.whenAllSuccess(tasks)
                                    .addOnSuccessListener(results -> {
                                        User manager = toUser((DocumentSnapshot) results.get(0));
                                        User patient = toParticipantUser(
                                                request.getPatientUserId(),
                                                UserRole.PATIENT,
                                                request.getPatientName(),
                                                request.getPatientEmail(),
                                                request.getPatientPhone()
                                        );
                                        User guardian = toParticipantUser(
                                                request.getGuardianUserId(),
                                                UserRole.GUARDIAN,
                                                request.getGuardianName(),
                                                request.getGuardianEmail(),
                                                request.getGuardianPhone()
                                        );
                                        HospitalGuide guide = findGuide(
                                                (QuerySnapshot) results.get(1),
                                                request.getDepartmentName()
                                        );
                                        SessionReport report = toReport((QuerySnapshot) results.get(2));

                                        if (manager == null || patient == null || guardian == null || guide == null) {
                                            callback.onError("Firebase 而щ젆??users, hospitalGuides) ?곗씠?곕? ?뺤씤?댁＜?몄슂.");
                                            return;
                                        }

                                        callback.onSuccess(new ManagerDashboard(
                                                manager,
                                                patient,
                                                guardian,
                                                request,
                                                session,
                                                guide,
                                                report
                                        ));
                                    })
                                    .addOnFailureListener(exception ->
                                            callback.onError("留ㅻ땲? ?곗씠?곕? 遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
                        })
                        .addOnFailureListener(exception ->
                                callback.onError("?덉빟 ?곗씠?곕? 遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // ?④퀎 ?대룞? ?꾩옱 ?몄뀡 臾몄꽌? ?꾩껜 ?④퀎 ?섎? 紐⑤몢 ?뚯븘???댁꽌 ??쒕낫?쒕? 癒쇱? ?쎈뒗??
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard dashboard) {
                        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
                        int currentStep = dashboard.getSession().getCurrentStepOrder();
                        if (currentStep >= totalSteps) {
                            callback.onError("留덉?留??④퀎?낅땲?? 由ы룷?몃? ?꾩넚?댁＜?몄슂.");
                            return;
                        }

                        int nextStep = currentStep + 1;
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("currentStepOrder", nextStep);
                        updates.put("currentStatus", resolveStepStatus(nextStep, totalSteps).name());
                        updates.put("updatedAt", FieldValue.serverTimestamp());

                        // ?몄뀡 ?④퀎? ?덉빟 ?곹깭瑜???踰덉뿉 媛깆떊???붾㈃ 媛??곹깭 遺덉씪移섎? 以꾩씤??
                        WriteBatch batch = firestore.batch();
                        batch.update(sessionSnapshot.getReference(), updates);
                        batch.update(
                                firestore.collection("appointmentRequests")
                                        .document(dashboard.getAppointmentRequest().getId()),
                                "status",
                                AppointmentStatus.IN_PROGRESS.name()
                        );

                        batch.commit()
                                .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                                .addOnFailureListener(exception ->
                                        callback.onError("?ㅼ쓬 ?④퀎濡??대룞?섏? 紐삵뻽?듬땲??"));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback) {
        // 蹂댄샇??怨듭쑀 硫붿떆吏??companionSessions 臾몄꽌???⑥씪 ?꾨뱶濡?愿由ы븳??
        updateSessionField(managerUserId, "guardianUpdate", guardianUpdate, callback);
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = FirebaseCompanionSessionMapper.toSession(
                        sessionSnapshot,
                        UserRole.MANAGER
                );
                if (session == null) {
                    callback.onError("?몄뀡 ?뺣낫瑜?李얠? 紐삵뻽?듬땲??");
                    return;
                }

                long capturedAtMillis = System.currentTimeMillis();
                String normalizedSummary = normalizeText(locationSummary);
                session.recordSharedLocation(latitude, longitude, normalizedSummary, capturedAtMillis);

                Map<String, Object> updates = new HashMap<>();
                updates.put("locationSummary", normalizedSummary);
                updates.put("sharedLatitude", latitude);
                updates.put("sharedLongitude", longitude);
                updates.put("sharedLocationUpdatedAt", FieldValue.serverTimestamp());
                updates.put(
                        "sharedLocationHistory",
                        FirebaseCompanionSessionMapper.toSharedLocationHistoryPayload(
                                session.getSharedLocationHistory()
                        )
                );
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("?ㅼ떆媛??꾩튂瑜???ν븯吏 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("liveLocationSharingActive", active);
                if (active) {
                    if (!Boolean.TRUE.equals(sessionSnapshot.getBoolean("liveLocationSharingActive"))) {
                        updates.put("liveLocationSharingStartedAt", FieldValue.serverTimestamp());
                    }
                } else {
                    updates.put("liveLocationSharingStartedAt", FieldValue.delete());
                }
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("?ㅼ떆媛??꾩튂 怨듭쑀 ?곹깭瑜???ν븯吏 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback) {
        // ?꾩튂 怨듭쑀 硫붾え???몄뀡 臾몄꽌??蹂꾨룄 ?꾨뱶????ν븳??
        updateSessionField(managerUserId, "locationSummary", locationSummary, callback);
    }

    @Override
    public void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback) {
        // ?꾩옣 ?ъ쭊?대굹 ?쒕쪟 ?뺤씤 硫붾え瑜??ㅼ쓬 ?붾㈃?먯꽌???ъ궗?⑺븷 ???덇쾶 ??ν븳??
        updateSessionField(managerUserId, "fieldPhotoNote", fieldPhotoNote, callback);
    }

    @Override
    public void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback) {
        // ??硫붾え??媛숈? ?몄뀡 臾몄꽌 ?덉뿉 ??ν빐 由ы룷???묒꽦 ?꾩뿉 ?꾩쟻?쒕떎.
        updateSessionField(managerUserId, "medicationNote", medicationNote, callback);
    }

    @Override
    public void savePharmacySummary(String managerUserId, String pharmacySummary, RepositoryCallback<ManagerDashboard> callback) {
        updateSessionField(managerUserId, "pharmacySummary", pharmacySummary, callback);
    }

    @Override
    public void updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("prescriptionCollected", prescriptionCollected);
        updateSessionFields(managerUserId, updates, callback);
    }

    @Override
    public void updatePharmacyCompleted(String managerUserId, boolean pharmacyCompleted, RepositoryCallback<ManagerDashboard> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("pharmacyCompleted", pharmacyCompleted);
        updateSessionFields(managerUserId, updates, callback);
    }

    @Override
    public void updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("medicationGuidanceCompleted", medicationGuidanceCompleted);
        updateSessionFields(managerUserId, updates, callback);
    }

    @Override
    public void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError("users 而щ젆?섏뿉??留ㅻ땲? ?뺣낫瑜?李얠? 紐삵뻽?듬땲??");
                        return;
                    }
                    callback.onSuccess(toManagerHomeProfile(documentSnapshot));
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? ???붿빟 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null) {
                        callback.onError("留ㅻ땲? ???섏씠吏 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??");
                        return;
                    }

                    callback.onSuccess(new ManagerDocumentOverview(
                            manager,
                            toManagerHomeProfile(documentSnapshot),
                            toManagerDocumentHistory(documentSnapshot)
                    ));
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? ???섏씠吏 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        Task<DocumentSnapshot> managerTask = firestore.collection("users")
                .document(managerUserId)
                .get();
        Task<QuerySnapshot> sessionTask = firestore.collection("companionSessions")
                .whereEqualTo("managerUserId", managerUserId)
                .get();

        List<Task<?>> tasks = Arrays.asList(managerTask, sessionTask);
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    User manager = toUser((DocumentSnapshot) results.get(0));
                    if (manager == null) {
                        callback.onError("留ㅻ땲? 怨쇨굅 ?숉뻾 ?대젰??遺덈윭?ㅼ? 紐삵뻽?듬땲??");
                        return;
                    }

                    List<CompanionSession> completedSessions = new ArrayList<>();
                    QuerySnapshot querySnapshot = (QuerySnapshot) results.get(1);
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        CompanionSession session = toSession(documentSnapshot);
                        if (session != null && session.getStatus() == SessionStatus.COMPLETED) {
                            completedSessions.add(session);
                        }
                    }

                    if (completedSessions.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    loadManagerHistoryDetailsSequentially(
                            manager,
                            completedSessions,
                            0,
                            new ArrayList<>(),
                            callback
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? 怨쇨굅 ?숉뻾 ?대젰??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        String normalizedSummary = normalizeText(documentSummary);
        Map<String, Object> updates = new HashMap<>();
        updates.put("managerDocumentSummary", normalizedSummary);
        updates.put(
                "managerDocumentStatus",
                normalizedSummary.isEmpty()
                        ? ManagerDocumentStatus.NOT_SUBMITTED.name()
                        : ManagerDocumentStatus.PENDING_REVIEW.name()
        );
        updates.put("managerDocumentReviewNote", "");
        updates.put("managerDocumentReviewedByName", "");
        updates.put("managerDocumentReviewedAt", FieldValue.delete());
        updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(managerUserId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        onManagerDocumentSummarySaved(managerUserId, normalizedSummary, callback))
                .addOnFailureListener(exception ->
                        callback.onError("?쒕쪟 ?깅줉 ?뺣낫瑜???ν븯吏 紐삵뻽?듬땲??"));

        boolean shouldUseLegacyPath = false;
        if (shouldUseLegacyPath) {
        saveManagerHomeProfileField(
                managerUserId,
                "managerDocumentSummary",
                normalizeText(documentSummary),
                "managerDocumentUpdatedAt",
                "?쒕쪟 ?깅줉 ?뺣낫瑜???ν븯吏 紐삵뻽?듬땲??",
                callback
        );
        }
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        if (documentFileMetadata == null || documentFileMetadata.isEmpty()) {
            callback.onError("?낅줈?쒗븳 ?쒕쪟 ?뚯씪 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??");
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("留ㅻ땲? 怨꾩젙???뺤씤?섏? 紐삵뻽?듬땲??");
                        return;
                    }

                    String documentSummary = normalizeText(documentSnapshot.getString("managerDocumentSummary"));
                    if (documentSummary.isEmpty()) {
                        callback.onError("?쒕쪟 ?붿빟??癒쇱? ??ν븳 ???먮낯 ?뚯씪???щ젮二쇱꽭??");
                        return;
                    }

                    long uploadedAtMillis = documentFileMetadata.getUploadedAtMillis() > 0L
                            ? documentFileMetadata.getUploadedAtMillis()
                            : System.currentTimeMillis();
                    List<ManagerDocumentHistoryEntry> historyEntries = appendManagerDocumentHistory(
                            toManagerDocumentHistory(documentSnapshot),
                            new ManagerDocumentHistoryEntry(
                                    ManagerDocumentHistoryEventType.SUBMITTED,
                                    uploadedAtMillis,
                                    normalizeText(manager.getName()),
                                    documentSummary,
                                    ""
                            )
                    );

                    Map<String, Object> updates = new HashMap<>();
                    String fileKeyPrefix = "managerDocumentFiles."
                            + documentFileMetadata.getFileType().getStorageKey();
                    updates.put(fileKeyPrefix + ".fullPath", documentFileMetadata.getFullPath());
                    updates.put(fileKeyPrefix + ".fileName", documentFileMetadata.getFileName());
                    updates.put(fileKeyPrefix + ".contentType", documentFileMetadata.getContentType());
                    updates.put(fileKeyPrefix + ".previewUri", documentFileMetadata.getPreviewUri());
                    updates.put(fileKeyPrefix + ".uploadedAt", uploadedAtMillis);
                    updates.put(
                            "managerDocumentFilePaths." + documentFileMetadata.getFileType().getStorageKey(),
                            documentFileMetadata.getFullPath()
                    );
                    String legacyPathKey = resolveLegacyDocumentStoragePathKey(
                            documentFileMetadata.getFileType()
                    );
                    if (legacyPathKey != null) {
                        updates.put(legacyPathKey, documentFileMetadata.getFullPath());
                    }
                    updates.put("managerDocumentStatus", ManagerDocumentStatus.PENDING_REVIEW.name());
                    updates.put("managerDocumentReviewNote", "");
                    updates.put("managerDocumentReviewedByName", "");
                    updates.put("managerDocumentReviewedAt", FieldValue.delete());
                    updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());
                    updates.put("managerDocumentHistory", toManagerDocumentHistoryPayload(historyEntries));

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("?먮낯 ?쒕쪟 ?뚯씪 ?뺣낫瑜???ν븯吏 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? ?쒕쪟 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        if (documentFileMetadata == null || documentFileMetadata.isEmpty()) {
            callback.onError("?낅줈?쒗븳 ?쒕쪟 ?뚯씪 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??");
            return;
        }

        long uploadedAtMillis = documentFileMetadata.getUploadedAtMillis() > 0L
                ? documentFileMetadata.getUploadedAtMillis()
                : System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        String fileKeyPrefix = "managerDocumentFiles." + documentFileMetadata.getFileType().getStorageKey();
        updates.put(fileKeyPrefix + ".fullPath", documentFileMetadata.getFullPath());
        updates.put(fileKeyPrefix + ".fileName", documentFileMetadata.getFileName());
        updates.put(fileKeyPrefix + ".contentType", documentFileMetadata.getContentType());
        updates.put(fileKeyPrefix + ".previewUri", documentFileMetadata.getPreviewUri());
        updates.put(fileKeyPrefix + ".uploadedAt", uploadedAtMillis);
        updates.put(
                "managerDocumentFilePaths." + documentFileMetadata.getFileType().getStorageKey(),
                documentFileMetadata.getFullPath()
        );
        String legacyPathKey = resolveLegacyDocumentStoragePathKey(documentFileMetadata.getFileType());
        if (legacyPathKey != null) {
            updates.put(legacyPathKey, documentFileMetadata.getFullPath());
        }
        updates.put("managerDocumentStatus", ManagerDocumentStatus.NOT_SUBMITTED.name());
        updates.put("managerDocumentReviewNote", "");
        updates.put("managerDocumentReviewedByName", "");
        updates.put("managerDocumentReviewedAt", FieldValue.delete());
        updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(managerUserId)
                .update(updates)
                .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                .addOnFailureListener(exception ->
                        callback.onError("?먮낯 ?쒕쪟 ?뚯씪 珥덉븞????ν븯吏 紐삵뻽?듬땲??"));
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        saveManagerHomeProfileField(
                managerUserId,
                "managerAvailabilitySummary",
                normalizeText(availabilitySummary),
                "managerAvailabilityUpdatedAt",
                "?쒕룞 媛???쇱젙????ν븯吏 紐삵뻽?듬땲??",
                callback
        );
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // 由ы룷????μ? sessionReports ?앹꽦怨??몄뀡 醫낅즺 泥섎━瑜??④퍡 諛섏쁺?댁빞 ?쒕떎.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("?몄뀡 ?뺣낫瑜?李얠? 紐삵뻽?듬땲??");
                    return;
                }

                Map<String, Object> reportDocument = new HashMap<>();
                reportDocument.put("sessionId", session.getId());
                reportDocument.put("summary", summary);
                reportDocument.put("treatmentNotes", treatmentNotes);
                reportDocument.put("medicationNotes", medicationNotes);
                reportDocument.put("medicationName", medicationName);
                reportDocument.put("medicationChangeSummary", medicationChangeSummary);
                reportDocument.put("medicationScheduleNote", medicationScheduleNote);
                reportDocument.put(
                        "medicationComparisonDecisionCode",
                        medicationComparisonDecision == null ? "" : medicationComparisonDecision.name()
                );
                reportDocument.put("medicationComparisonNote", medicationComparisonNote);
                reportDocument.put("nextVisitAt", nextVisitAt);
                reportDocument.put("createdAt", FieldValue.serverTimestamp());

                // 由ы룷????κ낵 ?몄뀡 醫낅즺 泥섎━瑜?諛곗튂濡?臾띠뼱 ??踰덉뿉 諛섏쁺?쒕떎.
                WriteBatch batch = firestore.batch();
                DocumentReference reportReference = firestore.collection("sessionReports")
                        .document("report-" + session.getId());

                batch.set(reportReference, reportDocument);
                batch.update(sessionSnapshot.getReference(), "currentStatus", SessionStatus.COMPLETED.name());
                batch.update(sessionSnapshot.getReference(), "medicationNote", medicationNotes);
                batch.update(sessionSnapshot.getReference(), "liveLocationSharingActive", false);
                batch.update(sessionSnapshot.getReference(), "liveLocationSharingStartedAt", FieldValue.delete());
                batch.update(sessionSnapshot.getReference(), "updatedAt", FieldValue.serverTimestamp());
                batch.update(
                        firestore.collection("appointmentRequests")
                                .document(session.getAppointmentRequestId()),
                        "status",
                        AppointmentStatus.COMPLETED.name()
                );

                batch.commit()
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("由ы룷?몃? ??ν븯吏 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firestore.collection("supportInquiries")
                .whereEqualTo("managerUserId", managerUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SupportInquiry> inquiries = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        SupportInquiry inquiry = toSupportInquiry(documentSnapshot);
                        if (inquiry != null) {
                            inquiries.add(inquiry);
                        }
                    }
                    inquiries.sort((left, right) ->
                            Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
                    callback.onSuccess(inquiries);
                })
                .addOnFailureListener(exception ->
                        callback.onError("臾몄쓽 ?댁뿭??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("留ㅻ땲? 怨꾩젙???뺤씤?섏? 紐삵뻽?듬땲??");
                        return;
                    }

                    Map<String, Object> inquiryDocument = new HashMap<>();
                    inquiryDocument.put("managerUserId", managerUserId);
                    inquiryDocument.put("managerName", normalizeText(manager.getName()));
                    inquiryDocument.put("category", category == null
                            ? SupportInquiryCategory.MATCHING.getValue()
                            : category.getValue());
                    inquiryDocument.put("title", normalizeText(title));
                    inquiryDocument.put("body", normalizeText(body));
                    inquiryDocument.put("status", SupportInquiryStatus.RECEIVED.name());
                    inquiryDocument.put("responseText", "");
                    inquiryDocument.put("respondedByName", "");
                    inquiryDocument.put("respondedAt", FieldValue.delete());
                    inquiryDocument.put("createdAt", FieldValue.serverTimestamp());

                    firestore.collection("supportInquiries")
                            .add(inquiryDocument)
                            .addOnSuccessListener(unused -> getSupportInquiries(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("臾몄쓽 ?댁슜????ν븯吏 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        String normalizedMessage = normalizeText(message);
        if (normalizedMessage.isEmpty() && (attachments == null || attachments.isEmpty())) {
            callback.onError("메시지나 첨부 파일 중 하나는 함께 보내야 합니다.");
            return;
        }

        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("chatMessages", FieldValue.arrayUnion(
                        buildChatMessagePayload(UserRole.MANAGER, normalizedMessage, attachments)
                ));
                updates.put(resolveChatReadField(UserRole.MANAGER), FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("硫붿떆吏瑜??꾩넚?섏? 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(resolveChatReadField(UserRole.MANAGER), FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                sessionSnapshot.getReference().update(updates);
            }

            @Override
            public void onError(String message) {
                // ?쎌쓬 泥섎━ ?ㅽ뙣???붾㈃ ?먮쫫??留됱? ?딅뒗??
            }
        });
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        if (stage == null || stage == CompanionLocationAlertStage.NONE) {
            return;
        }
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null || !session.getLocationAlertStage().canAdvanceTo(stage)) {
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("locationAlertStage", stage.getValue());
                updates.put("locationAlertSentAt", FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                sessionSnapshot.getReference().update(updates);
            }

            @Override
            public void onError(String message) {
                // ?먮룞 ?꾩튂 ?뚮┝ ?ㅽ뙣???꾩튂 怨듭쑀 ?먮쫫??留됱? ?딅뒗??
            }
        });
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void onManagerDocumentSummarySaved(
            String managerUserId,
            String normalizedSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        if (normalizedSummary.isEmpty()) {
            getManagerHomeProfile(managerUserId, callback);
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError("users 而щ젆?섏뿉??留ㅻ땲? ?뺣낫瑜?李얠? 紐삵뻽?듬땲??");
                        return;
                    }

                    List<ManagerDocumentHistoryEntry> historyEntries = appendManagerDocumentHistory(
                            toManagerDocumentHistory(documentSnapshot),
                            new ManagerDocumentHistoryEntry(
                                    ManagerDocumentHistoryEventType.SUBMITTED,
                                    System.currentTimeMillis(),
                                    normalizeText(documentSnapshot.getString("name")),
                                    normalizedSummary,
                                    ""
                            )
                    );

                    documentSnapshot.getReference()
                            .update("managerDocumentHistory", toManagerDocumentHistoryPayload(historyEntries))
                            .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("??뺤첒 野꺜??????????館釉?쭪? 筌륁궢六??щ빍??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("??뺤첒 野꺜??????????館釉??袁④에 ?類ｋ궖???븍뜄???? 筌륁궢六??щ빍??"));
    }

    private void loadManagerHistoryDetailsSequentially(
            User manager,
            List<CompanionSession> sessions,
            int index,
            List<AppointmentRequestDetail> results,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        if (index >= sessions.size()) {
            results.sort((left, right) -> right.getAppointmentRequest()
                    .getAppointmentAt()
                    .compareTo(left.getAppointmentRequest().getAppointmentAt()));
            callback.onSuccess(results);
            return;
        }

        CompanionSession session = sessions.get(index);
        firestore.collection("appointmentRequests")
                .document(session.getAppointmentRequestId())
                .get()
                .addOnSuccessListener(requestSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(requestSnapshot);
                    if (request == null) {
                        loadManagerHistoryDetailsSequentially(
                                manager,
                                sessions,
                                index + 1,
                                results,
                                callback
                        );
                        return;
                    }

                    Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                            .whereEqualTo("hospitalName", request.getHospitalName())
                            .get();
                    Task<QuerySnapshot> reportTask = firestore.collection("sessionReports")
                            .whereEqualTo("sessionId", session.getId())
                            .limit(1)
                            .get();
                    Task<DocumentSnapshot> followUpTask = firestore.collection("appointmentFollowUps")
                            .document(request.getId())
                            .get();

                    Tasks.whenAllSuccess(guideTask, reportTask, followUpTask)
                            .addOnSuccessListener(historyResults -> {
                                User patient = toParticipantUser(
                                        request.getPatientUserId(),
                                        UserRole.PATIENT,
                                        request.getPatientName(),
                                        request.getPatientEmail(),
                                        request.getPatientPhone()
                                );
                                User guardian = toParticipantUser(
                                        request.getGuardianUserId(),
                                        UserRole.GUARDIAN,
                                        request.getGuardianName(),
                                        request.getGuardianEmail(),
                                        request.getGuardianPhone()
                                );
                                HospitalGuide hospitalGuide = findGuide(
                                        (QuerySnapshot) historyResults.get(0),
                                        request.getDepartmentName()
                                );
                                SessionReport report = toReport((QuerySnapshot) historyResults.get(1));
                                AppointmentFollowUpRecord followUpRecord = toAppointmentFollowUpRecord(
                                        (DocumentSnapshot) historyResults.get(2),
                                        request.getId()
                                );

                                results.add(new AppointmentRequestDetail(
                                        request,
                                        patient,
                                        guardian,
                                        manager,
                                        session,
                                        report,
                                        hospitalGuide,
                                        followUpRecord
                                ));
                                loadManagerHistoryDetailsSequentially(
                                        manager,
                                        sessions,
                                        index + 1,
                                        results,
                                        callback
                                );
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("留ㅻ땲? 怨쇨굅 ?숉뻾 ?대젰??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("留ㅻ땲? 怨쇨굅 ?숉뻾 ?대젰??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    private void saveManagerHomeProfileField(
            String managerUserId,
            String key,
            String value,
            String updatedAtKey,
            String errorMessage,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(key, value);
        updates.put(updatedAtKey, FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(managerUserId)
                .update(updates)
                .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                .addOnFailureListener(exception -> callback.onError(errorMessage));
    }

    private void updateSessionField(
            String managerUserId,
            String key,
            String value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(key, value);
        updateSessionFields(managerUserId, updates, callback);
    }

    private void updateSessionFields(
            String managerUserId,
            Map<String, Object> updates,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // ?몄뀡 硫붾え? ?쎄뎅 ?④퀎 ?곹깭?????????쒕낫?쒕? ?ㅼ떆 ?쎌뼱 ??援ъ“濡??좎??쒕떎.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updatesWithTimestamp = new HashMap<>(updates);
                updatesWithTimestamp.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updatesWithTimestamp)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("?몄뀡 ?뺣낫瑜???ν븯吏 紐삵뻽?듬땲??"));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadSessionDocument(String managerUserId, RepositoryCallback<DocumentSnapshot> callback) {
        // ?꾩옱 援ы쁽? 留ㅻ땲???吏꾪뻾 以묒씤 ?숉뻾 ?몄뀡 1嫄댁쓣 湲곗??쇰줈 ?붾㈃??援ъ꽦?쒕떎.
        firestore.collection("companionSessions")
                .whereEqualTo("managerUserId", managerUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        CompanionSession session = toSession(documentSnapshot);
                        if (session != null && isActiveSession(session)) {
                            callback.onSuccess(documentSnapshot);
                            return;
                        }
                    }
                    callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
                })
                .addOnFailureListener(exception ->
                        callback.onError("?숉뻾 ?몄뀡 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    private boolean isActiveSession(CompanionSession session) {
        return session.getStatus() != SessionStatus.COMPLETED
                && session.getStatus() != SessionStatus.CANCELED;
    }

    private ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot) {
        String documentSummary = documentSnapshot.getString("managerDocumentSummary");
        String availabilitySummary = documentSnapshot.getString("managerAvailabilitySummary");
        String documentStatus = documentSnapshot.getString("managerDocumentStatus");
        String documentReviewNote = documentSnapshot.getString("managerDocumentReviewNote");
        String documentReviewedByName = documentSnapshot.getString("managerDocumentReviewedByName");
        long documentUpdatedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentUpdatedAt"));
        long documentReviewedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentReviewedAt"));
        return new ManagerHomeProfile(
                documentSummary == null ? "" : documentSummary,
                availabilitySummary == null ? "" : availabilitySummary,
                resolveManagerDocumentStatus(documentStatus, documentSummary),
                documentReviewNote == null ? "" : documentReviewNote,
                documentUpdatedAtMillis,
                documentReviewedAtMillis,
                documentReviewedByName == null ? "" : documentReviewedByName,
                toManagerDocumentFiles(documentSnapshot)
        );
    }

    private List<ManagerDocumentFileMetadata> toManagerDocumentFiles(DocumentSnapshot documentSnapshot) {
        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileByType = new HashMap<>();

        Object rawMetadataMap = documentSnapshot.get("managerDocumentFiles");
        if (rawMetadataMap instanceof Map) {
            Map<?, ?> metadataMap = (Map<?, ?>) rawMetadataMap;
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadata(
                        fileType,
                        metadataMap.get(fileType.getStorageKey())
                );
                if (metadata != null) {
                    fileByType.put(fileType, metadata);
                }
            }
        }

        Object rawPathMap = documentSnapshot.get("managerDocumentFilePaths");
        if (rawPathMap instanceof Map) {
            Map<?, ?> pathMap = (Map<?, ?>) rawPathMap;
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                if (fileByType.containsKey(fileType)) {
                    continue;
                }
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                        fileType,
                        stringValue(pathMap.get(fileType.getStorageKey()))
                );
                if (metadata != null) {
                    fileByType.put(fileType, metadata);
                }
            }
        }

        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            if (fileByType.containsKey(fileType)) {
                continue;
            }
            String legacyPathKey = resolveLegacyDocumentStoragePathKey(fileType);
            if (legacyPathKey == null) {
                continue;
            }
            ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                    fileType,
                    documentSnapshot.getString(legacyPathKey)
            );
            if (metadata != null) {
                fileByType.put(fileType, metadata);
            }
        }

        List<ManagerDocumentFileMetadata> documentFiles = new ArrayList<>();
        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            ManagerDocumentFileMetadata metadata = fileByType.get(fileType);
            if (metadata != null) {
                documentFiles.add(metadata);
            }
        }
        return documentFiles;
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            @Nullable Object rawValue
    ) {
        if (!(rawValue instanceof Map)) {
            return null;
        }

        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = normalizeText(stringValue(valueMap.get("fullPath")));
        if (fullPath.isEmpty()) {
            return null;
        }

        String fileName = normalizeText(stringValue(valueMap.get("fileName")));
        if (fileName.isEmpty()) {
            fileName = resolveFileNameFromPath(fullPath);
        }

        return new ManagerDocumentFileMetadata(
                fileType,
                fullPath,
                fileName,
                normalizeText(stringValue(valueMap.get("contentType"))),
                resolveTimestampMillis(valueMap.get("uploadedAt")),
                normalizeText(stringValue(valueMap.get("previewUri")))
        );
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadataFromPath(
            ManagerDocumentFileType fileType,
            @Nullable String fullPath
    ) {
        String normalizedPath = normalizeText(fullPath);
        if (normalizedPath.isEmpty()) {
            return null;
        }
        return new ManagerDocumentFileMetadata(
                fileType,
                normalizedPath,
                resolveFileNameFromPath(normalizedPath),
                "",
                0L
        );
    }

    private List<ManagerDocumentHistoryEntry> toManagerDocumentHistory(DocumentSnapshot documentSnapshot) {
        Object rawHistory = documentSnapshot.get("managerDocumentHistory");
        if (!(rawHistory instanceof List)) {
            return new ArrayList<>();
        }

        List<ManagerDocumentHistoryEntry> historyEntries = new ArrayList<>();
        for (Object rawEntry : (List<?>) rawHistory) {
            if (!(rawEntry instanceof Map)) {
                continue;
            }
            Map<?, ?> entryMap = (Map<?, ?>) rawEntry;
            historyEntries.add(new ManagerDocumentHistoryEntry(
                    resolveHistoryEventType(normalizeText(stringValue(entryMap.get("eventType")))),
                    resolveTimestampMillis(entryMap.get("happenedAt")),
                    normalizeText(stringValue(entryMap.get("actorName"))),
                    normalizeText(stringValue(entryMap.get("summary"))),
                    normalizeText(stringValue(entryMap.get("reviewNote")))
            ));
        }
        historyEntries.sort((left, right) -> Long.compare(right.getHappenedAtMillis(), left.getHappenedAtMillis()));
        return historyEntries;
    }

    private List<ManagerDocumentHistoryEntry> appendManagerDocumentHistory(
            List<ManagerDocumentHistoryEntry> historyEntries,
            ManagerDocumentHistoryEntry historyEntry
    ) {
        List<ManagerDocumentHistoryEntry> updatedEntries = new ArrayList<>(historyEntries);
        updatedEntries.add(0, historyEntry);
        return updatedEntries;
    }

    private List<Map<String, Object>> toManagerDocumentHistoryPayload(
            List<ManagerDocumentHistoryEntry> historyEntries
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ManagerDocumentHistoryEntry historyEntry : historyEntries) {
            Map<String, Object> item = new HashMap<>();
            item.put("eventType", historyEntry.getEventType().name());
            item.put("happenedAt", historyEntry.getHappenedAtMillis());
            item.put("actorName", historyEntry.getActorName());
            item.put("summary", historyEntry.getSummary());
            item.put("reviewNote", historyEntry.getReviewNote());
            payload.add(item);
        }
        return payload;
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        // users 而щ젆??臾몄꽌瑜??깆쓽 User 紐⑤뜽濡?蹂?섑븳??
        if (!documentSnapshot.exists()) {
            return null;
        }

        String roleValue = documentSnapshot.getString("role");
        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        if (roleValue == null || name == null || email == null) {
            return null;
        }

        return new User(
                documentSnapshot.getId(),
                UserRole.valueOf(roleValue),
                name,
                email,
                phone == null ? "" : phone
        );
    }

    @Nullable
    private User toParticipantUser(
            @Nullable String userId,
            UserRole role,
            @Nullable String name,
            @Nullable String email,
            @Nullable String phone
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        return new User(
                userId,
                role,
                normalizeText(name),
                normalizeText(email),
                normalizeText(phone)
        );
    }

    @Nullable
    private AppointmentRequest toAppointmentRequest(DocumentSnapshot documentSnapshot) {
        // appointmentRequests 臾몄꽌瑜??쇱젙 移대뱶??諛붾줈 ?????덈뒗 紐⑤뜽濡??뺣━?쒕떎.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String patientUserId = documentSnapshot.getString("patientUserId");
        String guardianUserId = documentSnapshot.getString("guardianUserId");
        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        String appointmentAt = stringifyDate(documentSnapshot.get("appointmentAt"));
        String meetingPlace = documentSnapshot.getString("meetingPlace");
        String specialNotes = documentSnapshot.getString("specialNotes");
        String statusValue = documentSnapshot.getString("status");
        String managerUserId = documentSnapshot.getString("managerUserId");

        if (patientUserId == null
                || guardianUserId == null
                || hospitalName == null
                || departmentName == null
                || appointmentAt == null
                || statusValue == null) {
            return null;
        }

        return new AppointmentRequest(
                documentSnapshot.getId(),
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                appointmentAt,
                meetingPlace == null ? "" : meetingPlace,
                specialNotes == null ? "" : specialNotes,
                AppointmentStatus.valueOf(statusValue),
                managerUserId,
                stringOrEmpty(documentSnapshot.getString("patientName")),
                stringOrEmpty(documentSnapshot.getString("patientPhone")),
                stringOrEmpty(documentSnapshot.getString("patientEmail")),
                stringOrEmpty(documentSnapshot.getString("guardianName")),
                stringOrEmpty(documentSnapshot.getString("guardianPhone")),
                stringOrEmpty(documentSnapshot.getString("guardianEmail")),
                stringOrEmpty(documentSnapshot.getString("patientConditionSummary")),
                stringOrEmpty(documentSnapshot.getString("medicationSummary")),
                stringOrEmpty(documentSnapshot.getString("mobilitySupportCode")),
                stringOrEmpty(documentSnapshot.getString("tripTypeCode")),
                stringOrEmpty(documentSnapshot.getString("managerGenderPreferenceCode")),
                stringOrEmpty(documentSnapshot.getString("paymentMethodCode")),
                stringOrEmpty(documentSnapshot.getString("couponCode")),
                numberOrZero(documentSnapshot.get("basePrice")),
                numberOrZero(documentSnapshot.get("optionSurchargePrice")),
                numberOrZero(documentSnapshot.get("couponDiscountPrice")),
                numberOrZero(documentSnapshot.get("finalPrice")),
                stringOrEmpty(documentSnapshot.getString("paymentStatusCode")),
                stringOrEmpty(documentSnapshot.getString("paymentApprovalCode")),
                stringOrEmpty(documentSnapshot.getString("paymentApprovedAt")),
                stringOrEmpty(documentSnapshot.getString("paymentProviderLabel"))
        );
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        // currentStepOrder? currentStatus瑜??쎌뼱 ?④퀎??UI ?곹깭瑜?蹂듭썝?쒕떎.
        return FirebaseCompanionSessionMapper.toSession(documentSnapshot, UserRole.MANAGER);
    }

    private Map<String, Object> buildChatMessagePayload(
            UserRole senderRole,
            String body,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderRole", senderRole == null ? UserRole.MANAGER.name() : senderRole.name());
        payload.put("body", normalizeText(body));
        payload.put("sentAtMillis", System.currentTimeMillis());
        if (attachments != null && !attachments.isEmpty()) {
            payload.put("attachments", toChatAttachmentPayloads(attachments));
            payload.put("attachment", toChatAttachmentPayload(attachments.get(0)));
        }
        return payload;
    }

    private String resolveChatReadField(@Nullable UserRole role) {
        if (role == UserRole.PATIENT) {
            return "patientChatReadAt";
        }
        if (role == UserRole.GUARDIAN) {
            return "guardianChatReadAt";
        }
        return "managerChatReadAt";
    }

    @Nullable
    private CompanionChatAttachment toChatAttachment(@Nullable Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return null;
        }
        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = normalizeText(stringValue(valueMap.get("fullPath")));
        if (fullPath.isEmpty()) {
            return null;
        }
        return new CompanionChatAttachment(
                fullPath,
                normalizeText(stringValue(valueMap.get("fileName"))),
                normalizeText(stringValue(valueMap.get("contentType"))),
                resolveTimestampMillis(valueMap.get("uploadedAtMillis"))
        );
    }

    private List<CompanionChatAttachment> toChatAttachments(
            @Nullable Object rawAttachments,
            @Nullable Object rawAttachment
    ) {
        List<CompanionChatAttachment> attachments = new ArrayList<>();
        if (rawAttachments instanceof List) {
            for (Object item : (List<?>) rawAttachments) {
                CompanionChatAttachment attachment = toChatAttachment(item);
                if (attachment != null && !attachment.isEmpty()) {
                    attachments.add(attachment);
                }
            }
        }
        if (!attachments.isEmpty()) {
            return attachments;
        }
        CompanionChatAttachment attachment = toChatAttachment(rawAttachment);
        if (attachment != null && !attachment.isEmpty()) {
            attachments.add(attachment);
        }
        return attachments;
    }

    private List<CompanionChatMessage> toChatMessages(@Nullable Object rawValue) {
        List<CompanionChatMessage> messages = new ArrayList<>();
        if (!(rawValue instanceof List)) {
            return messages;
        }
        for (Object rawMessage : (List<?>) rawValue) {
            if (!(rawMessage instanceof Map)) {
                continue;
            }
            Map<?, ?> valueMap = (Map<?, ?>) rawMessage;
            String roleValue = normalizeText(stringValue(valueMap.get("senderRole")));
            String body = normalizeText(stringValue(valueMap.get("body")));
            List<CompanionChatAttachment> attachments = toChatAttachments(
                    valueMap.get("attachments"),
                    valueMap.get("attachment")
            );
            if (body.isEmpty() && attachments.isEmpty()) {
                continue;
            }
            long sentAtMillis = resolveTimestampMillis(valueMap.get("sentAtMillis"));
            UserRole senderRole;
            try {
                senderRole = roleValue.isEmpty() ? UserRole.MANAGER : UserRole.valueOf(roleValue);
            } catch (IllegalArgumentException exception) {
                senderRole = UserRole.MANAGER;
            }
            messages.add(new CompanionChatMessage(senderRole, body, sentAtMillis, attachments));
        }
        return messages;
    }

    private List<Map<String, Object>> toChatAttachmentPayloads(List<CompanionChatAttachment> attachments) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (CompanionChatAttachment attachment : attachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            payloads.add(toChatAttachmentPayload(attachment));
        }
        return payloads;
    }

    private Map<String, Object> toChatAttachmentPayload(CompanionChatAttachment attachment) {
        Map<String, Object> attachmentPayload = new HashMap<>();
        attachmentPayload.put("fullPath", attachment.getFullPath());
        attachmentPayload.put("fileName", attachment.getFileName());
        attachmentPayload.put("contentType", attachment.getContentType());
        attachmentPayload.put("uploadedAtMillis", attachment.getUploadedAtMillis());
        return attachmentPayload;
    }

    @Nullable
    private HospitalGuide findGuide(QuerySnapshot querySnapshot, String departmentName) {
        // 蹂묒썝紐낆쑝濡?癒쇱? 醫곹엺 ??吏꾨즺怨쇨? ?쇱튂?섎뒗 媛?대뱶瑜???踰???李얜뒗??
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null && guide.getDepartmentName().equals(departmentName)) {
                return guide;
            }
        }
        return null;
    }

    @Nullable
    private HospitalGuide toGuide(DocumentSnapshot documentSnapshot) {
        // Firestore 諛곗뿴 ?뺥깭??steps瑜?GuideStep 紐⑸줉?쇰줈 蹂?섑븳??
        if (!documentSnapshot.exists()) {
            return null;
        }

        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        Object stepsValue = documentSnapshot.get("steps");
        if (hospitalName == null || departmentName == null || !(stepsValue instanceof List)) {
            return null;
        }

        List<?> rawSteps = (List<?>) stepsValue;
        List<GuideStep> steps = new ArrayList<>();
        for (Object rawStep : rawSteps) {
            if (!(rawStep instanceof Map)) {
                continue;
            }
            // 媛??④퀎??order/title/description 3媛??꾨뱶瑜?媛吏?留듭씠?쇨퀬 媛?뺥븳??
            Map<?, ?> stepMap = (Map<?, ?>) rawStep;
            Object orderValue = stepMap.get("order");
            Object titleValue = stepMap.get("title");
            Object descriptionValue = stepMap.get("description");
            if (!(orderValue instanceof Number) || titleValue == null || descriptionValue == null) {
                continue;
            }
            steps.add(new GuideStep(
                    ((Number) orderValue).intValue(),
                    String.valueOf(titleValue),
                    String.valueOf(descriptionValue)
            ));
        }

        if (steps.isEmpty()) {
            return null;
        }

        return new HospitalGuide(documentSnapshot.getId(), hospitalName, departmentName, steps);
    }

    @Nullable
    private SessionReport toReport(QuerySnapshot querySnapshot) {
        // ?꾩옱 援ы쁽? ?몄뀡??由ы룷??1媛쒕? 媛?뺥븯怨?泥?臾몄꽌留??ъ슜?쒕떎.
        if (querySnapshot.isEmpty()) {
            return null;
        }
        DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
        String sessionId = documentSnapshot.getString("sessionId");
        String summary = documentSnapshot.getString("summary");
        String treatmentNotes = documentSnapshot.getString("treatmentNotes");
        String medicationNotes = documentSnapshot.getString("medicationNotes");
        String medicationName = documentSnapshot.getString("medicationName");
        String medicationChangeSummary = documentSnapshot.getString("medicationChangeSummary");
        String medicationScheduleNote = documentSnapshot.getString("medicationScheduleNote");
        MedicationComparisonDecision medicationComparisonDecision = MedicationComparisonDecision.fromValue(
                documentSnapshot.getString("medicationComparisonDecisionCode")
        );
        String medicationComparisonNote = documentSnapshot.getString("medicationComparisonNote");
        String nextVisitAt = stringifyDate(documentSnapshot.get("nextVisitAt"));
        if (sessionId == null || summary == null) {
            return null;
        }

        return new SessionReport(
                documentSnapshot.getId(),
                sessionId,
                summary,
                treatmentNotes == null ? "" : treatmentNotes,
                medicationNotes == null ? "" : medicationNotes,
                medicationName == null ? "" : medicationName,
                medicationChangeSummary == null ? "" : medicationChangeSummary,
                medicationScheduleNote == null ? "" : medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote == null ? "" : medicationComparisonNote,
                nextVisitAt == null ? "" : nextVisitAt
        );
    }

    @Nullable
    private AppointmentFollowUpRecord toAppointmentFollowUpRecord(
            DocumentSnapshot documentSnapshot,
            String requestId
    ) {
        if (!documentSnapshot.exists()) {
            return AppointmentFollowUpRecord.empty(requestId);
        }

        return new AppointmentFollowUpRecord(
                requestId,
                AppointmentFollowUpReviewRating.fromValue(documentSnapshot.getString("reviewRatingCode")),
                resolveTimestampMillis(documentSnapshot.get("reviewSavedAt")),
                AppointmentFollowUpSettlementStatus.fromValue(
                        documentSnapshot.getString("settlementFollowUpStatus")
                ),
                normalizeText(documentSnapshot.getString("settlementFollowUpNote")),
                resolveTimestampMillis(documentSnapshot.get("settlementFollowUpSavedAt")),
                AppointmentFollowUpSupportEscalationStatus.fromValue(
                        documentSnapshot.getString("supportEscalationStatus")
                ),
                resolveTimestampMillis(documentSnapshot.get("supportEscalatedAt"))
        );
    }

    @Nullable
    private SupportInquiry toSupportInquiry(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }

        String managerUserId = documentSnapshot.getString("managerUserId");
        String managerName = documentSnapshot.getString("managerName");
        String title = documentSnapshot.getString("title");
        String body = documentSnapshot.getString("body");
        if (managerUserId == null || managerName == null || title == null || body == null) {
            return null;
        }

        return new SupportInquiry(
                documentSnapshot.getId(),
                managerUserId,
                normalizeText(managerName),
                SupportInquiryCategory.fromValue(documentSnapshot.getString("category")),
                normalizeText(title),
                normalizeText(body),
                resolveSupportInquiryStatus(documentSnapshot.getString("status")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                normalizeText(documentSnapshot.getString("responseText")),
                resolveTimestampMillis(documentSnapshot.get("respondedAt")),
                normalizeText(documentSnapshot.getString("respondedByName"))
        );
    }

    @Nullable
    private String resolveLegacyDocumentStoragePathKey(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return "managerIdCardStoragePath";
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return "managerLicenseStoragePath";
        }
        if (fileType == ManagerDocumentFileType.CRIMINAL_RECORD) {
            return "managerCriminalRecordStoragePath";
        }
        return null;
    }

    private String resolveFileNameFromPath(String fullPath) {
        int separatorIndex = fullPath.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex >= fullPath.length() - 1) {
            return fullPath;
        }
        return fullPath.substring(separatorIndex + 1);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String stringValue(@Nullable Object rawValue) {
        return rawValue instanceof String ? (String) rawValue : null;
    }

    @Nullable
    private String stringifyDate(@Nullable Object rawValue) {
        // Timestamp? 臾몄옄???낅젰??紐⑤몢 媛숈? ?띿뒪??異쒕젰?쇰줈 留욎텣??
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().toString();
        }
        return String.valueOf(rawValue);
    }

    private String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @Nullable
    private Double doubleOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private SupportInquiryStatus resolveSupportInquiryStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return SupportInquiryStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // ?????녿뒗 ?곹깭 媛믪? ?묒닔?⑥쑝濡?蹂댁젙?쒕떎.
            }
        }
        return SupportInquiryStatus.RECEIVED;
    }

    private ManagerDocumentHistoryEventType resolveHistoryEventType(String rawValue) {
        if (rawValue.isEmpty()) {
            return ManagerDocumentHistoryEventType.SUBMITTED;
        }
        try {
            return ManagerDocumentHistoryEventType.valueOf(rawValue);
        } catch (IllegalArgumentException exception) {
            return ManagerDocumentHistoryEventType.SUBMITTED;
        }
    }

    private ManagerDocumentStatus resolveManagerDocumentStatus(
            @Nullable String rawStatus,
            @Nullable String documentSummary
    ) {
        if (rawStatus != null) {
            try {
                return ManagerDocumentStatus.valueOf(rawStatus);
            } catch (IllegalArgumentException ignored) {
                // ?????녿뒗 媛믪? ?꾨옒 湲곕낯 洹쒖튃?쇰줈 蹂댁젙?쒕떎.
            }
        }
        if (normalizeText(documentSummary).isEmpty()) {
            return ManagerDocumentStatus.NOT_SUBMITTED;
        }
        return ManagerDocumentStatus.PENDING_REVIEW;
    }

    private SessionStatus resolveStepStatus(int stepOrder, int totalSteps) {
        // ?④퀎 踰덊샇瑜??붾㈃ ?곷떒 ?곹깭 諛곗????ъ슜?섎뒗 ????곹깭濡?留ㅽ븨?쒕떎.
        if (stepOrder <= 1) {
            return SessionStatus.MEETING;
        }
        if (stepOrder == 2) {
            return SessionStatus.WAITING;
        }
        if (stepOrder <= 4) {
            return SessionStatus.IN_TREATMENT;
        }
        if (stepOrder < totalSteps) {
            return SessionStatus.PAYMENT;
        }
        return SessionStatus.PAYMENT;
    }
}


