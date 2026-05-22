package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

/**
 * 지도 fallback 액션 카드 한 개를 바인딩한다.
 */
public final class ManagerGuideMapActionBinder {
    public interface Listener {
        void onMapActionClick(ManagerGuideMapActionModel model);
    }

    private final Listener listener;

    public ManagerGuideMapActionBinder(Listener listener) {
        this.listener = listener;
    }

    public void bind(View view, ManagerGuideMapActionModel model) {
        TextView titleView = view.findViewById(R.id.textGuideMapActionTitle);
        TextView bodyView = view.findViewById(R.id.textGuideMapActionBody);
        MaterialButton buttonView = view.findViewById(R.id.buttonGuideMapAction);

        titleView.setText(model.getTitle());
        bodyView.setText(model.getBody());
        buttonView.setText(model.getButtonLabel());
        buttonView.setOnClickListener(v -> listener.onMapActionClick(model));
    }
}
