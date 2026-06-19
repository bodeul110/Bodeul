package com.example.bodeul.ui.support;

import android.content.Context;
import android.graphics.Rect;
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
    public interface SupportActionListener {
        void onToggleResponse(String requestId);
        void onClearFocusMode();
    }

    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textRequestSummary;
    private final TextView textLatestSummary;
    private final View focusModeContainer;
    private final TextView textFocusModeTitle;
    private final TextView textFocusModeBody;
    private final TextView buttonFocusModeClear;
    private final LinearLayout requestContainer;
    private final TextView textEmpty;
    private final ClientSupportRequestCardBinder requestCardBinder;
    private SupportActionListener supportActionListener;

    public ClientSupportBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textRequestSummary,
            TextView textLatestSummary,
            View focusModeContainer,
            TextView textFocusModeTitle,
            TextView textFocusModeBody,
            TextView buttonFocusModeClear,
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
        this.focusModeContainer = focusModeContainer;
        this.textFocusModeTitle = textFocusModeTitle;
        this.textFocusModeBody = textFocusModeBody;
        this.buttonFocusModeClear = buttonFocusModeClear;
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
        bindFocusMode(screenModel);

        requestContainer.removeAllViews();
        textEmpty.setVisibility(screenModel.getRequestCards().isEmpty() ? View.VISIBLE : View.GONE);
        for (ClientSupportRequestCardModel cardModel : screenModel.getRequestCards()) {
            if (screenModel.isFocusModeActive() && !cardModel.isFocused()) {
                continue;
            }
            View itemView = inflater.inflate(R.layout.item_client_support_request, requestContainer, false);
            requestCardBinder.bind(itemView, cardModel, requestId -> {
                if (supportActionListener != null) {
                    supportActionListener.onToggleResponse(requestId);
                }
            });
            requestContainer.addView(itemView);
            if (cardModel.isFocused()) {
                itemView.post(() -> itemView.requestRectangleOnScreen(
                        new Rect(0, 0, itemView.getWidth(), itemView.getHeight()),
                        true
                ));
            }
        }
    }

    private void bindFocusMode(ClientSupportScreenModel screenModel) {
        if (!screenModel.isFocusModeActive()) {
            focusModeContainer.setVisibility(View.GONE);
            buttonFocusModeClear.setOnClickListener(null);
            return;
        }
        focusModeContainer.setVisibility(View.VISIBLE);
        textFocusModeTitle.setText(screenModel.getFocusModeTitleText());
        textFocusModeBody.setText(screenModel.getFocusModeBodyText());
        buttonFocusModeClear.setText(screenModel.getFocusModeActionText());
        buttonFocusModeClear.setOnClickListener(view -> {
            if (supportActionListener != null) {
                supportActionListener.onClearFocusMode();
            }
        });
    }

    public void setSupportActionListener(SupportActionListener supportActionListener) {
        this.supportActionListener = supportActionListener;
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
