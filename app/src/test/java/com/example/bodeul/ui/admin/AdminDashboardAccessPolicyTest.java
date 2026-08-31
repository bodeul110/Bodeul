package com.example.bodeul.ui.admin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdminDashboardAccessPolicyTest {
    @Test
    public void canLoadLegacyDashboard_blocksFirebaseAdminData() {
        assertFalse(AdminDashboardAccessPolicy.canLoadLegacyDashboard(true));
    }

    @Test
    public void canLoadLegacyDashboard_keepsMockDemoAvailable() {
        assertTrue(AdminDashboardAccessPolicy.canLoadLegacyDashboard(false));
    }
}
