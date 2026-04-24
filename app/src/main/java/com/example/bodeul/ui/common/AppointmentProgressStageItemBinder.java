package com.example.bodeul.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.card.MaterialCardView;

/**
 * 예약 진행 단계 카드를 공통 규칙으로 렌더링한다.
 */
public final class AppointmentProgressStageItemBinder {
    private final Context context;

    public AppointmentProgressStageItemBinder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(View itemView, AppointmentProgressStageModel model) {
        MaterialCardView cardView = (MaterialCardView) itemView;
        TextView badgeView = itemView.findViewById(R.id.textAppointmentProgressStageBadge);
        TextView titleView = itemView.findViewById(R.id.textAppointmentProgressStageTitle);
        TextView bodyView = itemView.findViewById(R.id.textAppointmentProgressStageBody);
        TextView stateView = itemView.findViewById(R.id.textAppointmentProgressStageState);

        badgeView.setText(String.valueOf(model.getOrder()));
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        stateView.setText(model.getStateText());

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
