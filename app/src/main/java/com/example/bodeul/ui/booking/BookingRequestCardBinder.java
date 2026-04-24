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
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * 예약 요청 카드 한 장의 표현 로직을 담당한다.
 */
public final class BookingRequestCardBinder {
    private final Context context;
    private final LayoutInflater layoutInflater;
    private final BookingPresentationFormatter formatter;

    public BookingRequestCardBinder(
            Context context,
            LayoutInflater layoutInflater,
            BookingPresentationFormatter formatter
    ) {
        this.context = context;
        this.layoutInflater = layoutInflater;
        this.formatter = formatter;
    }

    public View createView(
            LinearLayout parent,
            AppointmentRequest request,
            UserRole currentUserRole,
            ActionListener actionListener
    ) {
        View requestView = layoutInflater.inflate(R.layout.item_booking_request, parent, false);
        MaterialCardView cardView = (MaterialCardView) requestView;
        TextView statusView = requestView.findViewById(R.id.textBookingRequestStatus);
        TextView titleView = requestView.findViewById(R.id.textBookingRequestTitle);
        TextView detailView = requestView.findViewById(R.id.textBookingRequestDetail);
        TextView placeView = requestView.findViewById(R.id.textBookingRequestPlace);
        TextView linkedView = requestView.findViewById(R.id.textBookingRequestLinked);
        TextView optionView = requestView.findViewById(R.id.textBookingRequestOption);
        TextView profileView = requestView.findViewById(R.id.textBookingRequestProfile);
        TextView priceView = requestView.findViewById(R.id.textBookingRequestPrice);
        TextView noteView = requestView.findViewById(R.id.textBookingRequestNote);
        View actionsView = requestView.findViewById(R.id.layoutBookingRequestActions);
        MaterialButton editButton = requestView.findViewById(R.id.buttonBookingRequestEdit);
        MaterialButton cancelButton = requestView.findViewById(R.id.buttonBookingRequestCancel);

        bindStatusBadge(statusView, request.getStatus());
        titleView.setText(context.getString(
                R.string.booking_request_title,
                request.getHospitalName(),
                request.getDepartmentName()
        ));
        detailView.setText(context.getString(
                R.string.booking_request_detail,
                request.getAppointmentAt()
        ));
        placeView.setText(context.getString(
                R.string.booking_request_place,
                request.getMeetingPlace()
        ));
        linkedView.setText(context.getString(
                R.string.booking_request_linked,
                formatter.toCounterpartRoleLabel(currentUserRole),
                formatter.buildLinkedParticipantLine(request, currentUserRole)
        ));

        bindOptionalText(optionView, buildOptionLine(request));
        bindOptionalText(profileView, buildProfileLine(request));
        bindOptionalText(priceView, buildPriceLine(request));

        if (TextUtils.isEmpty(request.getSpecialNotes())) {
            noteView.setVisibility(View.GONE);
        } else {
            noteView.setVisibility(View.VISIBLE);
            noteView.setText(context.getString(R.string.booking_request_note, request.getSpecialNotes()));
        }

        cardView.setStrokeColor(ContextCompat.getColor(context, resolveStatusStrokeColor(request.getStatus())));
        requestView.setOnClickListener(view -> actionListener.onOpenRequest(request));
        boolean editable = canEditRequest(request);
        boolean cancelable = canCancelRequest(request);
        if (editable || cancelable) {
            actionsView.setVisibility(View.VISIBLE);
            editButton.setVisibility(editable ? View.VISIBLE : View.GONE);
            cancelButton.setVisibility(cancelable ? View.VISIBLE : View.GONE);
            updateRequestActionMargins(editButton, cancelButton, editable, cancelable);
            editButton.setOnClickListener(editable ? view -> actionListener.onEditRequest(request) : null);
            cancelButton.setOnClickListener(cancelable ? view -> actionListener.onCancelRequest(request) : null);
        } else {
            actionsView.setVisibility(View.GONE);
        }

        return requestView;
    }

    private void bindOptionalText(TextView textView, String value) {
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(value);
    }

    private String buildOptionLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getTripTypeCode()) && TextUtils.isEmpty(request.getManagerGenderPreferenceCode())) {
            return "";
        }
        return context.getString(
                R.string.booking_request_option,
                formatter.toTripTypeLabel(request.getTripTypeCode()),
                formatter.toManagerGenderPreferenceLabel(request.getManagerGenderPreferenceCode())
        );
    }

    private String buildProfileLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getPatientConditionSummary())
                && TextUtils.isEmpty(request.getMedicationSummary())
                && TextUtils.isEmpty(request.getMobilitySupportCode())) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(request.getPatientConditionSummary())) {
            builder.append(context.getString(
                    R.string.booking_request_profile_condition,
                    request.getPatientConditionSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMedicationSummary())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_medication,
                    request.getMedicationSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMobilitySupportCode())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_mobility,
                    formatter.toMobilityLabel(request.getMobilitySupportCode())
            ));
        }
        return builder.toString();
    }

    private String buildPriceLine(AppointmentRequest request) {
        if (request.getFinalPrice() <= 0) {
            return "";
        }
        return context.getString(
                R.string.booking_request_price,
                formatter.formatPrice(request.getFinalPrice()),
                formatter.toPaymentMethodLabel(request.getPaymentMethodCode()),
                formatter.toCouponLabel(request.getCouponCode())
        );
    }

    private boolean canEditRequest(AppointmentRequest request) {
        return request.getStatus() == AppointmentStatus.REQUESTED;
    }

    private boolean canCancelRequest(AppointmentRequest request) {
        return request.getStatus() == AppointmentStatus.REQUESTED
                || request.getStatus() == AppointmentStatus.MATCHED;
    }

    private void updateRequestActionMargins(
            MaterialButton editButton,
            MaterialButton cancelButton,
            boolean editable,
            boolean cancelable
    ) {
        int splitMargin = dpToPx(8);
        updateActionButtonMargin(editButton, 0, editable && cancelable ? splitMargin : 0);
        updateActionButtonMargin(cancelButton, editable && cancelable ? splitMargin : 0, 0);
    }

    private void updateActionButtonMargin(MaterialButton button, int startMargin, int endMargin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
        params.setMarginStart(startMargin);
        params.setMarginEnd(endMargin);
        button.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
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

    private int resolveStatusStrokeColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_outline;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    public interface ActionListener {
        void onOpenRequest(AppointmentRequest request);

        void onEditRequest(AppointmentRequest request);

        void onCancelRequest(AppointmentRequest request);
    }
}
