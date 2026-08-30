package com.example.bodeul.ui.auth;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;
import com.google.android.material.card.MaterialCardView;

/**
 * 역할 카드 한 장의 선택 상태를 전담해 액티비티가 뷰 세부 구현을 알지 않게 만든다.
 */
public final class RoleOptionCardBinder {
    private final Context context;
    private final MaterialCardView cardView;
    private final View selectedBadge;
    private final TextView actionView;

    public RoleOptionCardBinder(
            Context context,
            MaterialCardView cardView,
            View selectedBadge,
            TextView actionView
    ) {
        this.context = context;
        this.cardView = cardView;
        this.selectedBadge = selectedBadge;
        this.actionView = actionView;
    }

    public void render(boolean selected) {
        cardView.setChecked(selected);
        cardView.setSelected(selected);
        ViewCompat.setStateDescription(
                cardView,
                context.getString(
                        selected
                                ? R.string.role_selection_state_selected
                                : R.string.role_selection_state_not_selected
                )
        );
        cardView.setStrokeWidth(dpToPx(selected ? 2 : 0));
        cardView.setStrokeColor(ContextCompat.getColor(
                context,
                selected ? R.color.figma_mvp_primary : android.R.color.transparent
        ));
        selectedBadge.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        actionView.setTextColor(ContextCompat.getColor(
                context,
                R.color.figma_mvp_primary
        ));
    }

    private int dpToPx(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }
}
