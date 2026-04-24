package com.example.bodeul.ui.admin;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 병원 가이드 카드를 뷰에 바인딩한다.
 */
public final class AdminGuideCardBinder {
    public interface Listener {
        void onEditGuide(String guideId);

        void onDeleteGuide(String guideId);
    }

    public void bind(View itemView, AdminGuideCardModel model, Listener listener) {
        TextView titleView = itemView.findViewById(R.id.textAdminGuideTitle);
        TextView countView = itemView.findViewById(R.id.textAdminGuideCount);
        TextView previewView = itemView.findViewById(R.id.textAdminGuidePreview);
        MaterialButton editButton = itemView.findViewById(R.id.buttonAdminGuideEdit);
        MaterialButton deleteButton = itemView.findViewById(R.id.buttonAdminGuideDelete);

        titleView.setText(model.getTitleText());
        countView.setText(model.getCountText());
        previewView.setText(model.getPreviewText());
        editButton.setOnClickListener(view -> listener.onEditGuide(model.getGuideId()));
        deleteButton.setOnClickListener(view -> listener.onDeleteGuide(model.getGuideId()));
    }
}
