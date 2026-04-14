package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore에 저장된 병원 동행 신청을 환자와 보호자 화면에 연결하는 저장소다.
 */
public class FirebaseBookingRepository implements BookingRepository {
    private final FirebaseFirestore firestore;

    public FirebaseBookingRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback) {
        Query query = buildUserQuery(currentUser);
        if (query == null) {
            callback.onError("환자 또는 보호자 계정으로 접근해주세요.");
            return;
        }

        query.get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(toSortedRequests(querySnapshot)))
                .addOnFailureListener(exception ->
                        callback.onError("신청 목록을 불러오지 못했습니다."));
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해주세요.");
            return;
        }

        Map<String, Object> requestDocument = new HashMap<>();
        requestDocument.put("patientUserId", currentUser.getRole() == UserRole.PATIENT ? currentUser.getId() : "");
        requestDocument.put("guardianUserId", currentUser.getRole() == UserRole.GUARDIAN ? currentUser.getId() : "");
        requestDocument.put("hospitalName", normalizeText(hospitalName));
        requestDocument.put("departmentName", normalizeText(departmentName));
        requestDocument.put("appointmentAt", normalizeText(appointmentAt));
        requestDocument.put("meetingPlace", normalizeText(meetingPlace));
        requestDocument.put("specialNotes", normalizeText(specialNotes));
        requestDocument.put("status", AppointmentStatus.REQUESTED.name());
        requestDocument.put("managerUserId", null);
        requestDocument.put("requesterUserId", currentUser.getId());
        requestDocument.put("requesterRole", currentUser.getRole().name());
        requestDocument.put("requesterName", currentUser.getName());
        requestDocument.put("requesterPhone", currentUser.getPhone());
        requestDocument.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("appointmentRequests")
                .add(requestDocument)
                .addOnSuccessListener(documentReference ->
                        documentReference.get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                                    if (request == null) {
                                        callback.onError("신청 정보를 다시 불러오지 못했습니다.");
                                        return;
                                    }
                                    callback.onSuccess(request);
                                })
                                .addOnFailureListener(exception ->
                                        callback.onError("신청 정보를 다시 불러오지 못했습니다.")))
                .addOnFailureListener(exception ->
                        callback.onError("동행 신청을 저장하지 못했습니다."));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    @Nullable
    private Query buildUserQuery(User currentUser) {
        if (currentUser.getRole() == UserRole.PATIENT) {
            return firestore.collection("appointmentRequests")
                    .whereEqualTo("patientUserId", currentUser.getId());
        }
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            return firestore.collection("appointmentRequests")
                    .whereEqualTo("guardianUserId", currentUser.getId());
        }
        return null;
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
                managerUserId
        );
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

    private String normalizeText(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }

    private boolean supportsRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }
}
