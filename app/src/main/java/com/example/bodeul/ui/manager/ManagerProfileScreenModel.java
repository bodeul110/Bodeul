package com.example.bodeul.ui.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 매니저 내 페이지를 그리기 위한 표현 모델이다.
 */
public final class ManagerProfileScreenModel {
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String uploadHighlightTitleText;
    private final String uploadHighlightBodyText;
    private final List<ManagerDocumentFileCardModel> documentFileCards;
    private final List<ManagerInfoLineItem> accountLines;
    private final List<ManagerInfoLineItem> documentLines;
    private final String reviewNoteText;
    private final String timelineText;
    private final List<ManagerDocumentHistoryItemModel> historyItems;

    public ManagerProfileScreenModel(
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String uploadHighlightTitleText,
            String uploadHighlightBodyText,
            List<ManagerDocumentFileCardModel> documentFileCards,
            List<ManagerInfoLineItem> accountLines,
            List<ManagerInfoLineItem> documentLines,
            String reviewNoteText,
            String timelineText,
            List<ManagerDocumentHistoryItemModel> historyItems
    ) {
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText;
        this.heroBodyText = heroBodyText;
        this.uploadHighlightTitleText = uploadHighlightTitleText;
        this.uploadHighlightBodyText = uploadHighlightBodyText;
        this.documentFileCards = documentFileCards == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(documentFileCards));
        this.accountLines = accountLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(accountLines));
        this.documentLines = documentLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(documentLines));
        this.reviewNoteText = reviewNoteText;
        this.timelineText = timelineText;
        this.historyItems = historyItems == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(historyItems));
    }

    public String getModeText() {
        return modeText;
    }

    public String getHeroBadgeText() {
        return heroBadgeText;
    }

    public String getHeroTitleText() {
        return heroTitleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getUploadHighlightTitleText() {
        return uploadHighlightTitleText;
    }

    public String getUploadHighlightBodyText() {
        return uploadHighlightBodyText;
    }

    public List<ManagerDocumentFileCardModel> getDocumentFileCards() {
        return documentFileCards;
    }

    public List<ManagerInfoLineItem> getAccountLines() {
        return accountLines;
    }

    public List<ManagerInfoLineItem> getDocumentLines() {
        return documentLines;
    }

    public String getReviewNoteText() {
        return reviewNoteText;
    }

    public String getTimelineText() {
        return timelineText;
    }

    public List<ManagerDocumentHistoryItemModel> getHistoryItems() {
        return historyItems;
    }
}
