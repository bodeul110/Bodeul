package com.example.bodeul.ui.admin;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 관리자 문의 응답 카드 모델을 실제 카드 레이아웃에 바인딩한다.
 */
public final class AdminSupportInquiryCardBinder {
    public interface Listener {
        void onRespond(AdminSupportInquiryCardModel cardModel);
    }

    public void bind(View itemView, AdminSupportInquiryCardModel model, Listener listener) {
        TextView categoryView = itemView.findViewById(R.id.textAdminSupportCategory);
        TextView statusView = itemView.findViewById(R.id.textAdminSupportStatus);
        TextView managerView = itemView.findViewById(R.id.textAdminSupportManager);
        TextView titleView = itemView.findViewById(R.id.textAdminSupportTitle);
        TextView bodyView = itemView.findViewById(R.id.textAdminSupportBody);
        TextView timestampView = itemView.findViewById(R.id.textAdminSupportTimestamp);
        View responseContainer = itemView.findViewById(R.id.layoutAdminSupportResponse);
        TextView responseView = itemView.findViewById(R.id.textAdminSupportResponseBody);
        TextView responseMetaView = itemView.findViewById(R.id.textAdminSupportResponseMeta);
        MaterialButton actionButton = itemView.findViewById(R.id.buttonAdminSupportRespond);

        categoryView.setText(model.getCategoryText());
        statusView.setText(model.getStatusText());
        statusView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(itemView.getContext(), model.getStatusBackgroundColorResId())
        ));
        statusView.setTextColor(ContextCompat.getColor(itemView.getContext(), model.getStatusTextColorResId()));
        managerView.setText(model.getManagerText());
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        timestampView.setText(model.getTimestampText());
        responseContainer.setVisibility(model.isShowResponse() ? View.VISIBLE : View.GONE);
        responseView.setText(model.getResponseText());
        responseMetaView.setText(model.getResponseMetaText());
        actionButton.setText(model.getActionButtonText());
        actionButton.setOnClickListener(view -> listener.onRespond(model));
    }
}
