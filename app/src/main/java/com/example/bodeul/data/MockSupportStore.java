package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MockSupportStore {
    private final MockBodeulRepository repository;

    public MockSupportStore(MockBodeulRepository repository) {
        this.repository = repository;
    }

    public List<SupportInquiry> getSupportInquiries(String managerUserId) {
        List<SupportInquiry> inquiries = new ArrayList<>();
        for (SupportInquiry inquiry : repository.getMutableSupportInquiries()) {
            if (inquiry.getManagerUserId().equals(managerUserId)) {
                inquiries.add(inquiry);
            }
        }
        return Collections.unmodifiableList(inquiries);
    }

    public List<SupportInquiry> getAllSupportInquiries() {
        return Collections.unmodifiableList(new ArrayList<>(repository.getMutableSupportInquiries()));
    }

    public List<ClientSupportRequest> getClientSupportRequests(String userId) {
        List<ClientSupportRequest> requests = new ArrayList<>();
        for (ClientSupportRequest request : repository.getMutableClientSupportRequests()) {
            if (request.getUserId().equals(userId)) {
                requests.add(request);
            }
        }
        return Collections.unmodifiableList(requests);
    }

    public List<ClientSupportRequest> getAllClientSupportRequests() {
        return Collections.unmodifiableList(new ArrayList<>(repository.getMutableClientSupportRequests()));
    }

    @Nullable
    public SupportInquiry saveSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body
    ) {
        User manager = repository.findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        long createdAtMillis = System.currentTimeMillis();
        SupportInquiry inquiry = new SupportInquiry(
                "support-" + createdAtMillis,
                managerUserId,
                manager.getName(),
                category,
                repository.normalizeText(title),
                repository.normalizeText(body),
                SupportInquiryStatus.RECEIVED,
                createdAtMillis,
                "",
                0L,
                ""
        );
        repository.getMutableSupportInquiries().add(0, inquiry);
        return inquiry;
    }

    @Nullable
    public ClientSupportRequest saveClientSupportRequest(
            String userId,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body
    ) {
        User user = repository.findUserById(userId);
        if (user == null || (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN)) {
            return null;
        }
        long createdAtMillis = System.currentTimeMillis();
        ClientSupportRequest request = new ClientSupportRequest(
                "client-support-" + createdAtMillis,
                userId,
                user.getName(),
                user.getRole(),
                repository.normalizeText(appointmentRequestId),
                category,
                repository.normalizeText(title),
                repository.normalizeText(body),
                ClientSupportStatus.RECEIVED,
                createdAtMillis,
                "",
                0L,
                "",
                false,
                0L,
                0,
                0L
        );
        repository.getMutableClientSupportRequests().add(0, request);
        return request;
    }

    public void markClientSupportResponsesRead(String userId) {
        long readAtMillis = System.currentTimeMillis();
        List<ClientSupportRequest> requests = repository.getMutableClientSupportRequests();
        for (int index = 0; index < requests.size(); index++) {
            ClientSupportRequest request = requests.get(index);
            if (!request.getUserId().equals(userId) || !request.hasUnreadResponse()) {
                continue;
            }
            requests.set(index, new ClientSupportRequest(
                    request.getId(),
                    request.getUserId(),
                    request.getUserName(),
                    request.getUserRole(),
                    request.getAppointmentRequestId(),
                    request.getCategory(),
                    request.getTitle(),
                    request.getBody(),
                    request.getStatus(),
                    request.getCreatedAtMillis(),
                    request.getResponseText(),
                    request.getRespondedAtMillis(),
                    request.getRespondedByName(),
                    true,
                    readAtMillis,
                    request.getResponseReminderCount(),
                    request.getResponseReminderSentAtMillis()
            ));
        }
    }

    @Nullable
    public ClientSupportRequest respondClientSupportRequest(
            String supportRequestId,
            String response,
            String respondedByName
    ) {
        List<ClientSupportRequest> requests = repository.getMutableClientSupportRequests();
        for (int index = 0; index < requests.size(); index++) {
            ClientSupportRequest request = requests.get(index);
            if (!request.getId().equals(supportRequestId)) {
                continue;
            }
            ClientSupportRequest updatedRequest = new ClientSupportRequest(
                    request.getId(),
                    request.getUserId(),
                    request.getUserName(),
                    request.getUserRole(),
                    request.getAppointmentRequestId(),
                    request.getCategory(),
                    request.getTitle(),
                    request.getBody(),
                    ClientSupportStatus.ANSWERED,
                    request.getCreatedAtMillis(),
                    repository.normalizeText(response),
                    System.currentTimeMillis(),
                    repository.normalizeText(respondedByName),
                    false,
                    0L,
                    0,
                    0L
            );
            requests.set(index, updatedRequest);
            return updatedRequest;
        }
        return null;
    }

    @Nullable
    public SupportInquiry respondSupportInquiry(
            String inquiryId,
            String response,
            String respondedByName
    ) {
        List<SupportInquiry> inquiries = repository.getMutableSupportInquiries();
        for (int index = 0; index < inquiries.size(); index++) {
            SupportInquiry inquiry = inquiries.get(index);
            if (!inquiry.getId().equals(inquiryId)) {
                continue;
            }
            SupportInquiry updatedInquiry = new SupportInquiry(
                    inquiry.getId(),
                    inquiry.getManagerUserId(),
                    inquiry.getManagerName(),
                    inquiry.getCategory(),
                    inquiry.getTitle(),
                    inquiry.getBody(),
                    SupportInquiryStatus.ANSWERED,
                    inquiry.getCreatedAtMillis(),
                    repository.normalizeText(response),
                    System.currentTimeMillis(),
                    repository.normalizeText(respondedByName)
            );
            inquiries.set(index, updatedInquiry);
            repository.appendAdminActionArtifacts(
                    AdminActionSourceType.SUPPORT,
                    AdminActionNotificationLevel.INFO,
                    "",
                    inquiry.getId(),
                    "문의 응답 등록",
                    repository.buildSupportNotificationBody(inquiry),
                    "문의 응답 등록",
                    updatedInquiry.getResponseText(),
                    updatedInquiry.getRespondedByName(),
                    updatedInquiry.getRespondedAtMillis()
            );
            return updatedInquiry;
        }
        return null;
    }
}
