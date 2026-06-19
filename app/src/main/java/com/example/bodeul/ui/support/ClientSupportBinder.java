package com.example.bodeul.ui.support;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

/**
 * 사용자 문의 화면 모델을 실제 뷰에 바인딩한다.
 */
public final class ClientSupportBinder {
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textRequestSummary;
    private final TextView textLatestSummary;
    private final LinearLayout requestContainer;
    private final TextView textEmpty;
    private final ClientSupportRequestCardBinder requestCardBinder;

    public ClientSupportBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textRequestSummary,
            TextView textLatestSummary,
            LinearLayout requestContainer,
            TextView textEmpty
    ) {
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textRequestSummary = textRequestSummary;
        this.textLatestSummary = textLatestSummary;
        this.requestContainer = requestContainer;
        this.textEmpty = textEmpty;
        this.requestCardBinder = new ClientSupportRequestCardBinder(context);
    }

    public void bindScreen(ClientSupportScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
        bindOptionalText(textRequestSummary, screenModel.getRequestSummaryText());
        textLatestSummary.setText(screenModel.getLatestSummaryText());

        requestContainer.removeAllViews();
        textEmpty.setVisibility(screenModel.getRequestCards().isEmpty() ? View.VISIBLE : View.GONE);
        for (ClientSupportRequestCardModel cardModel : screenModel.getRequestCards()) {
            View itemView = inflater.inflate(R.layout.item_client_support_request, requestContainer, false);
            requestCardBinder.bind(itemView, cardModel);
            requestContainer.addView(itemView);
        }
    }

    private void bindOptionalText(TextView textView, String value) {
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(value);
    }
}
