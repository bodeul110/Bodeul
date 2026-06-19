package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 실시간 위치 확인 화면 모델을 실제 뷰에 반영한다.
 */
public final class BookingLiveLocationBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textStatusSectionTitle;
    private final TextView textMemoSectionTitle;
    private final TextView textMapSectionTitle;
    private final TextView textMapSectionHelper;
    private final TextView textMapHighlightTitle;
    private final TextView textMapHighlightBody;
    private final LinearLayout statusLineContainer;
    private final LinearLayout memoLineContainer;
    private final LinearLayout mapActionContainer;
    private final MaterialButton buttonPrimary;
    private final MaterialButton buttonRefresh;
    private final BookingLiveLocationMapActionBinder mapActionBinder;

    public BookingLiveLocationBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textTitle,
            TextView textSubtitle,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textStatusSectionTitle,
            TextView textMemoSectionTitle,
            TextView textMapSectionTitle,
            TextView textMapSectionHelper,
            TextView textMapHighlightTitle,
            TextView textMapHighlightBody,
            LinearLayout statusLineContainer,
            LinearLayout memoLineContainer,
            LinearLayout mapActionContainer,
            MaterialButton buttonPrimary,
            MaterialButton buttonRefresh,
            BookingLiveLocationMapActionBinder mapActionBinder
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textTitle = textTitle;
        this.textSubtitle = textSubtitle;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textStatusSectionTitle = textStatusSectionTitle;
        this.textMemoSectionTitle = textMemoSectionTitle;
        this.textMapSectionTitle = textMapSectionTitle;
        this.textMapSectionHelper = textMapSectionHelper;
        this.textMapHighlightTitle = textMapHighlightTitle;
        this.textMapHighlightBody = textMapHighlightBody;
        this.statusLineContainer = statusLineContainer;
        this.memoLineContainer = memoLineContainer;
        this.mapActionContainer = mapActionContainer;
        this.buttonPrimary = buttonPrimary;
        this.buttonRefresh = buttonRefresh;
        this.mapActionBinder = mapActionBinder;
    }

    public void bindScreen(BookingLiveLocationScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeLabel());
        textTitle.setText(screenModel.getTitle());
        textSubtitle.setText(screenModel.getSubtitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroTitle.setText(screenModel.getHeroTitle());
        textHeroBody.setText(screenModel.getHeroBody());
        textStatusSectionTitle.setText(screenModel.getStatusSectionTitle());
        textMemoSectionTitle.setText(screenModel.getMemoSectionTitle());
        textMapSectionTitle.setText(screenModel.getMapSectionTitle());
        textMapSectionHelper.setText(screenModel.getMapSectionHelper());
        textMapHighlightTitle.setText(screenModel.getMapHighlightTitle());
        textMapHighlightBody.setText(screenModel.getMapHighlightBody());
        buttonPrimary.setText(screenModel.getPrimaryActionLabel());
        buttonRefresh.setText(screenModel.getRefreshActionLabel());

        bindLines(statusLineContainer, screenModel.getStatusLines());
        bindLines(memoLineContainer, screenModel.getMemoLines());
        bindMapActions(screenModel.getMapActions());
    }

    private void bindLines(LinearLayout container, List<BookingStatusLineItem> items) {
        container.removeAllViews();
        for (int index = 0; index < items.size(); index++) {
            View itemView = inflater.inflate(R.layout.item_booking_status_line, container, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(10);
            }
            itemView.setLayoutParams(params);

            TextView labelView = itemView.findViewById(R.id.textBookingStatusLineLabel);
            TextView valueView = itemView.findViewById(R.id.textBookingStatusLineValue);
            BookingStatusLineItem item = items.get(index);
            labelView.setText(item.getLabelText());
            valueView.setText(item.getValueText());
            valueView.setTextColor(ContextCompat.getColor(
                    context,
                    item.isEmphasized() ? R.color.bodeul_text_primary : R.color.bodeul_text_secondary
            ));
            valueView.setTextSize(item.isEmphasized() ? 15f : 14f);
            container.addView(itemView);
        }
    }

    private void bindMapActions(List<BookingLiveLocationMapActionModel> items) {
        mapActionContainer.removeAllViews();
        for (BookingLiveLocationMapActionModel item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_booking_live_location_map_action,
                    mapActionContainer,
                    false
            );
            mapActionBinder.bind(itemView, item);
            mapActionContainer.addView(itemView);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
