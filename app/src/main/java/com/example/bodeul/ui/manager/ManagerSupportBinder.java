package com.example.bodeul.ui.manager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;

/**
 * 지원팀 문의 화면 모델을 레이아웃에 바인딩한다.
 */
public final class ManagerSupportBinder {
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textLatestSummary;
    private final LinearLayout inquiryContainer;
    private final TextView textEmpty;
    private final ManagerSupportInquiryCardBinder inquiryCardBinder;

    public ManagerSupportBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textLatestSummary,
            LinearLayout inquiryContainer,
            TextView textEmpty
    ) {
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textLatestSummary = textLatestSummary;
        this.inquiryContainer = inquiryContainer;
        this.textEmpty = textEmpty;
        this.inquiryCardBinder = new ManagerSupportInquiryCardBinder(context);
    }

    public void bindScreen(ManagerSupportScreenModel screenModel) {
        textMode.setText(screenModel.getModeText());
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
        textLatestSummary.setText(screenModel.getLatestSummaryText());

        inquiryContainer.removeAllViews();
        textEmpty.setVisibility(screenModel.getInquiryCards().isEmpty() ? View.VISIBLE : View.GONE);
        for (ManagerSupportInquiryCardModel cardModel : screenModel.getInquiryCards()) {
            View itemView = inflater.inflate(R.layout.item_manager_support_inquiry, inquiryContainer, false);
            inquiryCardBinder.bind(itemView, cardModel);
            inquiryContainer.addView(itemView);
        }
    }
}

