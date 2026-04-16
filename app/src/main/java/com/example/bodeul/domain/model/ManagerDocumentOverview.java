package com.example.bodeul.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManagerDocumentOverview {
    private final User manager;
    private final ManagerHomeProfile profile;
    private final List<ManagerDocumentHistoryEntry> historyEntries;

    public ManagerDocumentOverview(User manager, ManagerHomeProfile profile) {
        this(manager, profile, Collections.emptyList());
    }

    public ManagerDocumentOverview(
            User manager,
            ManagerHomeProfile profile,
            List<ManagerDocumentHistoryEntry> historyEntries
    ) {
        this.manager = manager;
        this.profile = profile;
        this.historyEntries = historyEntries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(historyEntries));
    }

    public User getManager() {
        return manager;
    }

    public ManagerHomeProfile getProfile() {
        return profile;
    }

    public List<ManagerDocumentHistoryEntry> getHistoryEntries() {
        return historyEntries;
    }
}
