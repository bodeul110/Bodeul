package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.util.StatePanelHelper;

/**
 * 예약 화면 상단 요약과 요청 목록 바인딩을 맡는다.
 */
public final class BookingDashboardBinder {
    private final Context context;
    private final LayoutInflater layoutInflater;
    private final BookingPresentationFormatter formatter;
    private final BookingRequestCardBinder requestCardBinder;
    private final TextView textRequesterName;
    private final TextView textRequesterRole;
    private final TextView textRequesterPhone;
    private final View latestCard;
    private final TextView textLatestStatus;
    private final TextView textLatestTitle;
    private final TextView textLatestBody;
    private final LinearLayout requestsContainer;

    public BookingDashboardBinder(
            Context context,
            LayoutInflater layoutInflater,
            BookingPresentationFormatter formatter,
            TextView textRequesterName,
            TextView textRequesterRole,
            TextView textRequesterPhone,
            View latestCard,
            TextView textLatestStatus,
            TextView textLatestTitle,
            TextView textLatestBody,
            LinearLayout requestsContainer
    ) {
        this.context = context;
        this.layoutInflater = layoutInflater;
        this.formatter = formatter;
        this.requestCardBinder = new BookingRequestCardBinder(context, layoutInflater, formatter);
        this.textRequesterName = textRequesterName;
        this.textRequesterRole = textRequesterRole;
        this.textRequesterPhone = textRequesterPhone;
        this.latestCard = latestCard;
        this.textLatestStatus = textLatestStatus;
        this.textLatestTitle = textLatestTitle;
        this.textLatestBody = textLatestBody;
        this.requestsContainer = requestsContainer;
    }

    public void bindDashboard(
            BookingDashboard dashboard,
            BookingRequestCardBinder.ActionListener actionListener
    ) {
        bindRequester(dashboard.getUser());
        bindLatestRequest(dashboard, actionListener);
        renderRequests(dashboard, actionListener);
    }

    private void bindRequester(User user) {
        textRequesterName.setText(context.getString(R.string.booking_requester_name, user.getName()));
        textRequesterRole.setText(context.getString(
                R.string.booking_requester_role,
                formatter.toRoleLabel(user.getRole())
        ));
        textRequesterPhone.setText(context.getString(
                R.string.booking_requester_phone,
                android.text.TextUtils.isEmpty(user.getPhone())
                        ? context.getString(R.string.booking_requester_phone_empty)
                        : user.getPhone()
        ));
    }

    private void bindLatestRequest(
            BookingDashboard dashboard,
            BookingRequestCardBinder.ActionListener actionListener
    ) {
        AppointmentRequest latestRequest = dashboard.getLatestRequest();
        if (latestRequest == null) {
            bindEmptySummary();
            latestCard.setOnClickListener(null);
            return;
        }

        bindStatusBadge(textLatestStatus, latestRequest.getStatus());
        textLatestTitle.setText(context.getString(
                R.string.booking_latest_title,
                latestRequest.getHospitalName(),
                latestRequest.getDepartmentName()
        ));
        textLatestBody.setText(buildLatestBody(dashboard, latestRequest));
        latestCard.setOnClickListener(view -> actionListener.onOpenRequest(latestRequest));
    }

    private CharSequence buildLatestBody(BookingDashboard dashboard, AppointmentRequest latestRequest) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(
                R.string.booking_latest_body_intro,
                dashboard.getRequests().size(),
                formatter.toStatusLabel(latestRequest.getStatus())
        ));
        builder.append('\n').append(context.getString(
                R.string.booking_latest_body_schedule,
                latestRequest.getAppointmentAt()
        ));
        builder.append('\n').append(context.getString(
                R.string.booking_latest_body_place,
                latestRequest.getMeetingPlace()
        ));
        if (!android.text.TextUtils.isEmpty(latestRequest.getTripTypeCode())
                || !android.text.TextUtils.isEmpty(latestRequest.getManagerGenderPreferenceCode())) {
            builder.append('\n').append(context.getString(
                    R.string.booking_latest_body_option,
                    formatter.toTripTypeLabel(latestRequest.getTripTypeCode()),
                    formatter.toManagerGenderPreferenceLabel(latestRequest.getManagerGenderPreferenceCode())
            ));
        }
        if (latestRequest.getFinalPrice() > 0) {
            builder.append('\n').append(context.getString(
                    R.string.booking_latest_body_price,
                    formatter.formatPrice(latestRequest.getFinalPrice()),
                    formatter.toPaymentMethodLabel(latestRequest.getPaymentMethodCode())
            ));
        }
        return builder;
    }

    private void bindEmptySummary() {
        textLatestStatus.setText(R.string.booking_empty_badge);
        textLatestStatus.setTextColor(ContextCompat.getColor(context, R.color.bodeul_text_secondary));
        textLatestStatus.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.bodeul_surface_alt)
        ));
        textLatestTitle.setText(R.string.booking_empty_title);
        textLatestBody.setText(R.string.booking_empty_body);
    }

    private void renderRequests(
            BookingDashboard dashboard,
            BookingRequestCardBinder.ActionListener actionListener
    ) {
        requestsContainer.removeAllViews();
        if (!dashboard.hasRequests()) {
            renderEmptyRequests();
            return;
        }

        for (AppointmentRequest request : dashboard.getRequests()) {
            requestsContainer.addView(requestCardBinder.createView(
                    requestsContainer,
                    request,
                    dashboard.getUser().getRole(),
                    actionListener
            ));
        }
    }

    private void renderEmptyRequests() {
        View emptyPanel = layoutInflater.inflate(R.layout.include_state_panel, requestsContainer, false);
        StatePanelHelper.show(
                emptyPanel,
                StatePanelHelper.Tone.INFO,
                context.getString(R.string.state_badge_notice),
                context.getString(R.string.booking_empty_title),
                context.getString(R.string.booking_requests_empty),
                null,
                null,
                null,
                null
        );
        requestsContainer.addView(emptyPanel);
    }

    private void bindStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor = resolveStatusBackgroundColor(status);
        int textColor = resolveStatusTextColor(status);
        textView.setText(formatter.toStatusLabel(status));
        textView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor)));
        textView.setTextColor(ContextCompat.getColor(context, textColor));
    }

    private int resolveStatusBackgroundColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_surface_alt;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    private int resolveStatusTextColor(AppointmentStatus status) {
        switch (status) {
            case REQUESTED:
            case CANCELED:
                return R.color.bodeul_text_primary;
            case MATCHED:
            case IN_PROGRESS:
            case COMPLETED:
            default:
                return R.color.white;
        }
    }
}
