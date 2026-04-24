package com.example.bodeul.ui.report;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * 보호자 요청별 진행 카드를 뷰에 바인딩한다.
 */
public final class GuardianReportEntryCardBinder {
    public interface Listener {
        void onOpenRequestDetail(String requestId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;

    public GuardianReportEntryCardBinder(Context context, LayoutInflater inflater, Listener listener) {
        this.context = context.getApplicationContext();
        this.inflater = inflater;
        this.listener = listener;
    }

    public void bind(View cardView, GuardianReportEntryCardModel model) {
        MaterialCardView rootCard = (MaterialCardView) cardView;
        TextView textTitle = cardView.findViewById(R.id.textGuardianReportEntryTitle);
        TextView textStatus = cardView.findViewById(R.id.textGuardianReportEntryStatus);
        TextView textHeroBody = cardView.findViewById(R.id.textGuardianReportEntryHeroBody);
        TextView textLiveTitle = cardView.findViewById(R.id.textGuardianReportEntryLiveTitle);
        LinearLayout liveContainer = cardView.findViewById(R.id.guardianReportEntryLiveContainer);
        TextView textReportTitle = cardView.findViewById(R.id.textGuardianReportEntryReportTitle);
        LinearLayout reportContainer = cardView.findViewById(R.id.guardianReportEntryReportContainer);
        TextView textPending = cardView.findViewById(R.id.textGuardianReportEntryPending);
        MaterialButton buttonAction = cardView.findViewById(R.id.buttonGuardianReportEntryAction);

        textTitle.setText(model.getTitleText());
        textStatus.setText(toStatusLabel(model.getStatus()));
        tintStatusBadge(textStatus, model.getStatus());
        textHeroBody.setText(model.getHeroBodyText());
        textLiveTitle.setText(model.getLiveSectionTitleText());
        textReportTitle.setText(model.getReportSectionTitleText());
        bindLines(liveContainer, model.getLiveLines());
        bindLines(reportContainer, model.getReportLines());

        if (model.getPendingReportText() == null) {
            textPending.setVisibility(View.GONE);
        } else {
            textPending.setVisibility(View.VISIBLE);
            textPending.setText(model.getPendingReportText());
        }

        buttonAction.setText(model.getActionLabelText());
        if (model.getRequestId() == null) {
            buttonAction.setVisibility(View.GONE);
            rootCard.setOnClickListener(null);
        } else {
            buttonAction.setVisibility(View.VISIBLE);
            buttonAction.setOnClickListener(view -> listener.onOpenRequestDetail(model.getRequestId()));
            rootCard.setOnClickListener(view -> listener.onOpenRequestDetail(model.getRequestId()));
        }
    }

    private void bindLines(LinearLayout container, List<GuardianReportLineItem> items) {
        container.removeAllViews();
        for (int index = 0; index < items.size(); index++) {
            View itemView = inflater.inflate(R.layout.item_guardian_report_line, container, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(10);
            }
            itemView.setLayoutParams(params);

            TextView labelView = itemView.findViewById(R.id.textGuardianReportLineLabel);
            TextView valueView = itemView.findViewById(R.id.textGuardianReportLineValue);
            GuardianReportLineItem item = items.get(index);
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

    private void tintStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case MATCHED:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case IN_PROGRESS:
            case COMPLETED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case CANCELED:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
            case REQUESTED:
            default:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
        }
        textView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor)));
        textView.setTextColor(ContextCompat.getColor(context, textColor));
    }

    private String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_requested);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
