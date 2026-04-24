package com.example.bodeul.ui.admin;

import android.text.TextUtils;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 후속 알림과 감사 로그 카드를 실제 뷰에 바인딩한다.
 */
public final class AdminActionCenterEntryBinder {
    public interface Listener {
        void onClickAction(String entryId, AdminActionCenterActionType actionType);
    }

    public void bind(View itemView, AdminActionCenterEntryModel model, Listener listener) {
        TextView badgeView = itemView.findViewById(R.id.textAdminActionCenterBadge);
        TextView stateView = itemView.findViewById(R.id.textAdminActionCenterState);
        TextView priorityView = itemView.findViewById(R.id.textAdminActionCenterPriority);
        TextView titleView = itemView.findViewById(R.id.textAdminActionCenterTitle);
        TextView bodyView = itemView.findViewById(R.id.textAdminActionCenterBody);
        TextView metaView = itemView.findViewById(R.id.textAdminActionCenterMeta);
        LinearLayout actionContainer = itemView.findViewById(R.id.adminActionCenterActionContainer);

        badgeView.setText(model.getBadgeText());
        bindBadge(itemView, badgeView, model.getTone());

        stateView.setText(model.getStateText());
        bindBadge(itemView, stateView, model.getStateTone());

        if (TextUtils.isEmpty(model.getPriorityText())) {
            priorityView.setVisibility(View.GONE);
        } else {
            priorityView.setVisibility(View.VISIBLE);
            priorityView.setText(model.getPriorityText());
            bindBadge(itemView, priorityView, model.getPriorityTone());
        }

        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        metaView.setText(model.getMetaText());

        actionContainer.removeAllViews();
        actionContainer.setVisibility(model.getActionModels().isEmpty() ? View.GONE : View.VISIBLE);
        for (AdminActionCenterActionModel actionModel : model.getActionModels()) {
            MaterialButton button = new MaterialButton(
                    itemView.getContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(itemView, 8));
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(itemView, 18));
            button.setText(actionModel.getLabelText());
            button.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
            ));
            button.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary));
            button.setOnClickListener(view ->
                    listener.onClickAction(model.getEntryId(), actionModel.getActionType()));
            actionContainer.addView(button);
        }
    }

    private void bindBadge(View itemView, TextView badgeView, AdminActionCenterTone tone) {
        switch (tone) {
            case WARNING:
                badgeView.setBackgroundResource(R.drawable.bg_badge_yellow);
                badgeView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_text_primary)
                );
                return;
            case SUCCESS:
                badgeView.setBackgroundResource(R.drawable.bg_badge_green);
                badgeView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_success)
                );
                return;
            case PRIMARY:
            default:
                badgeView.setBackgroundResource(R.drawable.bg_badge_blue);
                badgeView.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary)
                );
        }
    }

    private int dpToPx(View itemView, int value) {
        return Math.round(value * itemView.getResources().getDisplayMetrics().density);
    }
}
