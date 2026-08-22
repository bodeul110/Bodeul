package com.example.bodeul.ui.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EntryFlowCoordinatorTest {
    @Test
    public void shouldShowPermissionGuide_beforeGuideCompletion() {
        assertTrue(EntryFlowCoordinator.shouldShowPermissionGuide(false));
    }

    @Test
    public void shouldNotShowPermissionGuide_afterGuideCompletion() {
        assertFalse(EntryFlowCoordinator.shouldShowPermissionGuide(true));
    }
}
