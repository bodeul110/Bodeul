package com.example.bodeul.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

/**
 * 외부 데이터 enum 문자열이 잘못되어도 예외가 나지 않는지 검증한다.
 */
public class SafeEnumParserTest {
    @Test
    public void parseOrNull_returnsEnumForTrimmedValue() {
        assertEquals(UserRole.MANAGER, SafeEnumParser.parseOrNull(UserRole.class, " MANAGER "));
    }

    @Test
    public void parseOrNull_returnsNullForInvalidValue() {
        assertNull(SafeEnumParser.parseOrNull(UserRole.class, "BROKEN"));
    }

    @Test
    public void parseOrNull_returnsNullForEmptyValue() {
        assertNull(SafeEnumParser.parseOrNull(UserRole.class, " "));
    }

    @Test
    public void parseOrDefault_returnsFallbackForInvalidValue() {
        assertEquals(
                UserRole.PATIENT,
                SafeEnumParser.parseOrDefault(UserRole.class, "BROKEN", UserRole.PATIENT)
        );
    }
}
