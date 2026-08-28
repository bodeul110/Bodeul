package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

public class ClientBottomNavigationVisibilityTest {
    @Test
    public void isVisibleFor_allowsOnlyPatientAndGuardianShell() {
        assertTrue(ClientBottomNavigationVisibility.isVisibleFor(UserRole.PATIENT));
        assertTrue(ClientBottomNavigationVisibility.isVisibleFor(UserRole.GUARDIAN));
        assertFalse(ClientBottomNavigationVisibility.isVisibleFor(UserRole.MANAGER));
        assertFalse(ClientBottomNavigationVisibility.isVisibleFor(UserRole.ADMIN));
        assertFalse(ClientBottomNavigationVisibility.isVisibleFor(null));
    }
}
