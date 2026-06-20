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
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
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
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

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
 * Firestore??蹂묒썝 ?숉뻾 ?좎껌????ν븯怨??섏옄/蹂댄샇??怨꾩젙 ?곌껐???쒕룄?섎뒗 ??μ냼??
 */
public class FirebaseBookingRepository implements BookingRepository {
    private static final String FUNCTIONS_REGION = "asia-northeast3";
    private final FirebaseFirestore firestore;
    private final FirebaseFunctions functions;

    public FirebaseBookingRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
        this.functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
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
                        callback.onError("蹂묒썝 ?좏깮 紐⑸줉??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void getMyAppointmentRequests(User currentUser, RepositoryCallback<List<AppointmentRequest>> callback) {
        Query query = buildUserQuery(currentUser);
        if (query == null) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }

        query.get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(toSortedRequests(querySnapshot)))
                .addOnFailureListener(exception ->
                        callback.onError("?붿껌 紐⑸줉??遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void getAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 濡쒓렇?명빐二쇱꽭??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("?붿껌 ?곸꽭 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??");
                        return;
                    }
                    loadAppointmentRequestDetail(request, callback);
                })
                .addOnFailureListener(exception ->
                        callback.onError("?붿껌 ?곸꽭 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public Runnable observeAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 濡쒓렇?명빐二쇱꽭??");
            return () -> {};
        }

        com.google.firebase.firestore.ListenerRegistration requestListener = firestore.collection("appointmentRequests")
                .document(requestId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        callback.onError("?붿껌 ?곸꽭 ?뺣낫瑜?媛먯??섏? 紐삵뻽?듬땲??");
                        return;
                    }
                    if (documentSnapshot != null) {
                        AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                        if (request == null || !isRequestOwner(currentUser, request)) {
                            callback.onError("?붿껌 ?곸꽭 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??");
                            return;
                        }
                        loadAppointmentRequestDetail(request, callback);
                    }
                });

        com.google.firebase.firestore.ListenerRegistration sessionListener = firestore.collection("companionSessions")
                .whereEqualTo("appointmentRequestId", requestId)
                .limit(1)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        return;
                    }
                    // Trigger a reload of the request details to get the latest session data
                    getAppointmentRequestDetail(currentUser, requestId, callback);
                });

        return () -> {
            requestListener.remove();
            sessionListener.remove();
        };
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
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
                                                        callback.onError("?붿껌 ?뺣낫瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??");
                                                        return;
                                                    }
                                                    callback.onSuccess(request);
                                                })
                                                .addOnFailureListener(exception ->
                                                        callback.onError("?붿껌 ?뺣낫瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??")))
                                .addOnFailureListener(exception ->
                                        callback.onError("?숉뻾 ?붿껌????ν븯吏 紐삵뻽?듬땲??"));
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
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest existingRequest = toAppointmentRequest(documentSnapshot);
                    if (!canMutateRequest(currentUser, existingRequest)) {
                        callback.onError("?묒닔 ?湲??곹깭 ?붿껌留??섏젙?????덉뒿?덈떎.");
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
                                                    callback.onError("?숉뻾 ?붿껌???섏젙?섏? 紐삵뻽?듬땲??"));
                                }

                                @Override
                                public void onError(String message) {
                                    callback.onError(message);
                                }
                            }
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onError("?붿껌 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest existingRequest = toAppointmentRequest(documentSnapshot);
                    if (!canCancelRequest(currentUser, existingRequest)) {
                        callback.onError("?묒닔 ?湲??먮뒗 留ㅻ땲? 諛곗젙 ?꾨즺 ?곹깭 ?붿껌留?痍⑥냼?????덉뒿?덈떎.");
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
                                            "liveLocationSharingActive", false,
                                            "liveLocationSharingStartedAt", FieldValue.delete(),
                                            "updatedAt", FieldValue.serverTimestamp()
                                    );
                                }

                                batch.commit()
                                        .addOnSuccessListener(unused ->
                                                reloadAppointmentRequest(documentSnapshot.getReference(), callback))
                                        .addOnFailureListener(exception ->
                                                callback.onError("?숉뻾 ?붿껌??痍⑥냼?섏? 紐삵뻽?듬땲??"));
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("?곌껐???숉뻾 ?몄뀡???뺤씤?섏? 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?붿껌 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public void getAppointmentFollowUp(
            User currentUser,
            String requestId,
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
                        callback.onError("?꾩냽 ?뺣낫 議고쉶 沅뚰븳???놁뒿?덈떎.");
                        return;
                    }

                    firestore.collection("appointmentFollowUps")
                            .document(requestId)
                            .get()
                            .addOnSuccessListener(followUpSnapshot ->
                                    callback.onSuccess(toAppointmentFollowUpRecord(followUpSnapshot, requestId)))
                            .addOnFailureListener(exception ->
                                    callback.onError("?꾩냽 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?꾩냽 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
    }

    @Override
    public void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
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
                        callback.onError("?꾧린 ???沅뚰븳???놁뒿?덈떎.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("?꾨즺???덉빟?먯꽌留??꾧린瑜???ν븷 ???덉뒿?덈떎.");
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
                                                    callback.onError("?꾧린 ???寃곌낵瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??")))
                            .addOnFailureListener(exception ->
                                    callback.onError("?꾧린 ?댁슜????ν븯吏 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?꾧린 ?????곸쓣 ?뺤씤?섏? 紐삵뻽?듬땲??"));
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
            callback.onError("??륁쁽 ?癒?뮉 癰귣똾????④쑴???곗쨮 嚥≪뮄??紐낅퉸 雅뚯눘苑??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("?類ㅺ텦 ?袁⑸꺗 ????亦낅슦釉????곷뮸??덈뼄.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("?袁⑥┷????됰튋?癒?퐣筌?筌띾뜆媛??袁⑸꺗 ?類ㅼ뵥?????館釉?????됰뮸??덈뼄.");
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
                                                    callback.onError("?類ㅺ텦 ?袁⑸꺗 野껉퀗?든몴???쇰뻻 ?븍뜄???? 筌륁궢六??щ빍??")))
                            .addOnFailureListener(exception ->
                                    callback.onError("?類ㅺ텦 ?袁⑸꺗 ?怨밴묶?????館釉?쭪? 筌륁궢六??щ빍??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?類ㅺ텦 ?袁⑸꺗 ???????怨몄뱽 ?類ㅼ뵥??? 筌륁궢六??щ빍??"));
    }

    @Override
    public void saveAppointmentFollowUpSupportEscalation(
            User currentUser,
            String requestId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("??륁쁽 ?癒?뮉 癰귣똾????④쑴???곗쨮 嚥≪뮄??紐낅퉸 雅뚯눘苑??");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("SOS ?袁⑸꺗 ????亦낅슦釉????곷뮸??덈뼄.");
                        return;
                    }
                    if (request.getStatus() != AppointmentStatus.COMPLETED) {
                        callback.onError("?袁⑥┷????됰튋?癒?퐣筌?SOS ?袁⑸꺗 疫꿸퀡以?????館釉?????됰뮸??덈뼄.");
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
                                                    callback.onError("SOS ?袁⑸꺗 野껉퀗?든몴???쇰뻻 ?븍뜄???? 筌륁궢六??щ빍??")))
                            .addOnFailureListener(exception ->
                                    callback.onError("SOS ?袁⑸꺗 疫꿸퀡以?????館釉?쭪? 筌륁궢六??щ빍??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("SOS ?袁⑸꺗 ???????怨몄뱽 ?類ㅼ뵥??? 筌륁궢六??щ빍??"));
    }

    @Override
    public void sendCompanionChatMessage(
            User currentUser,
            String requestId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        if (!supportsRole(currentUser.getRole())) {
            callback.onError("?꾩옱 怨꾩젙? ?숉뻾 梨꾪똿???ъ슜?????놁뒿?덈떎.");
            return;
        }

        String normalizedMessage = normalizeText(message);
        if (normalizedMessage.isEmpty() && (attachments == null || attachments.isEmpty())) {
            callback.onError("메시지나 첨부 파일 중 하나는 함께 보내야 합니다.");
            return;
        }

        firestore.collection("appointmentRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(documentSnapshot);
                    if (request == null || !isRequestOwner(currentUser, request)) {
                        callback.onError("?숉뻾 ?붿껌 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??");
                        return;
                    }

                    firestore.collection("companionSessions")
                            .whereEqualTo("appointmentRequestId", requestId)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                DocumentSnapshot activeSessionSnapshot = findActiveSessionDocument(querySnapshot);
                                if (activeSessionSnapshot == null) {
                                    callback.onError("吏꾪뻾 以묒씤 ?숉뻾 ?몄뀡???놁뒿?덈떎.");
                                    return;
                                }

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("chatMessages", FieldValue.arrayUnion(
                                        buildChatMessagePayload(currentUser.getRole(), normalizedMessage, attachments)
                                ));
                                updates.put(resolveChatReadField(currentUser.getRole()), FieldValue.serverTimestamp());
                                updates.put("updatedAt", FieldValue.serverTimestamp());

                                activeSessionSnapshot.getReference()
                                        .update(updates)
                                        .addOnSuccessListener(unused ->
                                                loadAppointmentRequestDetail(request, callback))
                                        .addOnFailureListener(exception ->
                                                callback.onError("硫붿떆吏瑜??꾩넚?섏? 紐삵뻽?듬땲??"));
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("吏꾪뻾 以묒씤 ?숉뻾 ?몄뀡???뺤씤?섏? 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError("?숉뻾 ?붿껌 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??"));
    }

    @Override
    public void markCompanionChatRead(User currentUser, String requestId) {
        if (!supportsRole(currentUser.getRole()) || normalizeText(requestId).isEmpty()) {
            return;
        }

        firestore.collection("companionSessions")
                .whereEqualTo("appointmentRequestId", requestId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentSnapshot activeSessionSnapshot = findActiveSessionDocument(querySnapshot);
                    if (activeSessionSnapshot == null) {
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(resolveChatReadField(currentUser.getRole()), FieldValue.serverTimestamp());
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    activeSessionSnapshot.getReference().update(updates);
                });
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
        Task<User> managerTask = loadAssignedManagerProfile(request.getId());
        Task<QuerySnapshot> sessionTask = firestore.collection("companionSessions")
                .whereEqualTo("appointmentRequestId", request.getId())
                .limit(1)
                .get();
        Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                .whereEqualTo("hospitalName", request.getHospitalName())
                .get();

        List<Task<?>> tasks = Arrays.asList(managerTask, sessionTask, guideTask);
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    User manager = (User) results.get(0);
                    CompanionSession session = findSession((QuerySnapshot) results.get(1));
                    HospitalGuide guide = findGuide((QuerySnapshot) results.get(2), request.getDepartmentName());

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
                                    callback.onError("吏꾪뻾 由ы룷?몃? 遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
                })
                .addOnFailureListener(exception ->
                        callback.onError(resolveAssignedManagerProfileMessage(
                                exception,
                                "?붿껌 ?곸꽭 ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??"
                        )));
    }

    private void reloadAppointmentRequest(
            com.google.firebase.firestore.DocumentReference documentReference,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        documentReference.get()
                .addOnSuccessListener(updatedSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(updatedSnapshot);
                    if (request == null) {
                        callback.onError("?붿껌 ?뺣낫瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??");
                        return;
                    }
                    callback.onSuccess(request);
                })
                .addOnFailureListener(exception ->
                        callback.onError("?붿껌 ?뺣낫瑜??ㅼ떆 遺덈윭?ㅼ? 紐삵뻽?듬땲??"));
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
        if (email.isEmpty() && phone.isEmpty()) {
            callback.onResolved(null);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("expectedRole", expectedRole.name());
        payload.put("email", email);
        payload.put("phone", phone);

        functions.getHttpsCallable("resolveLinkedParticipant")
                .call(payload)
                .addOnSuccessListener(result ->
                        callback.onResolved(toLinkedParticipantUser(result.getData(), expectedRole)))
                .addOnFailureListener(exception ->
                        callback.onError(resolveLinkedParticipantMessage(exception)));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private User toLinkedParticipantUser(@Nullable Object rawData, UserRole expectedRole) {
        if (!(rawData instanceof Map)) {
            return null;
        }

        Object participantValue = ((Map<String, Object>) rawData).get("participant");
        if (!(participantValue instanceof Map)) {
            return null;
        }

        Map<String, Object> participant = (Map<String, Object>) participantValue;
        String userId = normalizeText(asString(participant.get("userId")));
        String roleValue = normalizeText(asString(participant.get("role")));
        String name = normalizeText(asString(participant.get("name")));
        String email = normalizeText(asString(participant.get("email")));
        String phone = normalizeText(asString(participant.get("phone")));
        if (userId.isEmpty() || roleValue.isEmpty() || name.isEmpty() || email.isEmpty()) {
            return null;
        }

        UserRole resolvedRole;
        try {
            resolvedRole = UserRole.valueOf(roleValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        if (resolvedRole != expectedRole) {
            return null;
        }

        return new User(
                userId,
                resolvedRole,
                UserProfileSanitizer.normalizeName(name),
                UserProfileSanitizer.normalizeEmail(email),
                UserProfileSanitizer.normalizePhone(phone)
        );
    }

    private String resolveLinkedParticipantMessage(Exception exception) {
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
        return "?곌껐??怨꾩젙 ?뺣낫瑜??뺤씤?섏? 紐삵뻽?듬땲??";
    }

    private Task<User> loadAssignedManagerProfile(String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        return functions.getHttpsCallable("resolveAssignedManagerProfile")
                .call(payload)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        if (exception != null) {
                            throw exception;
                        }
                        throw new IllegalStateException("留ㅻ땲? ?뺣낫瑜?遺덈윭?ㅼ? 紐삵뻽?듬땲??");
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
        String userId = normalizeText(asString(manager.get("userId")));
        String roleValue = normalizeText(asString(manager.get("role")));
        String name = normalizeText(asString(manager.get("name")));
        String email = normalizeText(asString(manager.get("email")));
        String phone = normalizeText(asString(manager.get("phone")));
        if (userId.isEmpty() || name.isEmpty() || email.isEmpty()) {
            return null;
        }
        if (!roleValue.isEmpty() && !UserRole.MANAGER.name().equals(roleValue)) {
            return null;
        }

        return new User(
                userId,
                UserRole.MANAGER,
                UserProfileSanitizer.normalizeName(name),
                UserProfileSanitizer.normalizeEmail(email),
                UserProfileSanitizer.normalizePhone(phone)
        );
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
    private String asString(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
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
                UserProfileSanitizer.normalizeName(stringOrEmpty(name)),
                UserProfileSanitizer.normalizeEmail(stringOrEmpty(email)),
                UserProfileSanitizer.normalizePhone(stringOrEmpty(phone))
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
    private DocumentSnapshot findActiveSessionDocument(QuerySnapshot querySnapshot) {
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            CompanionSession session = toSession(documentSnapshot);
            if (session == null) {
                continue;
            }
            if (session.getStatus() == SessionStatus.COMPLETED
                    || session.getStatus() == SessionStatus.CANCELED) {
                continue;
            }
            return documentSnapshot;
        }
        return null;
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        return FirebaseCompanionSessionMapper.toSession(documentSnapshot, UserRole.GUARDIAN);
    }

    private Map<String, Object> buildChatMessagePayload(
            UserRole senderRole,
            String body,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderRole", senderRole == null ? UserRole.GUARDIAN.name() : senderRole.name());
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
        String fullPath = normalizeText(asString(valueMap.get("fullPath")));
        if (fullPath.isEmpty()) {
            return null;
        }
        return new CompanionChatAttachment(
                fullPath,
                normalizeText(asString(valueMap.get("fileName"))),
                normalizeText(asString(valueMap.get("contentType"))),
                numberOrZero(valueMap.get("uploadedAtMillis"))
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
            String roleValue = normalizeText(asString(valueMap.get("senderRole")));
            String body = normalizeText(asString(valueMap.get("body")));
            List<CompanionChatAttachment> attachments = toChatAttachments(
                    valueMap.get("attachments"),
                    valueMap.get("attachment")
            );
            if (body.isEmpty() && attachments.isEmpty()) {
                continue;
            }
            long sentAtMillis = numberOrZero(valueMap.get("sentAtMillis"));
            UserRole senderRole;
            try {
                senderRole = roleValue.isEmpty() ? UserRole.GUARDIAN : UserRole.valueOf(roleValue);
            } catch (IllegalArgumentException exception) {
                senderRole = UserRole.GUARDIAN;
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

    @Nullable
    private Double doubleOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
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

        // ?덉빟 ?뚮┝ ?ㅼ?以꾨윭媛 媛숈? 湲곗??쇰줈 怨꾩궛?????덈룄濡??낅젰媛믪쓣 ?쒖슱 ?쒓컙 湲곗??쇰줈 ?뚯떛?쒕떎.
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



