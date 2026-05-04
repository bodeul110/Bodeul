package com.example.bodeul.ui.manager;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

/**
 * 매니저 내 페이지 화면 모델을 실제 뷰 계층에 바인딩한다.
 */
public final class ManagerProfileBinder {
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textUploadHighlightTitle;
    private final TextView textUploadHighlightBody;
    private final LinearLayout accountContainer;
    private final LinearLayout documentFileContainer;
    private final LinearLayout documentContainer;
    private final TextView textReviewNote;
    private final TextView textTimeline;
    private final LinearLayout historyContainer;
    private final TextView textHistoryEmpty;
    private final ManagerDocumentHistoryItemBinder historyItemBinder;
    private final ManagerDocumentFileCardBinder fileCardBinder;

    public ManagerProfileBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textUploadHighlightTitle,
            TextView textUploadHighlightBody,
            LinearLayout accountContainer,
            LinearLayout documentFileContainer,
            LinearLayout documentContainer,
            TextView textReviewNote,
            TextView textTimeline,
            LinearLayout historyContainer,
            TextView textHistoryEmpty
    ) {
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textUploadHighlightTitle = textUploadHighlightTitle;
        this.textUploadHighlightBody = textUploadHighlightBody;
        this.accountContainer = accountContainer;
        this.documentFileContainer = documentFileContainer;
        this.documentContainer = documentContainer;
        this.textReviewNote = textReviewNote;
        this.textTimeline = textTimeline;
        this.historyContainer = historyContainer;
        this.textHistoryEmpty = textHistoryEmpty;
        this.historyItemBinder = new ManagerDocumentHistoryItemBinder(context);
        this.fileCardBinder = new ManagerDocumentFileCardBinder();
    }

    public void bindScreen(ManagerProfileScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
        textUploadHighlightTitle.setText(screenModel.getUploadHighlightTitleText());
        textUploadHighlightBody.setText(screenModel.getUploadHighlightBodyText());
        textReviewNote.setText(screenModel.getReviewNoteText());
        textTimeline.setText(screenModel.getTimelineText());

        bindInfoLines(accountContainer, screenModel.getAccountLines());
        bindDocumentFiles(screenModel.getDocumentFileCards());
        bindInfoLines(documentContainer, screenModel.getDocumentLines());
        bindHistory(screenModel);
    }

    private void bindInfoLines(LinearLayout container, java.util.List<ManagerInfoLineItem> items) {
        container.removeAllViews();
        for (ManagerInfoLineItem item : items) {
            View itemView = inflater.inflate(R.layout.item_manager_info_line, container, false);
            TextView labelView = itemView.findViewById(R.id.textManagerInfoLineLabel);
            TextView valueView = itemView.findViewById(R.id.textManagerInfoLineValue);
            labelView.setText(item.getLabelText());
            valueView.setText(item.getValueText());
            valueView.setTypeface(valueView.getTypeface(), item.isEmphasized() ? Typeface.BOLD : Typeface.NORMAL);
            valueView.setTextSize(item.isEmphasized() ? 16f : 14f);
            container.addView(itemView);
        }
    }

    private void bindDocumentFiles(java.util.List<ManagerDocumentFileCardModel> items) {
        documentFileContainer.removeAllViews();
        for (ManagerDocumentFileCardModel item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_manager_document_file_status,
                    documentFileContainer,
                    false
            );
            fileCardBinder.bind(itemView, item);
            documentFileContainer.addView(itemView);
        }
    }

    private void bindHistory(ManagerProfileScreenModel screenModel) {
        historyContainer.removeAllViews();
        textHistoryEmpty.setVisibility(screenModel.getHistoryItems().isEmpty() ? View.VISIBLE : View.GONE);
        for (ManagerDocumentHistoryItemModel itemModel : screenModel.getHistoryItems()) {
            View itemView = inflater.inflate(R.layout.item_manager_document_history, historyContainer, false);
            historyItemBinder.bind(itemView, itemModel);
            historyContainer.addView(itemView);
        }
    }
}
