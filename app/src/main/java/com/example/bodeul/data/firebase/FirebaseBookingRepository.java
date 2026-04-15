package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.UserProfileSanitizer;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Firestore에 병원 동행 신청을 저장하고 환자/보호자 계정 연결을 시도하는 저장소다.
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
            callback.onError("환자 또는 보호자 계정으로 접근해 주세요.");
            return;
        }

        query.get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(toSortedRequests(querySnapshot)))
                .addOnFailureListener(exception ->
                        callback.onError("요청 목록을 불러오지 못했습니다."));
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해 주세요.");
            return;
        }

        ParticipantSnapshot linkedParticipantInput = new ParticipantSnapshot(
                UserProfileSanitizer.normalizeName(linkedParticipantName),
                UserProfileSanitizer.normalizePhone(linkedParticipantPhone),
                UserProfileSanitizer.normalizeEmail(linkedParticipantEmail)
        );

        resolveLinkedParticipant(
                resolveCounterpartRole(currentUser.getRole()),
                linkedParticipantInput.email,
                linkedParticipantInput.phone,
                new LinkedParticipantCallback() {
                    @Override
                    public void onResolved(@Nullable User linkedParticipant) {
                        Map<String, Object> requestDocument = buildRequestDocument(
                                currentUser,
                                hospitalName,
                                departmentName,
                                appointmentAt,
                                meetingPlace,
                                specialNotes,
                                linkedParticipantInput,
                                linkedParticipant
                        );

                        firestore.collection("appointmentRequests")
                                .add(requestDocument)
                                .addOnSuccessListener(documentReference ->
                                        documentReference.get()
                                                .addOnSuccessListener(documentSnapshot -> {
                                                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                                                    if (request == null) {
                                                        callback.onError("요청 정보를 다시 불러오지 못했습니다.");
                                                        return;
                                                    }
                                                    callback.onSuccess(request);
                                                })
                                                .addOnFailureListener(exception ->
                                                        callback.onError("요청 정보를 다시 불러오지 못했습니다.")))
                                .addOnFailureListener(exception ->
                                        callback.onError("동행 요청을 저장하지 못했습니다."));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
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

    private Map<String, Object> buildRequestDocument(
            User currentUser,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            ParticipantSnapshot linkedParticipantInput,
            @Nullable User linkedParticipant
    ) {
        ParticipantSnapshot currentParticipantSnapshot = new ParticipantSnapshot(
                UserProfileSanitizer.normalizeName(currentUser.getName()),
                UserProfileSanitizer.normalizePhone(currentUser.getPhone()),
                UserProfileSanitizer.normalizeEmail(currentUser.getEmail())
        );
        ParticipantSnapshot linkedParticipantSnapshot = linkedParticipant == null
                ? linkedParticipantInput
                : new ParticipantSnapshot(
                        UserProfileSanitizer.normalizeName(linkedParticipant.getName()),
                        UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone()),
                        UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail())
                );

        Map<String, Object> requestDocument = new HashMap<>();
        if (currentUser.getRole() == UserRole.PATIENT) {
            applyPatientFields(requestDocument, currentUser.getId(), currentParticipantSnapshot);
            applyGuardianFields(requestDocument, linkedParticipant == null ? "" : linkedParticipant.getId(), linkedParticipantSnapshot);
        } else {
            applyPatientFields(requestDocument, linkedParticipant == null ? "" : linkedParticipant.getId(), linkedParticipantSnapshot);
            applyGuardianFields(requestDocument, currentUser.getId(), currentParticipantSnapshot);
        }

        requestDocument.put("hospitalName", normalizeText(hospitalName));
        requestDocument.put("departmentName", normalizeText(departmentName));
        requestDocument.put("appointmentAt", normalizeText(appointmentAt));
        requestDocument.put("meetingPlace", normalizeText(meetingPlace));
        requestDocument.put("specialNotes", normalizeText(specialNotes));

        ParsedAppointmentAt parsedAppointmentAt = parseAppointmentAt(appointmentAt);
        if (parsedAppointmentAt != null) {
            requestDocument.put("appointmentAtEpochMillis", parsedAppointmentAt.epochMillis);
            requestDocument.put("appointmentDateKey", parsedAppointmentAt.dateKey);
        }

        requestDocument.put("reminderStages", Arrays.asList("D7", "D3", "D1"));
        requestDocument.put("status", AppointmentStatus.REQUESTED.name());
        requestDocument.put("managerUserId", null);
        requestDocument.put("requesterUserId", currentUser.getId());
        requestDocument.put("requesterRole", currentUser.getRole().name());
        requestDocument.put("requesterName", currentParticipantSnapshot.name);
        requestDocument.put("requesterPhone", currentParticipantSnapshot.phone);
        requestDocument.put("createdAt", FieldValue.serverTimestamp());
        return requestDocument;
    }

    private void applyPatientFields(Map<String, Object> requestDocument, String patientUserId, ParticipantSnapshot patientSnapshot) {
        requestDocument.put("patientUserId", normalizeText(patientUserId));
        requestDocument.put("patientName", patientSnapshot.name);
        requestDocument.put("patientPhone", patientSnapshot.phone);
        requestDocument.put("patientEmail", patientSnapshot.email);
    }

    private void applyGuardianFields(Map<String, Object> requestDocument, String guardianUserId, ParticipantSnapshot guardianSnapshot) {
        requestDocument.put("guardianUserId", normalizeText(guardianUserId));
        requestDocument.put("guardianName", guardianSnapshot.name);
        requestDocument.put("guardianPhone", guardianSnapshot.phone);
        requestDocument.put("guardianEmail", guardianSnapshot.email);
    }

    private void resolveLinkedParticipant(
            UserRole expectedRole,
            String email,
            String phone,
            LinkedParticipantCallback callback
    ) {
        if (!email.isEmpty()) {
            firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        User matchedUser = findMatchedUser(querySnapshot, expectedRole);
                        if (matchedUser != null || phone.isEmpty()) {
                            callback.onResolved(matchedUser);
                            return;
                        }
                        resolveLinkedParticipantByPhone(expectedRole, phone, callback);
                    })
                    .addOnFailureListener(exception ->
                            callback.onError("연결할 계정 정보를 확인하지 못했습니다."));
            return;
        }

        resolveLinkedParticipantByPhone(expectedRole, phone, callback);
    }

    private void resolveLinkedParticipantByPhone(
            UserRole expectedRole,
            String phone,
            LinkedParticipantCallback callback
    ) {
        if (phone.isEmpty()) {
            callback.onResolved(null);
            return;
        }

        firestore.collection("users")
                .whereEqualTo("phone", phone)
                .get()
                .addOnSuccessListener(querySnapshot ->
                        callback.onResolved(findMatchedUser(querySnapshot, expectedRole)))
                .addOnFailureListener(exception ->
                        callback.onError("연결할 계정 정보를 확인하지 못했습니다."));
    }

    @Nullable
    private User findMatchedUser(QuerySnapshot querySnapshot, UserRole expectedRole) {
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            User user = toUser(documentSnapshot);
            if (user != null && user.getRole() == expectedRole) {
                return user;
            }
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
                UserProfileSanitizer.normalizeName(name),
                UserProfileSanitizer.normalizeEmail(email),
                UserProfileSanitizer.normalizePhone(phone)
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
                stringOrEmpty(documentSnapshot.getString("guardianEmail"))
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

    private String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private String normalizeText(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }

    @Nullable
    private ParsedAppointmentAt parseAppointmentAt(String rawValue) {
        String normalizedValue = normalizeText(rawValue);
        if (normalizedValue.isEmpty()) {
            return null;
        }

        // 예약 알림 스케줄러가 같은 기준으로 계산할 수 있도록 입력값을 서울 시간 기준으로 파싱한다.
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        try {
            Date parsedDate = parser.parse(normalizedValue);
            if (parsedDate == null) {
                return null;
            }

            SimpleDateFormat dateKeyFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            dateKeyFormatter.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            return new ParsedAppointmentAt(
                    parsedDate.getTime(),
                    dateKeyFormatter.format(parsedDate)
            );
        } catch (ParseException exception) {
            return null;
        }
    }

    private boolean supportsRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }

    private UserRole resolveCounterpartRole(UserRole requesterRole) {
        return requesterRole == UserRole.PATIENT ? UserRole.GUARDIAN : UserRole.PATIENT;
    }

    private interface LinkedParticipantCallback {
        void onResolved(@Nullable User linkedParticipant);

        void onError(String message);
    }

    private static final class ParticipantSnapshot {
        private final String name;
        private final String phone;
        private final String email;

        private ParticipantSnapshot(String name, String phone, String email) {
            this.name = name;
            this.phone = phone;
            this.email = email;
        }
    }

    private static final class ParsedAppointmentAt {
        private final long epochMillis;
        private final String dateKey;

        private ParsedAppointmentAt(long epochMillis, String dateKey) {
            this.epochMillis = epochMillis;
            this.dateKey = dateKey;
        }
    }
}
