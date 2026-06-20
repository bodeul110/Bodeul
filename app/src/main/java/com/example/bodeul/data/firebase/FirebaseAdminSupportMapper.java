package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 문의 문서를 공용 도메인 모델로 변환한다.
 */
final class FirebaseAdminSupportMapper {
    private FirebaseAdminSupportMapper() {
    }

    static List<SupportInquiry> toSupportInquiries(QuerySnapshot supportSnapshot) {
        List<SupportInquiry> inquiries = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : supportSnapshot.getDocuments()) {
            SupportInquiry inquiry = toSupportInquiry(documentSnapshot);
            if (inquiry != null) {
                inquiries.add(inquiry);
            }
        }
        inquiries.sort((left, right) -> Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
        return inquiries;
    }

    static List<ClientSupportRequest> toClientSupportRequests(QuerySnapshot clientSupportSnapshot) {
        List<ClientSupportRequest> requests = new ArrayList<>();
        for (DocumentSnapshot documentSnapshot : clientSupportSnapshot.getDocuments()) {
            ClientSupportRequest request = toClientSupportRequest(documentSnapshot);
            if (request != null) {
                requests.add(request);
            }
        }
        requests.sort((left, right) -> Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
        return requests;
    }

    @Nullable
    private static SupportInquiry toSupportInquiry(DocumentSnapshot documentSnapshot) {
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
    private static ClientSupportRequest toClientSupportRequest(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }
        String userId = normalizeText(documentSnapshot.getString("userId"));
        if (userId.isEmpty()) {
            return null;
        }
        return new ClientSupportRequest(
                documentSnapshot.getId(),
                userId,
                normalizeText(documentSnapshot.getString("userName")),
                resolveUserRole(documentSnapshot.getString("userRole")),
                normalizeText(documentSnapshot.getString("appointmentRequestId")),
                ClientSupportCategory.fromValue(documentSnapshot.getString("category")),
                normalizeText(documentSnapshot.getString("title")),
                normalizeText(documentSnapshot.getString("body")),
                resolveClientSupportStatus(documentSnapshot.getString("status")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                normalizeText(documentSnapshot.getString("responseText")),
                resolveTimestampMillis(documentSnapshot.get("respondedAt")),
                normalizeText(documentSnapshot.getString("respondedByName")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("responseReadByUser")),
                resolveTimestampMillis(documentSnapshot.get("responseReadAt")),
                Math.max(numberOrZero(documentSnapshot.get("responseReminderCount")), 0),
                resolveTimestampMillis(documentSnapshot.get("responseReminderSentAt"))
        );
    }

    private static SupportInquiryStatus resolveSupportInquiryStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return SupportInquiryStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 기본 접수 상태로 보정한다.
            }
        }
        return SupportInquiryStatus.RECEIVED;
    }

    private static ClientSupportStatus resolveClientSupportStatus(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return ClientSupportStatus.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 기본 접수 상태로 보정한다.
            }
        }
        return ClientSupportStatus.RECEIVED;
    }

    private static UserRole resolveUserRole(@Nullable String rawValue) {
        if (rawValue != null) {
            try {
                return UserRole.valueOf(rawValue);
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 값은 기본 환자 권한으로 보정한다.
            }
        }
        return UserRole.PATIENT;
    }

    private static long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private static int numberOrZero(@Nullable Object rawValue) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        }
        return 0;
    }

    private static String normalizeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
