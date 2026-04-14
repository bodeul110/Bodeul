package com.example.bodeul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.util.UserProfileSanitizer;

import org.junit.Test;

/**
 * 사용자 프로필 입력 정규화 규칙을 검증한다.
 */
public class UserProfileSanitizerTest {
    @Test
    public void normalizeEmail_trimsAndLowercases() {
        assertEquals("manager@bodeul.app", UserProfileSanitizer.normalizeEmail("  Manager@Bodeul.App "));
    }

    @Test
    public void normalizeName_collapsesInnerSpaces() {
        assertEquals("김 승민", UserProfileSanitizer.normalizeName("  김   승민  "));
    }

    @Test
    public void normalizePhone_formatsKoreanMobile() {
        assertEquals("010-1234-5678", UserProfileSanitizer.normalizePhone("+82 10-1234-5678"));
    }

    @Test
    public void isValidPhone_acceptsSeoulAreaNumber() {
        assertTrue(UserProfileSanitizer.isValidPhone("02-123-4567"));
    }

    @Test
    public void isValidPhone_rejectsShortInput() {
        assertFalse(UserProfileSanitizer.isValidPhone("010-1234"));
    }
}
