package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.card.MaterialCardView;

/**
 * 단계 레일에 들어가는 작은 상태 카드를 바인딩한다.
 */
public final class ManagerGuideStageItemBinder {
    private final Context context;

    public ManagerGuideStageItemBinder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(View itemView, ManagerGuideStageModel model) {
        MaterialCardView cardView = (MaterialCardView) itemView;
        TextView badgeView = itemView.findViewById(R.id.textGuideStageBadge);
        TextView titleView = itemView.findViewById(R.id.textGuideStageTitle);
        TextView descriptionView = itemView.findViewById(R.id.textGuideStageDescription);
        TextView stateView = itemView.findViewById(R.id.textGuideStageState);

        badgeView.setText(String.valueOf(model.getOrder()));
        titleView.setText(model.getTitle());
        descriptionView.setText(model.getDescription());
        stateView.setText(model.getStateLabel());

        int badgeColor;
        int badgeTextColor;
        int strokeColor;
        int cardColor;
        switch (model.getState()) {
            case COMPLETED:
                badgeColor = ContextCompat.getColor(context, R.color.bodeul_success);
                badgeTextColor = ContextCompat.getColor(context, R.color.white);
                strokeColor = ContextCompat.getColor(context, R.color.bodeul_success);
                cardColor = ContextCompat.getColor(context, R.color.bodeul_surface);
                break;
            case ACTIVE:
                badgeColor = ContextCompat.getColor(context, R.color.bodeul_primary);
                badgeTextColor = ContextCompat.getColor(context, R.color.white);
                strokeColor = ContextCompat.getColor(context, R.color.bodeul_primary);
                cardColor = ContextCompat.getColor(context, R.color.bodeul_surface_alt);
                break;
            case UPCOMING:
            default:
                badgeColor = ContextCompat.getColor(context, R.color.bodeul_surface_alt);
                badgeTextColor = ContextCompat.getColor(context, R.color.bodeul_primary);
                strokeColor = ContextCompat.getColor(context, R.color.bodeul_outline);
                cardColor = ContextCompat.getColor(context, R.color.bodeul_surface);
                break;
        }

        badgeView.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        badgeView.setTextColor(badgeTextColor);
        stateView.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        stateView.setTextColor(badgeTextColor);
        cardView.setCardBackgroundColor(cardColor);
        cardView.setStrokeColor(strokeColor);
    }
}
