package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.bodeul.R;

/**
 * 빠른 액션 카드 한 장을 화면 모델 기준으로 바인딩한다.
 */
public final class ManagerHomeActionCardBinder {
    public ManagerHomeActionCardBinder() {
    }

    public void bind(View itemView, ManagerHomeActionCardModel actionCardModel) {
        ImageView iconView = itemView.findViewById(R.id.imageManagerActionIcon);
        TextView titleView = itemView.findViewById(R.id.textManagerActionTitle);
        TextView bodyView = itemView.findViewById(R.id.textManagerActionBody);
        TextView statusView = itemView.findViewById(R.id.textManagerActionStatus);

        iconView.setImageResource(resolveIconResId(actionCardModel.getActionType()));
        iconView.setBackgroundResource(resolveIconBackgroundResId(actionCardModel.getActionType()));
        iconView.setContentDescription(actionCardModel.getTitleText());
        titleView.setText(actionCardModel.getTitleText());
        bodyView.setText(actionCardModel.getBodyText());

        if (TextUtils.isEmpty(actionCardModel.getStatusText())) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(actionCardModel.getStatusText());
        }
    }

    private int resolveIconResId(ManagerHomeActionType actionType) {
        switch (actionType) {
            case DOCUMENT:
                return R.drawable.manager_home_icon_document;
            case SCHEDULE:
                return R.drawable.manager_home_icon_calendar;
            case HISTORY:
                return R.drawable.manager_home_icon_history;
            case SUPPORT:
            default:
                return R.drawable.manager_home_icon_support;
        }
    }

    private int resolveIconBackgroundResId(ManagerHomeActionType actionType) {
        switch (actionType) {
            case DOCUMENT:
                return R.drawable.bg_manager_home_icon_blue;
            case SCHEDULE:
                return R.drawable.bg_manager_home_icon_yellow;
            case HISTORY:
                return R.drawable.bg_manager_home_icon_purple;
            case SUPPORT:
            default:
                return R.drawable.bg_manager_home_icon_green;
        }
    }
}
