package com.example.bodeul.data;

import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 환자와 보호자의 문의 접수와 내역 조회를 담당하는 저장소 계약이다.
 */
public interface ClientSupportRepository {
    void getClientSupportRequests(
            User currentUser,
            RepositoryCallback<List<ClientSupportRequest>> callback
    );

    void submitClientSupportRequest(
            User currentUser,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body,
            RepositoryCallback<List<ClientSupportRequest>> callback
    );

    boolean isFirebaseBacked();
}
