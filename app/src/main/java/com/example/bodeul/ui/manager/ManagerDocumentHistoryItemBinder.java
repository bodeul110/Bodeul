package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 서류 검토 이력 표현 모델을 카드 뷰에 바인딩한다.
 */
public final class ManagerDocumentHistoryItemBinder {
    private final Context context;

    public ManagerDocumentHistoryItemBinder(Context context) {
        this.context = context;
    }

    public void bind(View itemView, ManagerDocumentHistoryItemModel model) {
        TextView badgeView = itemView.findViewById(R.id.textManagerDocumentHistoryBadge);
        TextView timestampView = itemView.findViewById(R.id.textManagerDocumentHistoryTimestamp);
        TextView actorView = itemView.findViewById(R.id.textManagerDocumentHistoryActor);
        TextView bodyView = itemView.findViewById(R.id.textManagerDocumentHistoryBody);

        badgeView.setText(model.getBadgeText());
        badgeView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, model.getBadgeBackgroundColorRes())
        ));
        badgeView.setTextColor(ContextCompat.getColor(context, model.getBadgeTextColorRes()));
        timestampView.setText(model.getTimestampText());
        actorView.setText(model.getActorText());
        bodyView.setText(model.getBodyText());
    }
}

