package com.example.bodeul.ui.auth;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;

import java.util.List;

/**
 * 권한 안내 모델 목록을 실제 카드 뷰로 렌더링하는 바인더다.
 */
public final class PermissionGuideItemBinder {
    private final LayoutInflater inflater;

    public PermissionGuideItemBinder(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public void bindItems(ViewGroup container, List<PermissionGuideItem> items) {
        container.removeAllViews();
        for (PermissionGuideItem item : items) {
            View itemView = inflater.inflate(R.layout.item_permission_guide, container, false);
            bindItem(itemView, item);
            container.addView(itemView);
        }
    }

    private void bindItem(View itemView, PermissionGuideItem item) {
        View iconBackground = itemView.findViewById(R.id.viewPermissionIconBackground);
        ImageView iconView = itemView.findViewById(R.id.imagePermissionIcon);
        TextView titleView = itemView.findViewById(R.id.textPermissionTitle);
        TextView descriptionView = itemView.findViewById(R.id.textPermissionDescription);

        ViewCompat.setBackgroundTintList(
                iconBackground,
                ColorStateList.valueOf(ContextCompat.getColor(
                        itemView.getContext(),
                        item.getIconBackgroundColorResId()
                ))
        );
        iconView.setImageResource(item.getIconResId());
        iconView.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_text_primary));
        titleView.setText(item.getTitleResId());
        descriptionView.setText(item.getDescriptionResId());
    }
}
