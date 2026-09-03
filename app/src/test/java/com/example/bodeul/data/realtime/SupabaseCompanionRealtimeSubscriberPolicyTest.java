package com.example.bodeul.data.realtime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupabaseCompanionRealtimeSubscriberPolicyTest {

    @Test
    public void realtimeSubscriptionRequiresFirebaseAndEndpoint() {
        assertTrue(SupabaseCompanionRealtimeSubscriber.hasAuthenticatedRealtimeConfiguration(
                "wss://example.supabase.co/realtime/v1/websocket?apikey=test",
                true));
        assertFalse(SupabaseCompanionRealtimeSubscriber.hasAuthenticatedRealtimeConfiguration(
                "wss://example.supabase.co/realtime/v1/websocket?apikey=test",
                false));
        assertFalse(SupabaseCompanionRealtimeSubscriber.hasAuthenticatedRealtimeConfiguration("", true));
        assertFalse(SupabaseCompanionRealtimeSubscriber.hasAuthenticatedRealtimeConfiguration("  ", true));
        assertFalse(SupabaseCompanionRealtimeSubscriber.hasAuthenticatedRealtimeConfiguration(null, true));
    }
}
