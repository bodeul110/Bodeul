package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 지원 문의 카드 모델을 뷰에 바인딩한다.
 */
public final class ManagerSupportInquiryCardBinder {
    private final Context context;

    public ManagerSupportInquiryCardBinder(Context context) {
        this.context = context;
    }

    public void bind(View itemView, ManagerSupportInquiryCardModel model) {
        TextView categoryView = itemView.findViewById(R.id.textManagerSupportInquiryCategory);
        TextView statusView = itemView.findViewById(R.id.textManagerSupportInquiryStatus);
        TextView titleView = itemView.findViewById(R.id.textManagerSupportInquiryTitle);
        TextView bodyView = itemView.findViewById(R.id.textManagerSupportInquiryBody);
        TextView timestampView = itemView.findViewById(R.id.textManagerSupportInquiryTimestamp);
        View responseContainer = itemView.findViewById(R.id.layoutManagerSupportInquiryResponse);
        TextView responseBodyView = itemView.findViewById(R.id.textManagerSupportInquiryResponseBody);
        TextView responseMetaView = itemView.findViewById(R.id.textManagerSupportInquiryResponseMeta);

        categoryView.setText(model.getCategoryText());
        statusView.setText(model.getStatusText());
        statusView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, model.getStatusBackgroundColorRes())
        ));
        statusView.setTextColor(ContextCompat.getColor(context, model.getStatusTextColorRes()));
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        timestampView.setText(model.getTimestampText());

        responseContainer.setVisibility(model.isShowResponse() ? View.VISIBLE : View.GONE);
        responseBodyView.setText(model.getResponseBodyText());
        responseMetaView.setText(model.getResponseMetaText());
    }
}
