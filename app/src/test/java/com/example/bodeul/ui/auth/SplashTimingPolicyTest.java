package com.example.bodeul.ui.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SplashTimingPolicyTest {
    @Test
    public void minimumDisplayTimeIsKeptWhenLaunchResolvesImmediately() {
        assertEquals(
                SplashTimingPolicy.MIN_DISPLAY_MILLIS,
                SplashTimingPolicy.remainingDisplayMillis(1_000L, 1_000L)
        );
        assertEquals(
                SplashTimingPolicy.MIN_DISPLAY_MILLIS,
                SplashTimingPolicy.remainingDisplayMillis(1_000L, 900L)
        );
    }

    @Test
    public void elapsedTimeIsRemovedFromRemainingDisplayTime() {
        assertEquals(500L, SplashTimingPolicy.remainingDisplayMillis(1_000L, 1_400L));
        assertEquals(0L, SplashTimingPolicy.remainingDisplayMillis(1_000L, 1_900L));
        assertEquals(0L, SplashTimingPolicy.remainingDisplayMillis(1_000L, 2_500L));
    }
}
