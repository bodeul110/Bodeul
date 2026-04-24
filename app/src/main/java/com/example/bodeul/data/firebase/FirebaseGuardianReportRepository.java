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
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore에 저장된 보호자 진행 현황과 리포트를 한 화면에 조합해 전달한다.
 */
public class FirebaseGuardianReportRepository implements GuardianReportRepository {
    private final FirebaseFirestore firestore;

    public FirebaseGuardianReportRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getGuardianDashboard(User currentUser, RepositoryCallback<GuardianReportDashboard> callback) {
        if (currentUser.getRole() != UserRole.GUARDIAN) {
            callback.onError("보호자 계정으로 접근해주세요.");
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

                    loadRelatedCollections(currentUser, requestDocuments, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("보호자 진행 현황을 불러오지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void loadRelatedCollections(
            User currentUser,
            List<DocumentSnapshot> requestDocuments,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        firestore.collection("companionSessions")
                .get()
                .addOnSuccessListener(sessionSnapshot ->
                        firestore.collection("sessionReports")
                                .get()
                                .addOnSuccessListener(reportSnapshot ->
                                        firestore.collection("users")
                                                .get()
                                                .addOnSuccessListener(userSnapshot ->
                                                        firestore.collection("hospitalGuides")
                                                                .get()
                                                                .addOnSuccessListener(guideSnapshot -> callback.onSuccess(
                                                                        new GuardianReportDashboard(
                                                                                currentUser,
                                                                                buildEntries(
                                                                                        requestDocuments,
                                                                                        sessionSnapshot,
                                                                                        reportSnapshot,
                                                                                        userSnapshot,
                                                                                        guideSnapshot
                                                                                )
                                                                        )
                                                                ))
                                                                .addOnFailureListener(exception ->
                                                                        callback.onError("병원 가이드를 불러오지 못했습니다.")))
                                                .addOnFailureListener(exception ->
                                                        callback.onError("사용자 정보를 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("세션 리포트를 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("동행 세션 정보를 불러오지 못했습니다."));
    }

    private List<GuardianReportEntry> buildEntries(
            List<DocumentSnapshot> requestDocuments,
            QuerySnapshot sessionSnapshot,
            QuerySnapshot reportSnapshot,
            QuerySnapshot userSnapshot,
            QuerySnapshot guideSnapshot
    ) {
        Map<String, CompanionSession> sessionsByRequestId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : sessionSnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null) {
                sessionsByRequestId.put(session.getAppointmentRequestId(), session);
            }
        }

        Map<String, SessionReport> reportsBySessionId = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : reportSnapshot.getDocuments()) {
            SessionReport report = toReport(documentSnapshot);
            if (report != null) {
                reportsBySessionId.put(report.getSessionId(), report);
            }
        }

        Map<String, User> usersById = new HashMap<>();
        for (DocumentSnapshot documentSnapshot : userSnapshot.getDocuments()) {
            User user = toUser(documentSnapshot);
            if (user != null) {
                usersById.put(user.getId(), user);
            }
        }

        List<HospitalGuide> guides = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : guideSnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null) {
                guides.add(guide);
            }
        }

        List<GuardianReportEntry> entries = new ArrayList<>();
        for (DocumentSnapshot requestDocument : requestDocuments) {
            AppointmentRequest request = toAppointmentRequest(requestDocument);
            if (request == null) {
                continue;
            }
            CompanionSession session = sessionsByRequestId.get(request.getId());
            entries.add(new GuardianReportEntry(
                    request,
                    usersById.get(request.getPatientUserId()),
                    usersById.get(request.getManagerUserId()),
                    session,
                    session == null ? null : reportsBySessionId.get(session.getId()),
                    findGuide(guides, request.getHospitalName(), request.getDepartmentName())
            ));
        }
        return entries;
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
                stringOrEmpty(documentSnapshot.getString("guardianUpdate")),
                stringOrEmpty(documentSnapshot.getString("locationSummary")),
                stringOrEmpty(documentSnapshot.getString("fieldPhotoNote")),
                stringOrEmpty(documentSnapshot.getString("medicationNote"))
        );
    }

    @Nullable
    private SessionReport toReport(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
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
    private HospitalGuide findGuide(List<HospitalGuide> guides, String hospitalName, String departmentName) {
        for (HospitalGuide guide : guides) {
            if (guide.getHospitalName().equals(hospitalName)
                    && guide.getDepartmentName().equals(departmentName)) {
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

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
