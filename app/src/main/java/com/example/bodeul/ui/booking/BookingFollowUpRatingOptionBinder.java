package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.google.android.material.card.MaterialCardView;

/**
 * 후기 선택 카드를 선택 상태에 맞게 렌더링한다.
 */
public final class BookingFollowUpRatingOptionBinder {
    private final Context context;

    public BookingFollowUpRatingOptionBinder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(
            View itemView,
            BookingFollowUpRatingOptionModel model,
            Listener listener
    ) {
        MaterialCardView cardView = (MaterialCardView) itemView;
        TextView titleView = itemView.findViewById(R.id.textBookingFollowUpRatingTitle);
        TextView bodyView = itemView.findViewById(R.id.textBookingFollowUpRatingBody);

        titleView.setText(model.getTitleText());
        bodyView.setText(model.getBodyText());

        int backgroundColor = ContextCompat.getColor(
                context,
                model.isSelected() ? R.color.bodeul_surface_alt : R.color.bodeul_surface
        );
        int strokeColor = ContextCompat.getColor(
                context,
                model.isSelected() ? R.color.bodeul_primary : R.color.bodeul_outline
        );
        int titleColor = ContextCompat.getColor(
                context,
                model.isSelected() ? R.color.bodeul_primary : R.color.bodeul_text_primary
        );
        cardView.setCardBackgroundColor(backgroundColor);
        cardView.setStrokeColor(strokeColor);
        cardView.setRippleColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.bodeul_soft_blue)
        ));
        titleView.setTextColor(titleColor);

        cardView.setOnClickListener(view -> listener.onSelectRating(model.getRating()));
    }

    public interface Listener {
        void onSelectRating(AppointmentFollowUpReviewRating rating);
    }
}
