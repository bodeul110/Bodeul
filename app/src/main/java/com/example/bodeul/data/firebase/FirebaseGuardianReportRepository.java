package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Firestore 기반 보호자 진행 현황과 리포트를 화면용 모델로 조합한다.
 */
public class FirebaseGuardianReportRepository implements GuardianReportRepository {
    private static final String FUNCTIONS_REGION = "asia-northeast3";
    private final FirebaseFirestore firestore;
    private final FirebaseFunctions functions;

    public FirebaseGuardianReportRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
        this.functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
    }

    @Override
    public void getGuardianDashboard(User currentUser, RepositoryCallback<GuardianReportDashboard> callback) {
        if (currentUser.getRole() != UserRole.GUARDIAN) {
            callback.onError("보호자 계정으로 로그인해주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .whereEqualTo("guardianUserId", currentUser.getId())
                .get()
                .addOnSuccessListener(requestSnapshot -> {
                    List<DocumentSnapshot> requestDocuments = new ArrayList<>(requestSnapshot.getDocuments());
                    requestDocuments.sort((left, right) -> {
                        long rightCreatedAt = resolveCreatedAt(right);
                        long leftCreatedAt = resolveCreatedAt(left);
                        if (rightCreatedAt != leftCreatedAt) {
                            return Long.compare(rightCreatedAt, leftCreatedAt);
                        }
                        return right.getId().compareTo(left.getId());
                    });

                    if (requestDocuments.isEmpty()) {
                        callback.onSuccess(new GuardianReportDashboard(currentUser, new ArrayList<>()));
                        return;
                    }

                    loadEntriesSequentially(
                            currentUser,
                            requestDocuments,
                            0,
                            new ArrayList<>(),
                            callback
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onError("보호자 진행 현황을 불러오지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void loadEntriesSequentially(
            User currentUser,
            List<DocumentSnapshot> requestDocuments,
            int index,
            List<GuardianReportEntry> entries,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        if (index >= requestDocuments.size()) {
            callback.onSuccess(new GuardianReportDashboard(currentUser, entries));
            return;
        }

        AppointmentRequest request = toAppointmentRequest(requestDocuments.get(index));
        if (request == null) {
            loadEntriesSequentially(currentUser, requestDocuments, index + 1, entries, callback);
            return;
        }

        loadGuardianReportEntry(request, new RepositoryCallback<GuardianReportEntry>() {
            @Override
            public void onSuccess(GuardianReportEntry entry) {
                entries.add(entry);
                loadEntriesSequentially(currentUser, requestDocuments, index + 1, entries, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadGuardianReportEntry(
            AppointmentRequest request,
            RepositoryCallback<GuardianReportEntry> callback
    ) {
        Task<QuerySnapshot> sessionTask = firestore.collection("companionSessions")
                .whereEqualTo("appointmentRequestId", request.getId())
                .limit(1)
                .get();
        Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                .whereEqualTo("hospitalName", request.getHospitalName())
                .get();
        Task<User> managerTask = loadAssignedManagerProfile(request.getId());

        Tasks.whenAllSuccess(Arrays.asList(sessionTask, guideTask, managerTask))
                .addOnSuccessListener(results -> {
                    CompanionSession session = toSession((QuerySnapshot) results.get(0));
                    HospitalGuide guide = findGuide(
                            (QuerySnapshot) results.get(1),
                            request.getDepartmentName()
                    );
                    User manager = (User) results.get(2);
                    User patient = toParticipantUser(
                            request.getPatientUserId(),
                            UserRole.PATIENT,
                            request.getPatientName(),
                            request.getPatientEmail(),
                            request.getPatientPhone()
                    );

                    if (session == null) {
                        callback.onSuccess(new GuardianReportEntry(
                                request,
                                patient,
                                manager,
                                null,
                                null,
                                guide
                        ));
                        return;
                    }

                    firestore.collection("sessionReports")
                            .whereEqualTo("sessionId", session.getId())
                            .limit(1)
                            .get()
                            .addOnSuccessListener(reportSnapshot -> callback.onSuccess(
                                    new GuardianReportEntry(
                                            request,
                                            patient,
                                            manager,
                                            session,
                                            toReport(reportSnapshot),
                                            guide
                                    )
                            ))
                            .addOnFailureListener(exception ->
                                    callback.onError("세션 리포트를 불러오지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveAssignedManagerProfileMessage(
                                exception,
                                "보호자 진행 현황을 불러오지 못했습니다."
                        )));
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
                stringOrEmpty(name),
                stringOrEmpty(email),
                stringOrEmpty(phone)
        );
    }

    private Task<User> loadAssignedManagerProfile(String requestId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("requestId", requestId);
        return functions.getHttpsCallable("resolveAssignedManagerProfile")
                .call(payload)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        if (exception != null) {
                            throw exception;
                        }
                        throw new IllegalStateException("매니저 정보를 불러오지 못했습니다.");
                    }
                    return toAssignedManagerUser(task.getResult().getData());
                });
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private User toAssignedManagerUser(@Nullable Object rawData) {
        if (!(rawData instanceof Map)) {
            return null;
        }

        Object managerValue = ((Map<String, Object>) rawData).get("manager");
        if (!(managerValue instanceof Map)) {
            return null;
        }

        Map<String, Object> manager = (Map<String, Object>) managerValue;
        String userId = stringOrEmpty(asString(manager.get("userId"))).trim();
        String roleValue = stringOrEmpty(asString(manager.get("role"))).trim();
        String name = stringOrEmpty(asString(manager.get("name"))).trim();
        String email = stringOrEmpty(asString(manager.get("email"))).trim();
        String phone = stringOrEmpty(asString(manager.get("phone"))).trim();
        if (userId.isEmpty() || name.isEmpty() || email.isEmpty()) {
            return null;
        }
        if (!roleValue.isEmpty() && !UserRole.MANAGER.name().equals(roleValue)) {
            return null;
        }

        return new User(userId, UserRole.MANAGER, name, email, phone);
    }

    private String resolveAssignedManagerProfileMessage(Exception exception, String fallbackMessage) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException = (FirebaseFunctionsException) exception;
            Object details = functionsException.getDetails();
            if (details instanceof Map) {
                String message = asString(((Map<?, ?>) details).get("message"));
                if (message != null && !message.trim().isEmpty()) {
                    return message;
                }
            }
        }
        return fallbackMessage;
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
    private CompanionSession toSession(@Nullable DocumentSnapshot documentSnapshot) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
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
                stringOrEmpty(documentSnapshot.getString("guardianUpdate")),
                stringOrEmpty(documentSnapshot.getString("locationSummary")),
                stringOrEmpty(documentSnapshot.getString("fieldPhotoNote")),
                stringOrEmpty(documentSnapshot.getString("medicationNote")),
                stringOrEmpty(documentSnapshot.getString("pharmacySummary")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("pharmacyCompleted"))
        );
    }

    @Nullable
    private CompanionSession toSession(@Nullable QuerySnapshot querySnapshot) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return toSession(querySnapshot.getDocuments().get(0));
    }

    @Nullable
    private SessionReport toReport(@Nullable DocumentSnapshot documentSnapshot) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            return null;
        }
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

    @Nullable
    private SessionReport toReport(@Nullable QuerySnapshot querySnapshot) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return toReport(querySnapshot.getDocuments().get(0));
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
    private HospitalGuide findGuide(@Nullable QuerySnapshot querySnapshot, String departmentName) {
        if (querySnapshot == null) {
            return null;
        }
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null && guide.getDepartmentName().equals(departmentName)) {
                return guide;
            }
        }
        return null;
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

    private String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Nullable
    private String asString(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
