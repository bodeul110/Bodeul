package com.example.bodeul.ui.support;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 사용자 문의 카드 모델을 실제 카드 레이아웃에 바인딩한다.
 */
public final class ClientSupportRequestCardBinder {
    public interface RequestActionListener {
        void onToggleResponse(String requestId);
    }

    private final Context context;

    public ClientSupportRequestCardBinder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(View itemView, ClientSupportRequestCardModel model, RequestActionListener listener) {
        MaterialCardView cardView = (MaterialCardView) itemView;
        View headerContainer = itemView.findViewById(R.id.layoutClientSupportRequestHeader);
        TextView categoryView = itemView.findViewById(R.id.textClientSupportRequestCategory);
        TextView statusView = itemView.findViewById(R.id.textClientSupportRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textClientSupportRequestTitle);
        TextView bodyView = itemView.findViewById(R.id.textClientSupportRequestBody);
        TextView timestampView = itemView.findViewById(R.id.textClientSupportRequestTimestamp);
        TextView toggleView = itemView.findViewById(R.id.buttonClientSupportResponseToggle);
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
        headerContainer.setVisibility(model.isCompactFocusMode() ? View.GONE : View.VISIBLE);
        categoryView.setVisibility(model.isCompactFocusMode() ? View.GONE : View.VISIBLE);
        statusView.setVisibility(model.isCompactFocusMode() ? View.GONE : View.VISIBLE);
        timestampView.setVisibility(model.isCompactFocusMode() ? View.GONE : View.VISIBLE);
        if (model.isFocused()) {
            cardView.setStrokeColor(ContextCompat.getColor(context, R.color.bodeul_primary));
            cardView.setStrokeWidth(dpToPx(2));
        } else if (model.isStaleUnread()) {
            cardView.setStrokeColor(ContextCompat.getColor(context, R.color.bodeul_error));
            cardView.setStrokeWidth(dpToPx(2));
        } else {
            cardView.setStrokeColor(ContextCompat.getColor(context, R.color.bodeul_outline));
            cardView.setStrokeWidth(dpToPx(1));
        }

        if (model.hasResponse()) {
            if (model.isCompactFocusMode()) {
                toggleView.setVisibility(View.GONE);
                toggleView.setOnClickListener(null);
                responseContainer.setVisibility(View.VISIBLE);
            } else {
                toggleView.setVisibility(View.VISIBLE);
                toggleView.setText(model.isExpanded()
                        ? R.string.client_support_response_collapse
                        : R.string.client_support_response_expand);
                toggleView.setOnClickListener(view -> listener.onToggleResponse(model.getRequestId()));
                responseContainer.setVisibility(model.isExpanded() ? View.VISIBLE : View.GONE);
            }
            responseBodyView.setText(model.getResponseBodyText());
            responseMetaView.setText(model.getResponseMetaText());
        } else {
            toggleView.setVisibility(View.GONE);
            toggleView.setOnClickListener(null);
            responseContainer.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
