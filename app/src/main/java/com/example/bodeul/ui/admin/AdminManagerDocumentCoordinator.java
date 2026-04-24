package com.example.bodeul.ui.admin;

import android.text.TextUtils;

import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 서류 검토 카드와 이력 목록에 필요한 화면 모델을 조합한다.
 */
public final class AdminManagerDocumentCoordinator {
    private final AdminManagerDocumentPresentationFormatter formatter;

    public AdminManagerDocumentCoordinator(AdminManagerDocumentPresentationFormatter formatter) {
        this.formatter = formatter;
    }

    public List<AdminManagerDocumentCardModel> createDocumentCards(
            List<ManagerDocumentOverview> overviews,
            boolean loading
    ) {
        List<AdminManagerDocumentCardModel> cards = new ArrayList<>();
        for (ManagerDocumentOverview overview : overviews) {
            ManagerHomeProfile profile = overview.getProfile();
            boolean hasDocumentSummary = !TextUtils.isEmpty(profile.getDocumentSummary());
            cards.add(new AdminManagerDocumentCardModel(
                    overview.getManager().getId(),
                    formatter.buildTitle(overview.getManager()),
                    formatter.toStatusLabel(profile.getDocumentStatus()),
                    formatter.getStatusBackgroundColorResId(profile.getDocumentStatus()),
                    formatter.getStatusTextColorResId(profile.getDocumentStatus()),
                    formatter.buildSummaryText(profile),
                    formatter.buildAvailabilityText(profile),
                    formatter.buildReviewNoteText(profile),
                    formatter.buildTimelineText(profile),
                    hasDocumentSummary,
                    !loading && hasDocumentSummary,
                    !overview.getHistoryEntries().isEmpty(),
                    !loading
            ));
        }
        return cards;
    }

    public List<AdminManagerDocumentHistoryItemModel> createHistoryItems(
            ManagerDocumentOverview overview
    ) {
        List<AdminManagerDocumentHistoryItemModel> items = new ArrayList<>();
        for (ManagerDocumentHistoryEntry historyEntry : overview.getHistoryEntries()) {
            String actorName = TextUtils.isEmpty(historyEntry.getActorName())
                    ? overview.getManager().getName()
                    : historyEntry.getActorName();
            items.add(new AdminManagerDocumentHistoryItemModel(
                    formatter.toHistoryBadgeLabel(historyEntry.getEventType()),
                    formatter.getHistoryBadgeBackgroundColorResId(historyEntry.getEventType()),
                    formatter.getHistoryBadgeTextColorResId(historyEntry.getEventType()),
                    formatter.formatTimestamp(historyEntry.getHappenedAtMillis()),
                    formatter.buildHistoryActorText(actorName),
                    formatter.buildHistoryBody(historyEntry)
            ));
        }
        return items;
    }
}
