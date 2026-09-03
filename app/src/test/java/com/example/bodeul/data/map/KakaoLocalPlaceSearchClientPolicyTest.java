package com.example.bodeul.data.map;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KakaoLocalPlaceSearchClientPolicyTest {

    @Test
    public void authenticatedCoreSearchRequiresFirebaseAndEndpoint() {
        assertTrue(KakaoLocalPlaceSearchClient.hasAuthenticatedCoreSearchConfiguration(
                "https://api.example.test",
                true));
        assertFalse(KakaoLocalPlaceSearchClient.hasAuthenticatedCoreSearchConfiguration(
                "https://api.example.test",
                false));
        assertFalse(KakaoLocalPlaceSearchClient.hasAuthenticatedCoreSearchConfiguration("", true));
        assertFalse(KakaoLocalPlaceSearchClient.hasAuthenticatedCoreSearchConfiguration("  ", true));
        assertFalse(KakaoLocalPlaceSearchClient.hasAuthenticatedCoreSearchConfiguration(null, true));
    }
}
