package com.example.bodeul.ui.support;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.ui.common.AttentionBannerModel;
import com.example.bodeul.ui.common.AttentionBannerTone;

public final class ClientSupportAttentionBannerFactory {
    private ClientSupportAttentionBannerFactory() {
    }

    @Nullable
    public static AttentionBannerModel create(Context context, int unreadCount, int staleUnreadCount) {
        if (unreadCount <= 0) {
            return null;
        }
        if (staleUnreadCount > 0) {
            return new AttentionBannerModel(
                    context.getString(R.string.support_attention_badge_overdue),
                    context.getString(R.string.support_attention_title_overdue),
                    context.getString(
                            R.string.support_attention_body_overdue,
                            unreadCount,
                            staleUnreadCount
                    ),
                    context.getString(R.string.support_attention_action_open),
                    AttentionBannerTone.CRITICAL
            );
        }
        return new AttentionBannerModel(
                context.getString(R.string.support_attention_badge_unread),
                context.getString(R.string.support_attention_title_unread),
                context.getString(R.string.support_attention_body_unread, unreadCount),
                context.getString(R.string.support_attention_action_open),
                AttentionBannerTone.WARNING
        );
    }
}
