package com.example.bodeul.ui.support;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 사용자 문의 카드 모델을 실제 카드 레이아웃에 바인딩한다.
 */
public final class ClientSupportRequestCardBinder {
    private final Context context;

    public ClientSupportRequestCardBinder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(View itemView, ClientSupportRequestCardModel model) {
        TextView categoryView = itemView.findViewById(R.id.textClientSupportRequestCategory);
        TextView statusView = itemView.findViewById(R.id.textClientSupportRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textClientSupportRequestTitle);
        TextView bodyView = itemView.findViewById(R.id.textClientSupportRequestBody);
        TextView timestampView = itemView.findViewById(R.id.textClientSupportRequestTimestamp);
        View responseContainer = itemView.findViewById(R.id.layoutClientSupportRequestResponse);
        TextView responseBodyView = itemView.findViewById(R.id.textClientSupportRequestResponseBody);
        TextView responseMetaView = itemView.findViewById(R.id.textClientSupportRequestResponseMeta);

        categoryView.setText(model.getCategoryText());
        statusView.setText(model.getStatusText());
        statusView.setBackgroundResource(R.drawable.bg_surface_pill);
        statusView.getBackground().setTint(ContextCompat.getColor(context, model.getStatusBackgroundColorResId()));
        statusView.setTextColor(ContextCompat.getColor(context, model.getStatusTextColorResId()));
        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());
        timestampView.setText(model.getTimestampText());

        if (model.hasResponse()) {
            responseContainer.setVisibility(View.VISIBLE);
            responseBodyView.setText(model.getResponseBodyText());
            responseMetaView.setText(model.getResponseMetaText());
        } else {
            responseContainer.setVisibility(View.GONE);
        }
    }
}
