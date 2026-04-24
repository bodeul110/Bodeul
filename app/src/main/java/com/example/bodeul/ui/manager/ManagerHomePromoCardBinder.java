package com.example.bodeul.ui.manager;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 소개 카드 한 장을 화면 모델 기준으로 바인딩한다.
 */
public final class ManagerHomePromoCardBinder {
    private final Context context;

    public ManagerHomePromoCardBinder(Context context) {
        this.context = context;
    }

    public void bind(View itemView, ManagerHomePromoCardModel promoCardModel) {
        View bannerView = itemView.findViewById(R.id.viewManagerPromoBanner);
        TextView badgeView = itemView.findViewById(R.id.textManagerPromoBadge);
        TextView titleView = itemView.findViewById(R.id.textManagerPromoTitle);
        TextView bodyView = itemView.findViewById(R.id.textManagerPromoBody);

        bannerView.setBackgroundResource(promoCardModel.getBannerBackgroundResId());
        badgeView.setText(promoCardModel.getBadgeText());
        badgeView.setBackgroundResource(promoCardModel.getBadgeBackgroundResId());
        badgeView.setTextColor(ContextCompat.getColor(context, promoCardModel.getBadgeTextColorResId()));
        titleView.setText(promoCardModel.getTitleText());
        bodyView.setText(promoCardModel.getBodyText());
    }
}
