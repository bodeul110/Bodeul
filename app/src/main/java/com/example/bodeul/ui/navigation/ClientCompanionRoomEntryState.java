package com.example.bodeul.ui.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;

import java.util.List;

/**
 * 서버가 현재 사용자에게 허용해 반환한 예약으로 최상위 동행방 진입 상태를 만든다.
 */
public final class ClientCompanionRoomEntryState {
    @Nullable
    private final String requestId;

    private ClientCompanionRoomEntryState(@Nullable String requestId) {
        this.requestId = requestId;
    }

    @NonNull
    public static ClientCompanionRoomEntryState fromAuthorizedRequests(
            @Nullable List<AppointmentRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return new ClientCompanionRoomEntryState(null);
        }
        for (AppointmentRequest request : requests) {
            if (request == null || request.getId() == null || request.getId().trim().isEmpty()) {
                continue;
            }
            if (request.getStatus() == AppointmentStatus.MATCHED
                    || request.getStatus() == AppointmentStatus.IN_PROGRESS) {
                return new ClientCompanionRoomEntryState(request.getId());
            }
        }
        return new ClientCompanionRoomEntryState(null);
    }

    public boolean isEmpty() {
        return requestId == null || requestId.trim().isEmpty();
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }
}
