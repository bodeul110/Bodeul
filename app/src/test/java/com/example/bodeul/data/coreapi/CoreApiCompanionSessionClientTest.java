package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CoreApiCompanionSessionClientTest {
    @Test
    public void formatInstantMillis_writesUtcIsoInstant() {
        assertEquals(
                "1970-01-01T00:00:01.234Z",
                CoreApiCompanionSessionClient.formatInstantMillis(1234L)
        );
    }

    @Test
    public void parseInstantMillis_acceptsUtcWithoutFraction() {
        assertEquals(
                1000L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01Z")
        );
    }

    @Test
    public void parseInstantMillis_truncatesLongFractionToMillis() {
        assertEquals(
                1123L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.123456Z")
        );
    }

    @Test
    public void parseInstantMillis_acceptsIsoOffset() {
        assertEquals(
                1000L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T09:00:01+09:00")
        );
    }

    @Test
    public void parseInstantMillis_returnsZeroForMissingOrInvalidValue() {
        assertEquals(0L, CoreApiCompanionSessionClient.parseInstantMillis(null));
        assertEquals(0L, CoreApiCompanionSessionClient.parseInstantMillis(" "));
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis("잘못된 시각")
        );
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.Z")
        );
        assertEquals(
                0L,
                CoreApiCompanionSessionClient.parseInstantMillis(
                        "1970-01-01T00:00:01.123abcZ")
        );
    }
}
