package com.example.bodeul.ui.manager;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;

/**
 * 문서별 원본 파일 상태 카드를 실제 뷰에 바인딩한다.
 */
public final class ManagerDocumentFileCardBinder {
    public void bind(View itemView, ManagerDocumentFileCardModel cardModel) {
        TextView textTitle = itemView.findViewById(R.id.textManagerDocumentFileTitle);
        TextView textBadge = itemView.findViewById(R.id.textManagerDocumentFileBadge);
        TextView textBody = itemView.findViewById(R.id.textManagerDocumentFileBody);
        TextView textTimestamp = itemView.findViewById(R.id.textManagerDocumentFileTimestamp);

        textTitle.setText(cardModel.getTitleText());
        textBadge.setText(cardModel.getBadgeText());
        ViewCompat.setBackgroundTintList(
                textBadge,
                ColorStateList.valueOf(ContextCompat.getColor(
                        itemView.getContext(),
                        cardModel.getBadgeBackgroundColorResId()
                ))
        );
        textBadge.setTextColor(ContextCompat.getColor(
                itemView.getContext(),
                cardModel.getBadgeTextColorResId()
        ));
        textBody.setText(cardModel.getBodyText());

        if (TextUtils.isEmpty(cardModel.getTimestampText())) {
            textTimestamp.setVisibility(View.GONE);
            textTimestamp.setText("");
            return;
        }
        textTimestamp.setVisibility(View.VISIBLE);
        textTimestamp.setText(cardModel.getTimestampText());
    }
}
