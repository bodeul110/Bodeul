package com.example.bodeul.ui.booking;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 실시간 위치 확인 화면의 지도 fallback 카드 한 건을 바인딩한다.
 */
public final class BookingLiveLocationMapActionBinder {
    public interface Listener {
        void onMapActionClick(BookingLiveLocationMapActionModel model);
    }

    private final Listener listener;

    public BookingLiveLocationMapActionBinder(Listener listener) {
        this.listener = listener;
    }

    public void bind(View view, BookingLiveLocationMapActionModel model) {
        TextView titleView = view.findViewById(R.id.textBookingLiveMapActionTitle);
        TextView bodyView = view.findViewById(R.id.textBookingLiveMapActionBody);
        MaterialButton buttonView = view.findViewById(R.id.buttonBookingLiveMapAction);

        titleView.setText(model.getTitle());
        bodyView.setText(model.getBody());
        buttonView.setText(model.getButtonLabel());
        buttonView.setOnClickListener(v -> listener.onMapActionClick(model));
    }
}
