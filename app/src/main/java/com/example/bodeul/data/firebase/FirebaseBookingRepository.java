package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingPaymentApproval;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.UserProfileSanitizer;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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
    public void getHospitalOptions(RepositoryCallback<List<BookingHospitalOption>> callback) {
        firestore.collection("hospitalGuides")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<HospitalGuide> guides = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        HospitalGuide guide = toGuide(documentSnapshot);
                        if (guide != null) {
                            guides.add(guide);
                        }
                    }
                    callback.onSuccess(toHospitalOptions(guides));
                })
                .addOnFailureListener(exception ->
                        callback.onError("병원 선택 목록을 불러오지 못했습니다."));
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
    public void getAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("요청 상세 정보를 확인하지 못했습니다.");
                        return;
                    }
                    loadAppointmentRequestDetail(request, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("요청 상세 정보를 확인하지 못했습니다."));
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해 주세요.");
            return;
        }

        ParticipantSnapshot linkedParticipantInput = new ParticipantSnapshot(
                UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName()),
                UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone()),
                UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail())
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
                                bookingRequestDraft,
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
    public void updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해 주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest existingRequest = toAppointmentRequest(documentSnapshot);
                    if (!canMutateRequest(currentUser, existingRequest)) {
                        callback.onError("접수 대기 상태 요청만 수정할 수 있습니다.");
                        return;
                    }

                    ParticipantSnapshot linkedParticipantInput = new ParticipantSnapshot(
                            UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName()),
                            UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone()),
                            UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail())
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
                                            bookingRequestDraft,
                                            linkedParticipantInput,
                                            linkedParticipant
                                    );
                                    requestDocument.remove("createdAt");
                                    requestDocument.put("updatedAt", FieldValue.serverTimestamp());

                                    documentSnapshot.getReference()
                                            .update(requestDocument)
                                            .addOnSuccessListener(unused ->
                                                    reloadAppointmentRequest(documentSnapshot.getReference(), callback))
                                            .addOnFailureListener(exception ->
                                                    callback.onError("동행 요청을 수정하지 못했습니다."));
                                }

                                @Override
                                public void onError(String message) {
                                    callback.onError(message);
                                }
                            }
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onError("요청 정보를 확인하지 못했습니다."));
    }

    @Override
    public void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 접근해 주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest existingRequest = toAppointmentRequest(documentSnapshot);
                    if (!canCancelRequest(currentUser, existingRequest)) {
                        callback.onError("접수 대기 또는 매니저 배정 완료 상태 요청만 취소할 수 있습니다.");
                        return;
                    }

                    firestore.collection("companionSessions")
                            .whereEqualTo("appointmentRequestId", requestId)
                            .get()
                            .addOnSuccessListener(sessionSnapshot -> {
                                WriteBatch batch = firestore.batch();
                                batch.update(
                                        documentSnapshot.getReference(),
                                        "status", AppointmentStatus.CANCELED.name(),
                                        "updatedAt", FieldValue.serverTimestamp()
                                );

                                for (DocumentSnapshot sessionDocument : sessionSnapshot.getDocuments()) {
                                    String currentStatus = sessionDocument.getString("currentStatus");
                                    if (SessionStatus.COMPLETED.name().equals(currentStatus)
                                            || SessionStatus.CANCELED.name().equals(currentStatus)) {
                                        continue;
                                    }
                                    batch.update(
                                            sessionDocument.getReference(),
                                            "currentStatus", SessionStatus.CANCELED.name(),
                                            "updatedAt", FieldValue.serverTimestamp()
                                    );
                                }

                                batch.commit()
                                        .addOnSuccessListener(unused ->
                                                reloadAppointmentRequest(documentSnapshot.getReference(), callback))
                                        .addOnFailureListener(exception ->
                                                callback.onError("동행 요청을 취소하지 못했습니다."));
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("연결된 동행 세션을 확인하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("요청 정보를 확인하지 못했습니다."));
    }

    @Override
    public void getAppointmentFollowUp(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("후속 정보 조회 권한이 없습니다.");
                        return;
                    }

                    firestore.collection("appointmentFollowUps")
                            .document(requestId)
                            .get()
                            .addOnSuccessListener(followUpSnapshot ->
                                    callback.onSuccess(toAppointmentFollowUpRecord(followUpSnapshot, requestId)))
                            .addOnFailureListener(exception ->
                                    callback.onError("후속 정보를 불러오지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("후속 정보를 불러오지 못했습니다."));
    }

    @Override
    public void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("환자 또는 보호자 계정으로 로그인해 주세요.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("후기 저장 권한이 없습니다.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("완료된 예약에서만 후기를 저장할 수 있습니다.");
                        return;
                    }

                    Map<String, Object> followUpDocument = new HashMap<>();
                    followUpDocument.put("requestId", requestId);
                    followUpDocument.put("reviewRatingCode", reviewRating.getValue());
                    followUpDocument.put("reviewSavedByUserId", currentUser.getId());
                    followUpDocument.put("reviewSavedAt", FieldValue.serverTimestamp());
                    followUpDocument.put("updatedAt", FieldValue.serverTimestamp());

                    firestore.collection("appointmentFollowUps")
                            .document(requestId)
                            .set(followUpDocument, SetOptions.merge())
                            .addOnSuccessListener(unused ->
                                    firestore.collection("appointmentFollowUps")
                                            .document(requestId)
                                            .get()
                                            .addOnSuccessListener(followUpSnapshot ->
                                                    callback.onSuccess(
                                                            toAppointmentFollowUpRecord(followUpSnapshot, requestId)
                                                    ))
                                            .addOnFailureListener(exception ->
                                                    callback.onError("후기 저장 결과를 다시 불러오지 못했습니다.")))
                            .addOnFailureListener(exception ->
                                    callback.onError("후기 내용을 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("후기 저장 대상을 확인하지 못했습니다."));
    }

    @Override
    public void saveAppointmentFollowUpSettlement(
            User currentUser,
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 濡쒓렇?명빐 二쇱꽭??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("?뺤궛 ?꾩냽 ???沅뚰븳???놁뒿?덈떎.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("?꾨즺???덉빟?먯꽌留?留덉감 ?꾩냽 ?뺤씤????ν븷 ???덉뒿?덈떎.");
                        return;
                    }

                    Map<String, Object> followUpDocument = new HashMap<>();
                    followUpDocument.put("requestId", requestId);
                    followUpDocument.put("settlementFollowUpStatus", settlementStatus.getValue());
                    followUpDocument.put("settlementFollowUpNote", normalizeText(settlementNote));
                    followUpDocument.put("settlementFollowUpSavedByUserId", currentUser.getId());
                    followUpDocument.put("settlementFollowUpSavedAt", FieldValue.serverTimestamp());
                    followUpDocument.put("updatedAt", FieldValue.serverTimestamp());

                    firestore.collection("appointmentFollowUps")
                            .document(requestId)
                            .set(followUpDocument, SetOptions.merge())
                            .addOnSuccessListener(unused ->
                                    firestore.collection("appointmentFollowUps")
                                            .document(requestId)
                                            .get()
                                            .addOnSuccessListener(followUpSnapshot ->
                                                    callback.onSuccess(
                                                            toAppointmentFollowUpRecord(followUpSnapshot, requestId)
                                                    ))
                                            .addOnFailureListener(exception ->
                                                    callback.onError("?뺤궛 ?꾩냽 寃곌낵瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??")))
                            .addOnFailureListener(exception ->
                                    callback.onError("?뺤궛 ?꾩냽 ?곹깭瑜???ν븯吏 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?뺤궛 ?꾩냽 ?????곸쓣 ?뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public void saveAppointmentFollowUpSupportEscalation(
            User currentUser,
            String requestId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 濡쒓렇?명빐 二쇱꽭??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("SOS ?꾩냽 ???沅뚰븳???놁뒿?덈떎.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("?꾨즺???덉빟?먯꽌留?SOS ?꾩냽 湲곕줉????ν븷 ???덉뒿?덈떎.");
                        return;
                    }

                    Map<String, Object> followUpDocument = new HashMap<>();
                    followUpDocument.put("requestId", requestId);
                    followUpDocument.put("supportEscalationStatus", escalationStatus.getValue());
                    followUpDocument.put("supportEscalatedByUserId", currentUser.getId());
                    followUpDocument.put("supportEscalatedAt", FieldValue.serverTimestamp());
                    followUpDocument.put("updatedAt", FieldValue.serverTimestamp());

                    firestore.collection("appointmentFollowUps")
                            .document(requestId)
                            .set(followUpDocument, SetOptions.merge())
                            .addOnSuccessListener(unused ->
                                    firestore.collection("appointmentFollowUps")
                                            .document(requestId)
                                            .get()
                                            .addOnSuccessListener(followUpSnapshot ->
                                                    callback.onSuccess(
                                                            toAppointmentFollowUpRecord(followUpSnapshot, requestId)
                                                    ))
                                            .addOnFailureListener(exception ->
                                                    callback.onError("SOS ?꾩냽 寃곌낵瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??")))
                            .addOnFailureListener(exception ->
                                    callback.onError("SOS ?꾩냽 湲곕줉????ν븯吏 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("SOS ?꾩냽 ?????곸쓣 ?뺤씤?섏? 紐삵뻽?듬땲??"));
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

    private boolean canMutateRequest(User currentUser, @Nullable AppointmentRequest request) {
        if (request == null || request.getStatus() != AppointmentStatus.REQUESTED) {
            return false;
        }
        return isRequestOwner(currentUser, request);
    }

    private boolean canCancelRequest(User currentUser, @Nullable AppointmentRequest request) {
        if (request == null) {
            return false;
        }
        if (request.getStatus() != AppointmentStatus.REQUESTED
                && request.getStatus() != AppointmentStatus.MATCHED) {
            return false;
        }
        return isRequestOwner(currentUser, request);
    }

    private boolean isRequestOwner(User currentUser, AppointmentRequest request) {
        if (currentUser.getRole() == UserRole.PATIENT) {
            return currentUser.getId().equals(request.getPatientUserId());
        }
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            return currentUser.getId().equals(request.getGuardianUserId());
        }
        return false;
    }

    private void loadAppointmentRequestDetail(
            AppointmentRequest request,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        Task<DocumentSnapshot> patientTask = loadUserSnapshot(request.getPatientUserId());
        Task<DocumentSnapshot> guardianTask = loadUserSnapshot(request.getGuardianUserId());
        Task<DocumentSnapshot> managerTask = loadUserSnapshot(request.getManagerUserId());
        Task<QuerySnapshot> sessionTask = firestore.collection("companionSessions")
                .whereEqualTo("appointmentRequestId", request.getId())
                .limit(1)
                .get();
        Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                .whereEqualTo("hospitalName", request.getHospitalName())
                .get();

        List<Task<?>> tasks = Arrays.asList(patientTask, guardianTask, managerTask, sessionTask, guideTask);
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    User patient = toUser((DocumentSnapshot) results.get(0));
                    User guardian = toUser((DocumentSnapshot) results.get(1));
                    User manager = toUser((DocumentSnapshot) results.get(2));
                    CompanionSession session = findSession((QuerySnapshot) results.get(3));
                    HospitalGuide guide = findGuide((QuerySnapshot) results.get(4), request.getDepartmentName());

                    if (session == null) {
                        callback.onSuccess(new AppointmentRequestDetail(
                                request,
                                patient,
                                guardian,
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
                                    new AppointmentRequestDetail(
                                            request,
                                            patient,
                                            guardian,
                                            manager,
                                            session,
                                            findReport(reportSnapshot),
                                            guide
                                    )
                            ))
                            .addOnFailureListener(exception ->
                                    callback.onError("진행 리포트를 불러오지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("요청 상세 정보를 불러오지 못했습니다."));
    }

    private void reloadAppointmentRequest(
            com.google.firebase.firestore.DocumentReference documentReference,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        documentReference.get()
                .addOnSuccessListener(updatedSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(updatedSnapshot);
                    if (request == null) {
                        callback.onError("요청 정보를 다시 불러오지 못했습니다.");
                        return;
                    }
                    callback.onSuccess(request);
                })
                .addOnFailureListener(exception ->
                        callback.onError("요청 정보를 다시 불러오지 못했습니다."));
    }

    private Task<DocumentSnapshot> loadUserSnapshot(@Nullable String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Tasks.forResult((DocumentSnapshot) null);
        }
        return firestore.collection("users")
                .document(userId)
                .get();
    }

    private Map<String, Object> buildRequestDocument(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
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

        requestDocument.put("hospitalName", normalizeText(bookingRequestDraft.getHospitalName()));
        requestDocument.put("departmentName", normalizeText(bookingRequestDraft.getDepartmentName()));
        requestDocument.put("appointmentAt", normalizeText(bookingRequestDraft.getAppointmentAt()));
        requestDocument.put("meetingPlace", normalizeText(bookingRequestDraft.getMeetingPlace()));
        requestDocument.put("specialNotes", normalizeText(bookingRequestDraft.getSpecialNotes()));
        requestDocument.put("patientConditionSummary", normalizeText(bookingRequestDraft.getPatientConditionSummary()));
        requestDocument.put("medicationSummary", normalizeText(bookingRequestDraft.getMedicationSummary()));
        requestDocument.put("mobilitySupportCode", bookingRequestDraft.getMobilitySupport().name());
        requestDocument.put("tripTypeCode", bookingRequestDraft.getTripType().name());
        requestDocument.put("managerGenderPreferenceCode", bookingRequestDraft.getManagerGenderPreference().name());
        requestDocument.put("paymentMethodCode", bookingRequestDraft.getPaymentMethod().name());
        requestDocument.put("couponCode", bookingRequestDraft.getCouponType().name());
        requestDocument.put("basePrice", bookingRequestDraft.getPriceSummary().getBasePrice());
        requestDocument.put("optionSurchargePrice", bookingRequestDraft.getPriceSummary().getOptionSurchargePrice());
        requestDocument.put("couponDiscountPrice", bookingRequestDraft.getPriceSummary().getCouponDiscountPrice());
        requestDocument.put("finalPrice", bookingRequestDraft.getPriceSummary().getFinalPrice());
        BookingPaymentApproval paymentApproval = bookingRequestDraft.getPaymentApproval();
        requestDocument.put("paymentStatusCode", paymentApproval.getStatus().name());
        requestDocument.put("paymentApprovalCode", normalizeText(paymentApproval.getApprovalCode()));
        requestDocument.put("paymentApprovedAt", normalizeText(paymentApproval.getApprovedAt()));
        requestDocument.put("paymentProviderLabel", normalizeText(paymentApproval.getProviderLabel()));

        ParsedAppointmentAt parsedAppointmentAt = parseAppointmentAt(bookingRequestDraft.getAppointmentAt());
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
        if (documentSnapshot == null) {
            return null;
        }
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
    private CompanionSession findSession(QuerySnapshot querySnapshot) {
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session != null) {
                return session;
            }
        }
        return null;
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
    private SessionReport findReport(QuerySnapshot querySnapshot) {
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            SessionReport report = toReport(documentSnapshot);
            if (report != null) {
                return report;
            }
        }
        return null;
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
    private HospitalGuide findGuide(QuerySnapshot querySnapshot, String departmentName) {
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

    private String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private AppointmentFollowUpRecord toAppointmentFollowUpRecord(
            @Nullable DocumentSnapshot documentSnapshot,
            String requestId
    ) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            return AppointmentFollowUpRecord.empty(requestId);
        }
        return new AppointmentFollowUpRecord(
                requestId,
                AppointmentFollowUpReviewRating.fromValue(
                        documentSnapshot.getString("reviewRatingCode")
                ),
                timestampToMillis(documentSnapshot.get("reviewSavedAt")),
                AppointmentFollowUpSettlementStatus.fromValue(
                        documentSnapshot.getString("settlementFollowUpStatus")
                ),
                normalizeText(documentSnapshot.getString("settlementFollowUpNote")),
                timestampToMillis(documentSnapshot.get("settlementFollowUpSavedAt")),
                AppointmentFollowUpSupportEscalationStatus.fromValue(
                        documentSnapshot.getString("supportEscalationStatus")
                ),
                timestampToMillis(documentSnapshot.get("supportEscalatedAt"))
        );
    }

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private long timestampToMillis(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        return 0L;
    }

    private String normalizeText(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }

    private List<BookingHospitalOption> toHospitalOptions(List<HospitalGuide> guides) {
        Map<String, TreeSet<String>> departmentsByHospital = new TreeMap<>();
        for (HospitalGuide guide : guides) {
            String hospitalName = normalizeText(guide.getHospitalName());
            String departmentName = normalizeText(guide.getDepartmentName());
            if (hospitalName.isEmpty() || departmentName.isEmpty()) {
                continue;
            }
            departmentsByHospital
                    .computeIfAbsent(hospitalName, key -> new TreeSet<>())
                    .add(departmentName);
        }

        List<BookingHospitalOption> options = new ArrayList<>();
        for (Map.Entry<String, TreeSet<String>> entry : departmentsByHospital.entrySet()) {
            options.add(new BookingHospitalOption(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
        return options;
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
