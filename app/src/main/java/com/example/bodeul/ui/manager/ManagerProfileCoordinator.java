package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * 매니저 내 페이지에 필요한 표시 문구와 카드 데이터를 조합한다.
 */
public final class ManagerProfileCoordinator {
    private final Context context;
    private final ManagerHomePresentationFormatter formatter;

    public ManagerProfileCoordinator(Context context, ManagerHomePresentationFormatter formatter) {
        this.context = context;
        this.formatter = formatter;
    }

    public ManagerProfileScreenModel createScreenModel(
            User currentUser,
            ManagerDocumentOverview overview,
            boolean firebaseBacked
    ) {
        ManagerHomeProfile profile = overview.getProfile();
        return new ManagerProfileScreenModel(
                context.getString(firebaseBacked
                        ? R.string.manager_home_mode_firebase
                        : R.string.manager_home_mode_demo),
                formatter.toDocumentStatusLabel(profile.getDocumentStatus()),
                context.getString(R.string.manager_profile_hero_title, currentUser.getName()),
                context.getString(
                        R.string.manager_profile_hero_body,
                        safeValue(currentUser.getPhone(), R.string.manager_profile_contact_missing),
                        safeValue(currentUser.getEmail(), R.string.manager_profile_contact_missing)
                ),
                createAccountLines(currentUser),
                createDocumentLines(profile),
                formatter.buildDocumentReviewNote(profile.getDocumentReviewNote()),
                formatter.buildDocumentTimelineText(profile),
                createHistoryItems(overview, currentUser)
        );
    }

    private List<ManagerInfoLineItem> createAccountLines(User currentUser) {
        List<ManagerInfoLineItem> items = new ArrayList<>();
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_name),
                safeValue(currentUser.getName(), R.string.manager_profile_contact_missing),
                true
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_phone),
                safeValue(currentUser.getPhone(), R.string.manager_profile_contact_missing),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_email),
                safeValue(currentUser.getEmail(), R.string.manager_profile_contact_missing),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_role),
                context.getString(R.string.manager_profile_role_manager),
                false
        ));
        return items;
    }

    private List<ManagerInfoLineItem> createDocumentLines(ManagerHomeProfile profile) {
        List<ManagerInfoLineItem> items = new ArrayList<>();
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_document_status),
                formatter.toDocumentStatusLabel(profile.getDocumentStatus()),
                true
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_document_summary),
                safeValue(profile.getDocumentSummary(), R.string.manager_profile_document_summary_empty),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_schedule_summary),
                safeValue(profile.getAvailabilitySummary(), R.string.manager_profile_schedule_summary_empty),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_profile_line_document_files),
                formatter.buildDocumentFileSummary(profile),
                false
        ));
        return items;
    }

    private List<ManagerDocumentHistoryItemModel> createHistoryItems(
            ManagerDocumentOverview overview,
            User currentUser
    ) {
        List<ManagerDocumentHistoryItemModel> items = new ArrayList<>();
        for (ManagerDocumentHistoryEntry historyEntry : overview.getHistoryEntries()) {
            items.add(new ManagerDocumentHistoryItemModel(
                    toHistoryBadgeText(historyEntry.getEventType()),
                    toHistoryBadgeBackground(historyEntry.getEventType()),
                    toHistoryBadgeTextColor(historyEntry.getEventType()),
                    formatter.formatTimestamp(historyEntry.getHappenedAtMillis()),
                    context.getString(
                            R.string.manager_profile_history_actor,
                            resolveActorName(historyEntry, currentUser)
                    ),
                    buildHistoryBody(historyEntry)
            ));
        }
        return items;
    }

    private String resolveActorName(ManagerDocumentHistoryEntry historyEntry, User currentUser) {
        if (!TextUtils.isEmpty(historyEntry.getActorName())) {
            return historyEntry.getActorName();
        }
        return currentUser.getName();
    }

    private String buildHistoryBody(ManagerDocumentHistoryEntry historyEntry) {
        String detail = historyEntry.getEventType() == ManagerDocumentHistoryEventType.SUBMITTED
                ? historyEntry.getSummary()
                : historyEntry.getReviewNote();
        if (TextUtils.isEmpty(detail)) {
            detail = context.getString(R.string.manager_profile_history_body_empty);
        }

        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.APPROVED) {
            return context.getString(R.string.manager_profile_history_approved_body, detail);
        }
        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.REJECTED) {
            return context.getString(R.string.manager_profile_history_rejected_body, detail);
        }
        return context.getString(R.string.manager_profile_history_submitted_body, detail);
    }

    private String toHistoryBadgeText(ManagerDocumentHistoryEventType eventType) {
        if (eventType == ManagerDocumentHistoryEventType.APPROVED) {
            return context.getString(R.string.manager_profile_history_approved);
        }
        if (eventType == ManagerDocumentHistoryEventType.REJECTED) {
            return context.getString(R.string.manager_profile_history_rejected);
        }
        return context.getString(R.string.manager_profile_history_submitted);
    }

    private int toHistoryBadgeBackground(ManagerDocumentHistoryEventType eventType) {
        if (eventType == ManagerDocumentHistoryEventType.APPROVED) {
            return R.color.bodeul_success;
        }
        if (eventType == ManagerDocumentHistoryEventType.REJECTED) {
            return R.color.bodeul_warning;
        }
        return R.color.bodeul_primary;
    }

    private int toHistoryBadgeTextColor(ManagerDocumentHistoryEventType eventType) {
        if (eventType == ManagerDocumentHistoryEventType.REJECTED) {
            return R.color.bodeul_text_primary;
        }
        return R.color.white;
    }

    private String safeValue(String value, int emptyResId) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(emptyResId);
        }
        return formatter.summarizeCardText(value);
    }
}
