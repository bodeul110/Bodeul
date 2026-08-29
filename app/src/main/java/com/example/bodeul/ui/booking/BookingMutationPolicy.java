package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.UserRole;

/**
 * 정보공유 동의와 예약 업무 대리 권한을 분리하는 Android 쓰기 경계다.
 */
public final class BookingMutationPolicy {
    public enum Operation {
        CREATE,
        UPDATE,
        CANCEL
    }

    private BookingMutationPolicy() {
    }

    public static boolean isAllowed(UserRole role, Operation operation) {
        return role == UserRole.PATIENT;
    }

    public static boolean isStatusActionAllowed(UserRole role, BookingStatusActionType actionType) {
        if (actionType == BookingStatusActionType.EDIT) {
            return isAllowed(role, Operation.UPDATE);
        }
        if (actionType == BookingStatusActionType.CANCEL) {
            return isAllowed(role, Operation.CANCEL);
        }
        return true;
    }

    public static boolean runIfAllowed(UserRole role, Operation operation, Runnable action) {
        if (!isAllowed(role, operation)) {
            return false;
        }
        action.run();
        return true;
    }
}
