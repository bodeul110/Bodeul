package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 과거 동행 이력의 운영 지표 카드를 바인딩한다.
 */
public final class ManagerHistoryMetricBinder {
    public void bind(View itemView, ManagerHistoryMetricModel model) {
        TextView labelView = itemView.findViewById(R.id.textManagerHistoryMetricLabel);
        TextView valueView = itemView.findViewById(R.id.textManagerHistoryMetricValue);
        TextView helperView = itemView.findViewById(R.id.textManagerHistoryMetricHelper);

        labelView.setText(model.getLabelText());
        valueView.setText(model.getValueText());
        helperView.setText(model.getHelperText());

        switch (model.getTone()) {
            case WARNING:
                labelView.setBackgroundResource(R.drawable.bg_badge_yellow);
                labelView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_text_primary)
                );
                break;
            case SUCCESS:
                labelView.setBackgroundResource(R.drawable.bg_badge_green);
                labelView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_success)
                );
                break;
            case PURPLE:
                labelView.setBackgroundResource(R.drawable.bg_badge_purple);
                labelView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
                );
                break;
            case PRIMARY:
            default:
                labelView.setBackgroundResource(R.drawable.bg_badge_blue);
                labelView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
                );
                break;
        }
    }
}
