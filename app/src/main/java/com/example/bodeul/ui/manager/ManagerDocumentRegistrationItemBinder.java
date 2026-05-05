package com.example.bodeul.ui.manager;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 서류 등록 업로드 카드 한 장을 실제 뷰에 연결한다.
 */
public final class ManagerDocumentRegistrationItemBinder {
    public void bind(View itemView, ManagerDocumentRegistrationItemModel itemModel) {
        TextView titleView = itemView.findViewById(R.id.textManagerDocumentRegistrationTitle);
        TextView badgeView = itemView.findViewById(R.id.textManagerDocumentRegistrationBadge);
        TextView helperView = itemView.findViewById(R.id.textManagerDocumentRegistrationHelper);
        TextView fileNameView = itemView.findViewById(R.id.textManagerDocumentRegistrationFileName);
        TextView fileMetaView = itemView.findViewById(R.id.textManagerDocumentRegistrationFileMeta);
        MaterialButton actionButton = itemView.findViewById(R.id.buttonManagerDocumentRegistrationUpload);

        titleView.setText(itemModel.getTitleText());
        badgeView.setText(itemModel.getBadgeText());
        ViewCompat.setBackgroundTintList(
                badgeView,
                ColorStateList.valueOf(ContextCompat.getColor(
                        itemView.getContext(),
                        itemModel.getBadgeBackgroundColorResId()
                ))
        );
        badgeView.setTextColor(ContextCompat.getColor(
                itemView.getContext(),
                itemModel.getBadgeTextColorResId()
        ));
        helperView.setText(itemModel.getHelperText());

        if (TextUtils.isEmpty(itemModel.getFileNameText())) {
            fileNameView.setVisibility(View.GONE);
            fileNameView.setText("");
        } else {
            fileNameView.setVisibility(View.VISIBLE);
            fileNameView.setText(itemModel.getFileNameText());
        }

        if (TextUtils.isEmpty(itemModel.getFileMetaText())) {
            fileMetaView.setVisibility(View.GONE);
            fileMetaView.setText("");
        } else {
            fileMetaView.setVisibility(View.VISIBLE);
            fileMetaView.setText(itemModel.getFileMetaText());
        }

        actionButton.setText(itemModel.getActionText());
    }
}
