package com.example.bodeul.ui.admin;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 관리자 서류 검토 이력 모델을 이력 카드 뷰에 바인딩한다.
 */
public final class AdminManagerDocumentHistoryItemBinder {
    public void bind(View itemView, AdminManagerDocumentHistoryItemModel model) {
        TextView badgeView = itemView.findViewById(R.id.textAdminDocumentHistoryBadge);
        TextView timestampView = itemView.findViewById(R.id.textAdminDocumentHistoryTimestamp);
        TextView actorView = itemView.findViewById(R.id.textAdminDocumentHistoryActor);
        TextView bodyView = itemView.findViewById(R.id.textAdminDocumentHistoryBody);

        badgeView.setText(model.getBadgeText());
        badgeView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(itemView.getContext(), model.getBadgeBackgroundColorResId())
        ));
        badgeView.setTextColor(
                ContextCompat.getColor(itemView.getContext(), model.getBadgeTextColorResId())
        );
        timestampView.setText(model.getTimestampText());
        actorView.setText(model.getActorText());
        bodyView.setText(model.getBodyText());
    }
}
