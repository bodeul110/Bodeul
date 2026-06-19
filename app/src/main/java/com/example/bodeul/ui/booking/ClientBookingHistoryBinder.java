package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public final class ClientBookingHistoryBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textListSectionTitle;
    private final TextView textListSectionHelper;
    private final LinearLayout entryContainer;
    private final MaterialButton buttonManage;

    public ClientBookingHistoryBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textTitle,
            TextView textSubtitle,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textListSectionTitle,
            TextView textListSectionHelper,
            LinearLayout entryContainer,
            MaterialButton buttonManage
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textTitle = textTitle;
        this.textSubtitle = textSubtitle;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textListSectionTitle = textListSectionTitle;
        this.textListSectionHelper = textListSectionHelper;
        this.entryContainer = entryContainer;
        this.buttonManage = buttonManage;
    }

    public void bindScreen(ClientBookingHistoryScreenModel screenModel, EntryClickListener listener) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeLabel());
        textTitle.setText(screenModel.getTitle());
        textSubtitle.setText(screenModel.getSubtitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroTitle.setText(screenModel.getHeroTitle());
        textHeroBody.setText(screenModel.getHeroBody());
        textListSectionTitle.setText(screenModel.getListSectionTitle());
        textListSectionHelper.setText(screenModel.getListSectionHelper());
        buttonManage.setText(screenModel.getManageActionLabel());
        bindEntries(screenModel.getEntries(), listener);
    }

    private void bindEntries(List<ClientBookingHistoryEntryModel> items, EntryClickListener listener) {
        entryContainer.removeAllViews();
        for (ClientBookingHistoryEntryModel item : items) {
            View itemView = inflater.inflate(R.layout.item_booking_request, entryContainer, false);
            bindEntryView(itemView, item, listener);
            entryContainer.addView(itemView);
        }
    }

    private void bindEntryView(View itemView, ClientBookingHistoryEntryModel model, EntryClickListener listener) {
        MaterialCardView cardView = (MaterialCardView) itemView;
        TextView statusView = itemView.findViewById(R.id.textBookingRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textBookingRequestTitle);
        TextView detailView = itemView.findViewById(R.id.textBookingRequestDetail);
        TextView placeView = itemView.findViewById(R.id.textBookingRequestPlace);
        TextView linkedView = itemView.findViewById(R.id.textBookingRequestLinked);
        TextView optionView = itemView.findViewById(R.id.textBookingRequestOption);
        TextView profileView = itemView.findViewById(R.id.textBookingRequestProfile);
        TextView priceView = itemView.findViewById(R.id.textBookingRequestPrice);
        TextView noteView = itemView.findViewById(R.id.textBookingRequestNote);
        View actionsView = itemView.findViewById(R.id.layoutBookingRequestActions);

        statusView.setText(model.getStatusLabel());
        statusView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, model.getStatusBackgroundColorResId())
        ));
        statusView.setTextColor(ContextCompat.getColor(context, model.getStatusTextColorResId()));
        titleView.setText(model.getTitle());
        detailView.setText(model.getDetail());
        placeView.setText(model.getPlace());
        linkedView.setText(model.getLinked());
        bindOptionalText(optionView, model.getOption());
        bindOptionalText(profileView, model.getProfile());
        bindOptionalText(priceView, model.getPrice());
        bindOptionalText(noteView, model.getNote());
        actionsView.setVisibility(View.GONE);
        cardView.setStrokeColor(ContextCompat.getColor(context, model.getStrokeColorResId()));
        itemView.setOnClickListener(view -> listener.onOpenRequest(model.getRequestId()));
    }

    private void bindOptionalText(TextView textView, String value) {
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(value);
    }

    public interface EntryClickListener {
        void onOpenRequest(String requestId);
    }
}
