package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import android.location.LocationListener;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class ManagerCurrentLocationReaderTest {
    @Test
    public void fallbackMustBeNoOlderThanFiveMinutes() {
        long now = 1_000_000L;

        assertTrue(ManagerCurrentLocationReader.isFreshFallback(now - 300_000L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(now - 300_001L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(0L, now));
    }

    @Test
    public void smallFutureClockDifferenceIsAccepted() {
        long now = 1_000_000L;

        assertTrue(ManagerCurrentLocationReader.isFreshFallback(now + 1_000L, now));
        assertFalse(ManagerCurrentLocationReader.isFreshFallback(now + 300_001L, now));
    }

    @Test
    public void legacyListenerDeclaresProviderCallbacksRequiredBeforeApi30() throws Exception {
        Method providerEnabled = ManagerCurrentLocationReader.LegacySingleUpdateListener.class
                .getDeclaredMethod("onProviderEnabled", String.class);
        Method statusChanged = ManagerCurrentLocationReader.LegacySingleUpdateListener.class
                .getDeclaredMethod(
                        "onStatusChanged",
                        String.class,
                        int.class,
                        android.os.Bundle.class);

        assertEquals(
                ManagerCurrentLocationReader.LegacySingleUpdateListener.class,
                providerEnabled.getDeclaringClass());
        assertEquals(
                ManagerCurrentLocationReader.LegacySingleUpdateListener.class,
                statusChanged.getDeclaringClass());
    }

    @Test
    public void legacyProviderStateCallbacksDoNotCompleteSingleUpdate() {
        AtomicInteger completionCount = new AtomicInteger();
        ManagerCurrentLocationReader.LegacySingleUpdateListener listener =
                new ManagerCurrentLocationReader.LegacySingleUpdateListener(
                        new ManagerCurrentLocationReader.LegacyListenerCallback() {
                            @Override
                            public void onLocationChanged(
                                    LocationListener source,
                                    Location location
                            ) {
                                completionCount.incrementAndGet();
                            }

                            @Override
                            public void onProviderDisabled(LocationListener source) {
                                completionCount.incrementAndGet();
                            }
                        });

        listener.onProviderEnabled("gps");
        listener.onStatusChanged("gps", 0, null);

        assertEquals(0, completionCount.get());
    }
}
