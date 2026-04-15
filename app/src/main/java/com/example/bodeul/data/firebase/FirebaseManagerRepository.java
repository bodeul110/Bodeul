package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
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
 * Firestore에 저장된 세션, 요청, 가이드, 리포트를 조합해 매니저 화면을 구성한다.
 */
public class FirebaseManagerRepository implements ManagerRepository {
    private final FirebaseFirestore firestore;

    public FirebaseManagerRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // 세션 문서를 기준으로 관련 사용자, 요청, 가이드, 리포트를 병렬로 읽어 대시보드를 완성한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("companionSessions 데이터 형식을 확인해주세요.");
                    return;
                }

                firestore.collection("appointmentRequests")
                        .document(session.getAppointmentRequestId())
                        .get()
                        .addOnSuccessListener(requestSnapshot -> {
                            AppointmentRequest request = toAppointmentRequest(requestSnapshot);
                            if (request == null) {
                                callback.onError("appointmentRequests 데이터를 찾지 못했습니다.");
                                return;
                            }

                            Task<DocumentSnapshot> managerTask = firestore.collection("users")
                                    .document(managerUserId)
                                    .get();
                            Task<DocumentSnapshot> patientTask = firestore.collection("users")
                                    .document(request.getPatientUserId())
                                    .get();
                            Task<DocumentSnapshot> guardianTask = firestore.collection("users")
                                    .document(request.getGuardianUserId())
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
                                    patientTask,
                                    guardianTask,
                                    guideTask,
                                    reportTask
                            );

                            Tasks.whenAllSuccess(tasks)
                                    .addOnSuccessListener(results -> {
                                        User manager = toUser((DocumentSnapshot) results.get(0));
                                        User patient = toUser((DocumentSnapshot) results.get(1));
                                        User guardian = toUser((DocumentSnapshot) results.get(2));
                                        HospitalGuide guide = findGuide(
                                                (QuerySnapshot) results.get(3),
                                                request.getDepartmentName()
                                        );
                                        SessionReport report = toReport((QuerySnapshot) results.get(4));

                                        if (manager == null || patient == null || guardian == null || guide == null) {
                                            callback.onError("Firebase 컬렉션(users, hospitalGuides) 데이터를 확인해주세요.");
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
                                            callback.onError("매니저 데이터를 불러오지 못했습니다."));
                        })
                        .addOnFailureListener(exception ->
                                callback.onError("예약 데이터를 불러오지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // 단계 이동은 현재 세션 문서와 전체 단계 수를 모두 알아야 해서 대시보드를 먼저 읽는다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard dashboard) {
                        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
                        int currentStep = dashboard.getSession().getCurrentStepOrder();
                        if (currentStep >= totalSteps) {
                            callback.onError("마지막 단계입니다. 리포트를 전송해주세요.");
                            return;
                        }

                        int nextStep = currentStep + 1;
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("currentStepOrder", nextStep);
                        updates.put("currentStatus", resolveStepStatus(nextStep, totalSteps).name());
                        updates.put("updatedAt", FieldValue.serverTimestamp());

                        // 세션 단계와 예약 상태를 한 번에 갱신해 화면 간 상태 불일치를 줄인다.
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
                                        callback.onError("다음 단계로 이동하지 못했습니다."));
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
        // 보호자 공유 메시지는 companionSessions 문서의 단일 필드로 관리한다.
        updateSessionField(managerUserId, "guardianUpdate", guardianUpdate, callback);
    }

    @Override
    public void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback) {
        // 약 메모도 같은 세션 문서 안에 저장해 리포트 작성 전에 누적한다.
        updateSessionField(managerUserId, "medicationNote", medicationNote, callback);
    }

    @Override
    public void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError("users 컬렉션에서 매니저 정보를 찾지 못했습니다.");
                        return;
                    }
                    callback.onSuccess(toManagerHomeProfile(documentSnapshot));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 홈 요약 정보를 불러오지 못했습니다."));
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        saveManagerHomeProfileField(
                managerUserId,
                "managerDocumentSummary",
                normalizeText(documentSummary),
                "managerDocumentUpdatedAt",
                "서류 등록 정보를 저장하지 못했습니다.",
                callback
        );
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
                "활동 가능 일정을 저장하지 못했습니다.",
                callback
        );
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // 리포트 저장은 sessionReports 생성과 세션 종료 처리를 함께 반영해야 한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("세션 정보를 찾지 못했습니다.");
                    return;
                }

                Map<String, Object> reportDocument = new HashMap<>();
                reportDocument.put("sessionId", session.getId());
                reportDocument.put("summary", summary);
                reportDocument.put("treatmentNotes", treatmentNotes);
                reportDocument.put("medicationNotes", medicationNotes);
                reportDocument.put("nextVisitAt", nextVisitAt);
                reportDocument.put("createdAt", FieldValue.serverTimestamp());

                // 리포트 저장과 세션 종료 처리를 배치로 묶어 한 번에 반영한다.
                WriteBatch batch = firestore.batch();
                DocumentReference reportReference = firestore.collection("sessionReports")
                        .document("report-" + session.getId());

                batch.set(reportReference, reportDocument);
                batch.update(sessionSnapshot.getReference(), "currentStatus", SessionStatus.COMPLETED.name());
                batch.update(sessionSnapshot.getReference(), "medicationNote", medicationNotes);
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
                                callback.onError("리포트를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
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
        // 단일 메모 수정도 저장 후 대시보드를 다시 읽어 화면 상태를 한 구조로 유지한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(key, value);
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("세션 정보를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadSessionDocument(String managerUserId, RepositoryCallback<DocumentSnapshot> callback) {
        // 현재 구현은 매니저당 진행 중인 동행 세션 1건을 기준으로 화면을 구성한다.
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
                        callback.onError("동행 세션 정보를 불러오지 못했습니다."));
    }

    private boolean isActiveSession(CompanionSession session) {
        return session.getStatus() != SessionStatus.COMPLETED
                && session.getStatus() != SessionStatus.CANCELED;
    }

    private ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot) {
        String documentSummary = documentSnapshot.getString("managerDocumentSummary");
        String availabilitySummary = documentSnapshot.getString("managerAvailabilitySummary");
        return new ManagerHomeProfile(
                documentSummary == null ? "" : documentSummary,
                availabilitySummary == null ? "" : availabilitySummary
        );
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        // users 컬렉션 문서를 앱의 User 모델로 변환한다.
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
    private AppointmentRequest toAppointmentRequest(DocumentSnapshot documentSnapshot) {
        // appointmentRequests 문서를 일정 카드에 바로 쓸 수 있는 모델로 정리한다.
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
                stringOrEmpty(documentSnapshot.getString("guardianEmail"))
        );
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        // currentStepOrder와 currentStatus를 읽어 단계형 UI 상태를 복원한다.
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
    private HospitalGuide findGuide(QuerySnapshot querySnapshot, String departmentName) {
        // 병원명으로 먼저 좁힌 뒤 진료과가 일치하는 가이드를 한 번 더 찾는다.
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
        // Firestore 배열 형태의 steps를 GuideStep 목록으로 변환한다.
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
            // 각 단계는 order/title/description 3개 필드를 가진 맵이라고 가정한다.
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
        // 현재 구현은 세션당 리포트 1개를 가정하고 첫 문서만 사용한다.
        if (querySnapshot.isEmpty()) {
            return null;
        }
        DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
        String sessionId = documentSnapshot.getString("sessionId");
        String summary = documentSnapshot.getString("summary");
        String treatmentNotes = documentSnapshot.getString("treatmentNotes");
        String medicationNotes = documentSnapshot.getString("medicationNotes");
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
                nextVisitAt == null ? "" : nextVisitAt
        );
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String stringifyDate(@Nullable Object rawValue) {
        // Timestamp와 문자열 입력을 모두 같은 텍스트 출력으로 맞춘다.
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

    private SessionStatus resolveStepStatus(int stepOrder, int totalSteps) {
        // 단계 번호를 화면 상단 상태 배지에 사용하는 대표 상태로 매핑한다.
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
