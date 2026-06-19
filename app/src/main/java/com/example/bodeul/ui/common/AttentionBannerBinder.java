package com.example.bodeul.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public final class AttentionBannerBinder {
    private final Context context;
    private final MaterialCardView cardView;
    private final TextView badgeView;
    private final TextView titleView;
    private final TextView bodyView;
    private final MaterialButton actionButton;

    public AttentionBannerBinder(Context context, View rootView) {
        this.context = context;
        this.cardView = (MaterialCardView) rootView;
        this.badgeView = rootView.findViewById(R.id.textAttentionBannerBadge);
        this.titleView = rootView.findViewById(R.id.textAttentionBannerTitle);
        this.bodyView = rootView.findViewById(R.id.textAttentionBannerBody);
        this.actionButton = rootView.findViewById(R.id.buttonAttentionBannerAction);
    }

    public void bind(@Nullable AttentionBannerModel model) {
        if (model == null) {
            cardView.setVisibility(View.GONE);
            return;
        }

        cardView.setVisibility(View.VISIBLE);
        badgeView.setText(model.getBadgeText());
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        actionButton.setText(model.getActionText());
        applyTone(model.getTone());
    }

    public void setOnActionClickListener(@Nullable View.OnClickListener listener) {
        actionButton.setOnClickListener(listener);
    }

    private void applyTone(AttentionBannerTone tone) {
        int cardBackgroundColor;
        int cardStrokeColor;
        int badgeBackgroundColor;
        int badgeTextColor;
        int actionTextColor;
        if (tone == AttentionBannerTone.CRITICAL) {
            cardBackgroundColor = R.color.bodeul_soft_red;
            cardStrokeColor = R.color.bodeul_error;
            badgeBackgroundColor = R.color.bodeul_surface;
            badgeTextColor = R.color.bodeul_error;
            actionTextColor = R.color.bodeul_error;
        } else {
            cardBackgroundColor = R.color.bodeul_soft_yellow;
            cardStrokeColor = R.color.bodeul_warning;
            badgeBackgroundColor = R.color.bodeul_surface;
            badgeTextColor = R.color.bodeul_text_primary;
            actionTextColor = R.color.bodeul_primary;
        }

        cardView.setCardBackgroundColor(ContextCompat.getColor(context, cardBackgroundColor));
        cardView.setStrokeColor(ContextCompat.getColor(context, cardStrokeColor));
        ViewCompat.setBackgroundTintList(
                badgeView,
                ColorStateList.valueOf(ContextCompat.getColor(context, badgeBackgroundColor))
        );
        badgeView.setTextColor(ContextCompat.getColor(context, badgeTextColor));
        actionButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)));
        actionButton.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bodeul_outline)));
        actionButton.setTextColor(ContextCompat.getColor(context, actionTextColor));
    }
}
