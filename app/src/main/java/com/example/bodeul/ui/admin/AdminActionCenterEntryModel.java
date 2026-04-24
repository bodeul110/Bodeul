package com.example.bodeul.ui.admin;

/**
 * 후속 알림 또는 감사 로그 카드 한 건을 표현한다.
 */
public final class AdminActionCenterEntryModel {
    private final String entryId;
    private final String badgeText;
    private final AdminActionCenterTone tone;
    private final String stateText;
    private final AdminActionCenterTone stateTone;
    private final String priorityText;
    private final AdminActionCenterTone priorityTone;
    private final String titleText;
    private final String bodyText;
    private final String metaText;
    private final java.util.List<AdminActionCenterActionModel> actionModels;

    public AdminActionCenterEntryModel(
            String entryId,
            String badgeText,
            AdminActionCenterTone tone,
            String stateText,
            AdminActionCenterTone stateTone,
            String priorityText,
            AdminActionCenterTone priorityTone,
            String titleText,
            String bodyText,
            String metaText,
            java.util.List<AdminActionCenterActionModel> actionModels
    ) {
        this.entryId = entryId == null ? "" : entryId;
        this.badgeText = badgeText == null ? "" : badgeText;
        this.tone = tone == null ? AdminActionCenterTone.PRIMARY : tone;
        this.stateText = stateText == null ? "" : stateText;
        this.stateTone = stateTone == null ? AdminActionCenterTone.PRIMARY : stateTone;
        this.priorityText = priorityText == null ? "" : priorityText;
        this.priorityTone = priorityTone == null ? AdminActionCenterTone.PRIMARY : priorityTone;
        this.titleText = titleText == null ? "" : titleText;
        this.bodyText = bodyText == null ? "" : bodyText;
        this.metaText = metaText == null ? "" : metaText;
        this.actionModels = actionModels == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(actionModels));
    }

    public String getEntryId() {
        return entryId;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public AdminActionCenterTone getTone() {
        return tone;
    }

    public String getStateText() {
        return stateText;
    }

    public AdminActionCenterTone getStateTone() {
        return stateTone;
    }

    public String getPriorityText() {
        return priorityText;
    }

    public AdminActionCenterTone getPriorityTone() {
        return priorityTone;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getMetaText() {
        return metaText;
    }

    public java.util.List<AdminActionCenterActionModel> getActionModels() {
        return actionModels;
    }
}
