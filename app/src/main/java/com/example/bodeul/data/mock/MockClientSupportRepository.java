package com.example.bodeul.data.mock;

import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.MockSupportStore;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;

/**
 * Firebase 없이도 환자와 보호자 문의 흐름을 확인할 수 있게 하는 목업 저장소다.
 */
public final class MockClientSupportRepository implements ClientSupportRepository {
    private final MockSupportStore supportStore;

    public MockClientSupportRepository(MockBodeulRepository repository) {
        this.supportStore = new MockSupportStore(repository);
    }

    @Override
    public void getClientSupportRequests(
            User currentUser,
            RepositoryCallback<List<ClientSupportRequest>> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("환자 또는 보호자 계정으로 다시 확인해 주세요.");
            return;
        }
        callback.onSuccess(supportStore.getClientSupportRequests(currentUser.getId()));
    }

    @Override
    public void submitClientSupportRequest(
            User currentUser,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body,
            RepositoryCallback<List<ClientSupportRequest>> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("환자 또는 보호자 계정만 문의를 남길 수 있습니다.");
            return;
        }

        ClientSupportRequest request = supportStore.saveClientSupportRequest(
                currentUser.getId(),
                appointmentRequestId,
                category,
                title,
                body
        );
        if (request == null) {
            callback.onError("문의 내용을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(supportStore.getClientSupportRequests(currentUser.getId()));
    }

    @Override
    public void markClientSupportResponsesRead(
            User currentUser,
            RepositoryCallback<Void> callback
    ) {
        if (!supportsRole(currentUser)) {
            callback.onError("?섏옄 ?먮뒗 蹂댄샇??怨꾩젙?쇰줈 ?ㅼ떆 ?뺤씤??二쇱꽭??");
            return;
        }
        supportStore.markClientSupportResponsesRead(currentUser.getId());
        callback.onSuccess(null);
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private boolean supportsRole(User currentUser) {
        return currentUser != null
                && (currentUser.getRole() == UserRole.PATIENT
                || currentUser.getRole() == UserRole.GUARDIAN);
    }
}
