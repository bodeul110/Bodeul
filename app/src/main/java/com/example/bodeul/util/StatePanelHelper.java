package com.example.bodeul.util;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * 여러 화면에서 공통으로 쓰는 상태 패널의 톤과 버튼 노출을 한곳에서 맞춘다.
 */
public final class StatePanelHelper {
    private StatePanelHelper() {
    }

    public static void hide(View panelRoot) {
        panelRoot.setVisibility(View.GONE);
    }

    public static void show(
            View panelRoot,
            Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        MaterialCardView cardView = (MaterialCardView) panelRoot;
        TextView badgeView = panelRoot.findViewById(R.id.textStatePanelBadge);
        TextView titleView = panelRoot.findViewById(R.id.textStatePanelTitle);
        TextView bodyView = panelRoot.findViewById(R.id.textStatePanelBody);
        MaterialButton primaryButton = panelRoot.findViewById(R.id.buttonStatePanelPrimary);
        MaterialButton secondaryButton = panelRoot.findViewById(R.id.buttonStatePanelSecondary);

        badgeView.setText(badge);
        titleView.setText(title);
        bodyView.setText(body);

        int badgeBackgroundColor = resolveBadgeBackgroundColor(panelRoot, tone);
        int badgeTextColor = resolveBadgeTextColor(panelRoot, tone);
        int strokeColor = resolveStrokeColor(panelRoot, tone);

        cardView.setStrokeColor(strokeColor);
        badgeView.setBackgroundTintList(ColorStateList.valueOf(badgeBackgroundColor));
        badgeView.setTextColor(badgeTextColor);

        bindButton(primaryButton, primaryText, primaryListener);
        bindButton(secondaryButton, secondaryText, secondaryListener);
        panelRoot.setVisibility(View.VISIBLE);
    }

    private static void bindButton(
            MaterialButton button,
            @Nullable CharSequence text,
            @Nullable View.OnClickListener listener
    ) {
        boolean visible = !TextUtils.isEmpty(text) && listener != null;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setOnClickListener(visible ? listener : null);
        if (visible) {
            button.setText(text);
        }
    }

    private static int resolveBadgeBackgroundColor(View panelRoot, Tone tone) {
        switch (tone) {
            case WARNING:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_soft_yellow);
            case ERROR:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_soft_red);
            case INFO:
            default:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_soft_blue);
        }
    }

    private static int resolveBadgeTextColor(View panelRoot, Tone tone) {
        switch (tone) {
            case WARNING:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_text_primary);
            case ERROR:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_error);
            case INFO:
            default:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_primary);
        }
    }

    private static int resolveStrokeColor(View panelRoot, Tone tone) {
        switch (tone) {
            case WARNING:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_warning);
            case ERROR:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_error);
            case INFO:
            default:
                return ContextCompat.getColor(panelRoot.getContext(), R.color.bodeul_outline);
        }
    }

    public enum Tone {
        INFO,
        WARNING,
        ERROR
    }
}
