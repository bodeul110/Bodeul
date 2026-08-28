package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ClientCompanionRoomEntryStateTest {
    @Test
    public void fromAuthorizedRequests_prefersInProgressOverEarlierMatchedRequest() {
        ClientCompanionRoomEntryState state = ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Arrays.asList(
                        request("future-matched", AppointmentStatus.MATCHED),
                        request("in-progress", AppointmentStatus.IN_PROGRESS)
                )
        );

        assertFalse(state.isEmpty());
        assertEquals("in-progress", state.getRequestId());
    }

    @Test
    public void fromAuthorizedRequests_usesFirstUsableInProgressId() {
        ClientCompanionRoomEntryState state = ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Arrays.asList(
                        request("", AppointmentStatus.IN_PROGRESS),
                        request("active", AppointmentStatus.IN_PROGRESS),
                        request("matched", AppointmentStatus.MATCHED)
                )
        );

        assertFalse(state.isEmpty());
        assertEquals("active", state.getRequestId());
    }

    @Test
    public void fromAuthorizedRequests_usesMatchedOnlyWhenNoUsableInProgressIdExists() {
        ClientCompanionRoomEntryState state = ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Arrays.asList(
                        request("   ", AppointmentStatus.IN_PROGRESS),
                        request("", AppointmentStatus.MATCHED),
                        request("matched", AppointmentStatus.MATCHED)
                )
        );

        assertFalse(state.isEmpty());
        assertEquals("matched", state.getRequestId());
    }

    @Test
    public void fromAuthorizedRequests_returnsEmptyWhenNoCurrentParticipantRequestIdExists() {
        ClientCompanionRoomEntryState state = ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Arrays.asList(
                        request("requested", AppointmentStatus.REQUESTED),
                        request("completed", AppointmentStatus.COMPLETED),
                        request("canceled", AppointmentStatus.CANCELED)
                )
        );

        assertTrue(state.isEmpty());
        assertNull(state.getRequestId());
    }

    @Test
    public void fromAuthorizedRequests_returnsEmptyForEmptyServerResult() {
        assertTrue(ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Collections.emptyList()
        ).isEmpty());
        assertTrue(ClientCompanionRoomEntryState.fromAuthorizedRequests(null).isEmpty());
    }

    @Test
    public void fromAuthorizedRequests_returnsEmptyWhenServerHasNoUsableParticipantId() {
        ClientCompanionRoomEntryState state = ClientCompanionRoomEntryState.fromAuthorizedRequests(
                Arrays.asList(
                        null,
                        request("", AppointmentStatus.MATCHED),
                        request("   ", AppointmentStatus.IN_PROGRESS)
                )
        );

        assertTrue(state.isEmpty());
        assertNull(state.getRequestId());
    }

    private AppointmentRequest request(String id, AppointmentStatus status) {
        return new AppointmentRequest(
                id,
                "patient",
                "guardian",
                "보들병원",
                "내과",
                "2026-08-28T10:00:00+09:00",
                "정문",
                "",
                status,
                "manager"
        );
    }
}
