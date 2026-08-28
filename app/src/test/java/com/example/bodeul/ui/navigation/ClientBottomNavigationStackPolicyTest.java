package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientBottomNavigationStackPolicyTest {
    @Test
    public void shouldFinishCurrent_keepsHomeUnderEveryDifferentTopLevelScreen() {
        for (ClientBottomNavigationTab destinationTab : ClientBottomNavigationTab.values()) {
            if (destinationTab != ClientBottomNavigationTab.HOME) {
                assertFalse(ClientBottomNavigationStackPolicy.shouldFinishCurrent(
                        ClientBottomNavigationTab.HOME,
                        destinationTab
                ));
            }
        }
    }

    @Test
    public void shouldFinishCurrent_replacesEveryNonHomeWhenOpeningDifferentTab() {
        for (ClientBottomNavigationTab currentTab : ClientBottomNavigationTab.values()) {
            if (currentTab == ClientBottomNavigationTab.HOME) {
                continue;
            }
            for (ClientBottomNavigationTab destinationTab : ClientBottomNavigationTab.values()) {
                if (destinationTab != currentTab) {
                    assertTrue(ClientBottomNavigationStackPolicy.shouldFinishCurrent(
                            currentTab,
                            destinationTab
                    ));
                }
            }
        }
    }

    @Test
    public void shouldFinishCurrent_doesNothingForReselection() {
        for (ClientBottomNavigationTab tab : ClientBottomNavigationTab.values()) {
            assertFalse(ClientBottomNavigationStackPolicy.shouldFinishCurrent(tab, tab));
        }
    }
}
