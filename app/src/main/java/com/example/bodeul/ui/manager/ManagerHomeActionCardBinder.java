package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 빠른 액션 카드 한 장을 화면 모델 기준으로 바인딩한다.
 */
public final class ManagerHomeActionCardBinder {
    private final Context context;

    public ManagerHomeActionCardBinder(Context context) {
        this.context = context;
    }

    public void bind(View itemView, ManagerHomeActionCardModel actionCardModel) {
        TextView badgeView = itemView.findViewById(R.id.textManagerActionBadge);
        TextView titleView = itemView.findViewById(R.id.textManagerActionTitle);
        TextView bodyView = itemView.findViewById(R.id.textManagerActionBody);
        TextView statusView = itemView.findViewById(R.id.textManagerActionStatus);

        badgeView.setText(actionCardModel.getBadgeText());
        badgeView.setBackgroundResource(actionCardModel.getBadgeBackgroundResId());
        badgeView.setTextColor(ContextCompat.getColor(context, actionCardModel.getBadgeTextColorResId()));
        titleView.setText(actionCardModel.getTitleText());
        bodyView.setText(actionCardModel.getBodyText());

        if (TextUtils.isEmpty(actionCardModel.getStatusText())) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(actionCardModel.getStatusText());
        }
    }
}
