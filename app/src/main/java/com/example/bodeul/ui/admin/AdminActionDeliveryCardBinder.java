package com.example.bodeul.ui.admin;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 운영 알림 전송 현황 카드를 뷰에 바인딩한다.
 */
public final class AdminActionDeliveryCardBinder {
    public void bind(View itemView, AdminActionDeliveryCardModel model) {
        TextView channelView = itemView.findViewById(R.id.textAdminActionDeliveryChannel);
        TextView statusView = itemView.findViewById(R.id.textAdminActionDeliveryStatus);
        TextView stateView = itemView.findViewById(R.id.textAdminActionDeliveryState);
        TextView titleView = itemView.findViewById(R.id.textAdminActionDeliveryTitle);
        TextView bodyView = itemView.findViewById(R.id.textAdminActionDeliveryBody);
        TextView metaView = itemView.findViewById(R.id.textAdminActionDeliveryMeta);

        channelView.setText(model.getChannelText());
        bindBadge(itemView, channelView, model.getChannelTone());
        statusView.setText(model.getStatusText());
        bindBadge(itemView, statusView, model.getStatusTone());
        stateView.setText(model.getStateText());
        bindBadge(itemView, stateView, model.getStateTone());
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        metaView.setText(model.getMetaText());
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
}
