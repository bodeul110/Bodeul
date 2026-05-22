package com.example.bodeul.ui.chat;

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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public final class CompanionChatBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textSectionTitle;
    private final TextView textEmptyBody;
    private final LinearLayout messageContainer;
    private final TextInputLayout inputLayout;
    private final MaterialButton buttonSend;

    public CompanionChatBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textTitle,
            TextView textSubtitle,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textSectionTitle,
            TextView textEmptyBody,
            LinearLayout messageContainer,
            TextInputLayout inputLayout,
            MaterialButton buttonSend
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textTitle = textTitle;
        this.textSubtitle = textSubtitle;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textSectionTitle = textSectionTitle;
        this.textEmptyBody = textEmptyBody;
        this.messageContainer = messageContainer;
        this.inputLayout = inputLayout;
        this.buttonSend = buttonSend;
    }

    public void bindScreen(CompanionChatScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeLabel());
        textTitle.setText(screenModel.getTitle());
        textSubtitle.setText(screenModel.getSubtitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroTitle.setText(screenModel.getHeroTitle());
        textHeroBody.setText(screenModel.getHeroBody());
        textSectionTitle.setText(screenModel.getSectionTitle());
        textEmptyBody.setText(screenModel.getEmptyBody());
        inputLayout.setHint(screenModel.getInputHint());
        buttonSend.setText(screenModel.getSendButtonLabel());
        bindMessages(screenModel.getMessages());
    }

    private void bindMessages(List<CompanionChatMessageItemModel> items) {
        messageContainer.removeAllViews();
        textEmptyBody.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < items.size(); index++) {
            CompanionChatMessageItemModel item = items.get(index);
            View itemView = inflater.inflate(R.layout.item_companion_chat_message, messageContainer, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(12);
            }
            itemView.setLayoutParams(params);

            LinearLayout root = itemView.findViewById(R.id.layoutCompanionChatMessageRoot);
            TextView senderView = itemView.findViewById(R.id.textCompanionChatSender);
            TextView bodyView = itemView.findViewById(R.id.textCompanionChatBody);
            TextView timeView = itemView.findViewById(R.id.textCompanionChatSentAt);
            MaterialCardView cardView = itemView.findViewById(R.id.cardCompanionChatBody);

            root.setGravity(item.isMine() ? android.view.Gravity.END : android.view.Gravity.START);
            senderView.setText(item.getSenderLabel());
            bodyView.setText(item.getBody());
            timeView.setText(item.getSentAtLabel());
            cardView.setCardBackgroundColor(ContextCompat.getColor(
                    context,
                    item.isMine() ? R.color.bodeul_soft_blue : R.color.bodeul_surface_alt
            ));
            senderView.setTextColor(ContextCompat.getColor(
                    context,
                    item.isMine() ? R.color.bodeul_primary : R.color.bodeul_text_secondary
            ));
            messageContainer.addView(itemView);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
