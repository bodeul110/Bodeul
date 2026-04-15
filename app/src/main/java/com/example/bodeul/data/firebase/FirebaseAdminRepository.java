package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore에 저장된 요청, 매니저, 가이드를 조합해 관리자 운영 화면에 연결한다.
 */
public class FirebaseAdminRepository implements AdminRepository {
    private final FirebaseFirestore firestore;

    public FirebaseAdminRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getAdminDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        loadDashboard(currentUser, callback);
    }

    @Override
    public void assignManager(
            User currentUser,
            String requestId,
            String managerUserId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        loadDashboard(currentUser, new RepositoryCallback<AdminDashboard>() {
            @Override
            public void onSuccess(AdminDashboard dashboard) {
                AdminRequestOverview targetOverview = findRequestOverview(dashboard, requestId);
                if (targetOverview == null) {
                    callback.onError("배정할 요청을 찾지 못했습니다.");
                    return;
                }
                if (!targetOverview.hasLinkedParticipants()) {
                    callback.onError("환자와 보호자 계정 연결이 완료된 요청만 배정할 수 있습니다.");
                    return;
                }
                if (!targetOverview.hasGuide()) {
                    callback.onError("해당 병원과 진료과 가이드가 없어 먼저 가이드를 등록해야 합니다.");
                    return;
                }
                if (!containsManager(dashboard.getAvailableManagers(), managerUserId)) {
                    callback.onError("선택한 매니저는 현재 다른 동행을 진행 중입니다.");
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
                sessionDocument.put("managerUserId", managerUserId);
                sessionDocument.put("currentStepOrder", 1);
                sessionDocument.put("currentStatus", SessionStatus.READY.name());
                sessionDocument.put("guardianUpdate", "");
                sessionDocument.put("medicationNote", "");
                sessionDocument.put("createdAt", FieldValue.serverTimestamp());
                sessionDocument.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(sessionReference, sessionDocument);

                batch.commit()
                        .addOnSuccessListener(unused -> loadDashboard(currentUser, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("매니저 배정을 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        List<Map<String, Object>> steps = buildGuideStepDocuments(stepLines);
        if (steps.isEmpty()) {
            callback.onError("병원 가이드를 저장하지 못했습니다. 단계 내용을 다시 확인해주세요.");
            return;
        }

        String normalizedHospital = normalizeText(hospitalName);
        String normalizedDepartment = normalizeText(departmentName);
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
                                .addOnSuccessListener(unused -> loadDashboard(currentUser, callback))
                                .addOnFailureListener(exception ->
                                        callback.onError("병원 가이드를 저장하지 못했습니다."));
                        return;
                    }

                    targetReference.update(guideDocument)
                            .addOnSuccessListener(unused -> loadDashboard(currentUser, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("병원 가이드를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("기존 병원 가이드를 확인하지 못했습니다."));
    }

    @Override
    public void deleteHospitalGuide(
            User currentUser,
            String guideId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        firestore.collection("hospitalGuides")
                .document(guideId)
                .delete()
                .addOnSuccessListener(unused -> loadDashboard(currentUser, callback))
                .addOnFailureListener(exception ->
                        callback.onError("병원 가이드를 삭제하지 못했습니다."));
    }

    @Override
    public void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            callback.onError("서류 검토 상태가 올바르지 않습니다.");
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("매니저 계정을 찾지 못했습니다.");
                        return;
                    }

                    ManagerHomeProfile profile = toManagerHomeProfile(documentSnapshot);
                    if (normalizeText(profile.getDocumentSummary()).isEmpty()) {
                        callback.onError("검토할 서류 요약이 아직 등록되지 않았습니다.");
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("managerDocumentStatus", status.name());
                    updates.put("managerDocumentReviewNote", normalizeText(reviewNote));
                    updates.put("managerDocumentReviewedAt", FieldValue.serverTimestamp());

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> loadDashboard(currentUser, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("매니저 서류 검토 상태를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 계정 정보를 불러오지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void loadDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        firestore.collection("appointmentRequests")
                .get()
                .addOnSuccessListener(requestSnapshot ->
                        firestore.collection("users")
                                .get()
                                .addOnSuccessListener(userSnapshot ->
                                        firestore.collection("companionSessions")
                                                .get()
                                                .addOnSuccessListener(sessionSnapshot ->
                                                        firestore.collection("hospitalGuides")
                                                                .get()
                                                                .addOnSuccessListener(guideSnapshot -> callback.onSuccess(
                                                                        buildDashboard(
                                                                                currentUser,
                                                                                requestSnapshot,
                                                                                userSnapshot,
                                                                                sessionSnapshot,
                                                                                guideSnapshot
                                                                        )
                                                                ))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("병원 가이드 목록을 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("동행 세션 목록을 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("사용자 목록을 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("운영 요청 목록을 불러오지 못했습니다."));
    }

    private AdminDashboard buildDashboard(
            User currentUser,
            QuerySnapshot requestSnapshot,
            QuerySnapshot userSnapshot,
            QuerySnapshot sessionSnapshot,
            QuerySnapshot guideSnapshot
    ) {
        List<AppointmentRequest> requests = toSortedRequests(requestSnapshot);

        Map<String, User> usersById = new HashMap<>();
        List<String> activeManagerIds = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null
                    && session.getStatus() != SessionStatus.COMPLETED
                    && session.getStatus() != SessionStatus.CANCELED) {
                activeManagerIds.add(session.getManagerUserId());
            }
        }

        List<User> availableManagers = new ArrayList<>();
        List<User> busyManagers = new ArrayList<>();
        List<ManagerDocumentOverview> managerDocumentOverviews = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : userSnapshot.getDocuments()) {
            User user = toUser(documentSnapshot);
            if (user == null) {
                continue;
            }
            usersById.put(user.getId(), user);
            if (user.getRole() != UserRole.MANAGER) {
                continue;
            }
            if (activeManagerIds.contains(user.getId())) {
                busyManagers.add(user);
            } else {
                availableManagers.add(user);
            }
            managerDocumentOverviews.add(new ManagerDocumentOverview(
                    user,
                    toManagerHomeProfile(documentSnapshot)
            ));
        }

        Map<String, CompanionSession> sessionsByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null) {
                sessionsByRequestId.put(session.getAppointmentRequestId(), session);
            }
        }

        List<HospitalGuide> guides = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : guideSnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null) {
                guides.add(guide);
            }
        }

        List<AdminRequestOverview> pendingRequests = new ArrayList<>();
        List<AdminRequestOverview> managedRequests = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            AdminRequestOverview overview = new AdminRequestOverview(
                    request,
                    usersById.get(request.getPatientUserId()),
                    usersById.get(request.getGuardianUserId()),
                    usersById.get(request.getManagerUserId()),
                    sessionsByRequestId.get(request.getId()),
                    hasGuide(guides, request.getHospitalName(), request.getDepartmentName()),
                    hasLinkedParticipants(request)
            );

            if (request.getStatus() == AppointmentStatus.REQUESTED) {
                pendingRequests.add(overview);
            } else {
                managedRequests.add(overview);
            }
        }

        return new AdminDashboard(
                currentUser,
                availableManagers,
                busyManagers,
                managerDocumentOverviews,
                pendingRequests,
                managedRequests,
                guides
        );
    }

    private List<AppointmentRequest> toSortedRequests(QuerySnapshot querySnapshot) {
        List<DocumentSnapshot> documents = new ArrayList<>(querySnapshot.getDocuments());
        documents.sort((left, right) -> {
            long rightCreatedAt = resolveCreatedAt(right);
            long leftCreatedAt = resolveCreatedAt(left);
            if (rightCreatedAt != leftCreatedAt) {
                return Long.compare(rightCreatedAt, leftCreatedAt);
            }
            return right.getId().compareTo(left.getId());
        });

        List<AppointmentRequest> requests = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : documents) {
            AppointmentRequest request = toAppointmentRequest(documentSnapshot);
            if (request != null) {
                requests.add(request);
            }
        }
        return requests;
    }

    private long resolveCreatedAt(DocumentSnapshot documentSnapshot) {
        Object rawCreatedAt = documentSnapshot.get("createdAt");
        if (rawCreatedAt instanceof Timestamp) {
            return ((Timestamp) rawCreatedAt).toDate().getTime();
        }
        if (rawCreatedAt instanceof Number) {
            return ((Number) rawCreatedAt).longValue();
        }
        return 0L;
    }

    @Nullable
    private AdminRequestOverview findRequestOverview(AdminDashboard dashboard, String requestId) {
        for (AdminRequestOverview overview : dashboard.getPendingRequests()) {
            if (overview.getAppointmentRequest().getId().equals(requestId)) {
                return overview;
            }
        }
        for (AdminRequestOverview overview : dashboard.getManagedRequests()) {
            if (overview.getAppointmentRequest().getId().equals(requestId)) {
                return overview;
            }
        }
        return null;
    }

    private boolean containsManager(List<User> managers, String managerUserId) {
        for (User manager : managers) {
            if (manager.getId().equals(managerUserId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGuide(List<HospitalGuide> guides, String hospitalName, String departmentName) {
        for (HospitalGuide guide : guides) {
            if (guide.getHospitalName().equals(hospitalName)
                    && guide.getDepartmentName().equals(departmentName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLinkedParticipants(AppointmentRequest request) {
        return !normalizeText(request.getPatientUserId()).isEmpty()
                && !normalizeText(request.getGuardianUserId()).isEmpty();
    }

    private List<Map<String, Object>> buildGuideStepDocuments(List<String> stepLines) {
        List<Map<String, Object>> steps = new ArrayList<>();
        int order = 1;
        for (String rawLine : stepLines) {
            String line = normalizeText(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = splitGuideLine(line, order);
            Map<String, Object> stepDocument = new HashMap<>();
            stepDocument.put("order", order);
            stepDocument.put("title", parts[0]);
            stepDocument.put("description", parts[1]);
            steps.add(stepDocument);
            order++;
        }
        return steps;
    }

    private String[] splitGuideLine(String line, int order) {
        int separatorIndex = findGuideSeparatorIndex(line);
        if (separatorIndex < 0) {
            return new String[]{"단계 " + order, line};
        }
        String title = normalizeText(line.substring(0, separatorIndex));
        String description = normalizeText(line.substring(separatorIndex + 1));
        if (title.isEmpty() || description.isEmpty()) {
            return new String[]{"단계 " + order, line};
        }
        return new String[]{title, description};
    }

    private int findGuideSeparatorIndex(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0) {
            return colonIndex;
        }
        int barIndex = line.indexOf('|');
        if (barIndex >= 0) {
            return barIndex;
        }
        return line.indexOf('-');
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
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

    private ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot) {
        String documentSummary = documentSnapshot.getString("managerDocumentSummary");
        String availabilitySummary = documentSnapshot.getString("managerAvailabilitySummary");
        String documentStatus = documentSnapshot.getString("managerDocumentStatus");
        String documentReviewNote = documentSnapshot.getString("managerDocumentReviewNote");
        return new ManagerHomeProfile(
                normalizeText(documentSummary),
                normalizeText(availabilitySummary),
                resolveManagerDocumentStatus(documentStatus, documentSummary),
                normalizeText(documentReviewNote)
        );
    }

    @Nullable
    private AppointmentRequest toAppointmentRequest(DocumentSnapshot documentSnapshot) {
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
                normalizeText(documentSnapshot.getString("patientName")),
                normalizeText(documentSnapshot.getString("patientPhone")),
                normalizeText(documentSnapshot.getString("patientEmail")),
                normalizeText(documentSnapshot.getString("guardianName")),
                normalizeText(documentSnapshot.getString("guardianPhone")),
                normalizeText(documentSnapshot.getString("guardianEmail"))
        );
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String appointmentRequestId = documentSnapshot.getString("appointmentRequestId");
        String managerUserId = documentSnapshot.getString("managerUserId");
        Long currentStepOrder = documentSnapshot.getLong("currentStepOrder");
        String statusValue = documentSnapshot.getString("currentStatus");
        if (appointmentRequestId == null || managerUserId == null || currentStepOrder == null || statusValue == null) {
            return null;
        }

        return new CompanionSession(
                documentSnapshot.getId(),
                appointmentRequestId,
                managerUserId,
                currentStepOrder.intValue(),
                SessionStatus.valueOf(statusValue),
                documentSnapshot.getString("guardianUpdate"),
                documentSnapshot.getString("medicationNote")
        );
    }

    @Nullable
    private HospitalGuide toGuide(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        Object stepsValue = documentSnapshot.get("steps");
        if (hospitalName == null || departmentName == null || !(stepsValue instanceof List)) {
            return null;
        }

        List<GuideStep> steps = new ArrayList<>();
        for (Object rawStep : (List<?>) stepsValue) {
            if (!(rawStep instanceof Map)) {
                continue;
            }
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
    private String stringifyDate(@Nullable Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().toString();
        }
        return String.valueOf(rawValue);
    }

    private String normalizeText(@Nullable String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }

    private ManagerDocumentStatus resolveManagerDocumentStatus(
            @Nullable String rawStatus,
            @Nullable String documentSummary
    ) {
        if (rawStatus != null) {
            try {
                return ManagerDocumentStatus.valueOf(rawStatus);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 기본 규칙으로 보정한다.
            }
        }
        if (normalizeText(documentSummary).isEmpty()) {
            return ManagerDocumentStatus.NOT_SUBMITTED;
        }
        return ManagerDocumentStatus.PENDING_REVIEW;
    }
}
