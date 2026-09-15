package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuideReceptionInputPolicyTest {
    @Test
    public void queueRequiresNonblankValueWithinTwentyCharacters() {
        assertTrue(ManagerGuideReceptionInputPolicy.isValidQueue("A128"));
        assertTrue(ManagerGuideReceptionInputPolicy.isValidQueue("128"));
        assertFalse(ManagerGuideReceptionInputPolicy.isValidQueue("  "));
        assertFalse(ManagerGuideReceptionInputPolicy.isValidQueue(null));
        assertFalse(ManagerGuideReceptionInputPolicy.isValidQueue("123456789012345678901"));
    }

    @Test
    public void waitMinutesAcceptOnlyOneToNineHundredNinetyNine() {
        assertEquals(35, ManagerGuideReceptionInputPolicy.parseWaitMinutes("35"));
        assertEquals(1, ManagerGuideReceptionInputPolicy.parseWaitMinutes(" 1 "));
        assertEquals(999, ManagerGuideReceptionInputPolicy.parseWaitMinutes("999"));
        assertEquals(0, ManagerGuideReceptionInputPolicy.parseWaitMinutes(""));
        assertEquals(0, ManagerGuideReceptionInputPolicy.parseWaitMinutes("0"));
        assertEquals(0, ManagerGuideReceptionInputPolicy.parseWaitMinutes("1000"));
        assertEquals(0, ManagerGuideReceptionInputPolicy.parseWaitMinutes("abc"));
    }
}
