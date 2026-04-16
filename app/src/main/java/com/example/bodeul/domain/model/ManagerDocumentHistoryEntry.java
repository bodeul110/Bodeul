package com.example.bodeul.domain.model;

public class ManagerDocumentHistoryEntry {
    private final ManagerDocumentHistoryEventType eventType;
    private final long happenedAtMillis;
    private final String actorName;
    private final String summary;
    private final String reviewNote;

    public ManagerDocumentHistoryEntry(
            ManagerDocumentHistoryEventType eventType,
            long happenedAtMillis,
            String actorName,
            String summary,
            String reviewNote
    ) {
        this.eventType = eventType == null
                ? ManagerDocumentHistoryEventType.SUBMITTED
                : eventType;
        this.happenedAtMillis = Math.max(happenedAtMillis, 0L);
        this.actorName = actorName == null ? "" : actorName;
        this.summary = summary == null ? "" : summary;
        this.reviewNote = reviewNote == null ? "" : reviewNote;
    }

    public ManagerDocumentHistoryEventType getEventType() {
        return eventType;
    }

    public long getHappenedAtMillis() {
        return happenedAtMillis;
    }

    public String getActorName() {
        return actorName;
    }

    public String getSummary() {
        return summary;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
