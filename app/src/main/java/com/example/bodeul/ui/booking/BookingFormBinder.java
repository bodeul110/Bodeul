package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingManagerGenderPreference;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.BookingTripType;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.UserProfileSanitizer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashMap;

/**
 * 예약 신청 폼 입력과 비용 요약 바인딩을 담당한다.
 */
public final class BookingFormBinder {
    private final Context context;
    private final BookingPresentationFormatter formatter;
    private final BookingPriceEstimator priceEstimator;
    private final BookingAppointmentSelector appointmentSelector;
    private final TextView textFormTitle;
    private final TextView textFormBadge;
    private final TextView textFormHelper;
    private final TextView textLinkedSection;
    private final TextView textLinkedHelper;
    private final TextView textEstimateBase;
    private final TextView textEstimateOption;
    private final TextView textEstimateDiscount;
    private final TextView textEstimateFinal;
    private final TextView textPaymentHelper;
    private final TextInputLayout layoutHealthSummary;
    private final TextInputLayout layoutMedicationSummary;
    private final TextInputLayout layoutLinkedName;
    private final TextInputLayout layoutLinkedPhone;
    private final TextInputLayout layoutLinkedEmail;
    private final TextInputLayout layoutHospitalName;
    private final TextInputLayout layoutDepartmentName;
    private final TextInputLayout layoutMeetingPlace;
    private final TextInputLayout layoutSpecialNotes;
    private final TextInputEditText inputHealthSummary;
    private final TextInputEditText inputMedicationSummary;
    private final TextInputEditText inputLinkedName;
    private final TextInputEditText inputLinkedPhone;
    private final TextInputEditText inputLinkedEmail;
    private final TextInputEditText inputHospitalName;
    private final TextInputEditText inputDepartmentName;
    private final TextInputEditText inputMeetingPlace;
    private final TextInputEditText inputSpecialNotes;
    private final MaterialButton buttonSelectHospital;
    private final MaterialButton buttonSelectMeetingPlace;
    private final MaterialButton buttonSubmitBooking;
    private final MaterialButton buttonCancelBookingEdit;
    private final BookingOptionGroupBinder<BookingMobilitySupport> mobilityGroupBinder;
    private final BookingOptionGroupBinder<BookingTripType> tripTypeGroupBinder;
    private final BookingOptionGroupBinder<BookingManagerGenderPreference> managerGenderGroupBinder;
    private BookingOptionGroupBinder<BookingPaymentMethod> paymentMethodGroupBinder;
    private BookingOptionGroupBinder<BookingCouponType> couponTypeGroupBinder;

    private String selectedMeetingPointId = "";
    private double selectedHospitalLatitude = 0.0;
    private double selectedHospitalLongitude = 0.0;

    public BookingFormBinder(
            Context context,
            BookingPresentationFormatter formatter,
            BookingPriceEstimator priceEstimator,
            BookingAppointmentSelector appointmentSelector,
            TextView textFormTitle,
            TextView textFormBadge,
            TextView textFormHelper,
            TextView textLinkedSection,
            TextView textLinkedHelper,
            TextView textEstimateBase,
            TextView textEstimateOption,
            TextView textEstimateDiscount,
            TextView textEstimateFinal,
            TextView textPaymentHelper,
            TextInputLayout layoutHealthSummary,
            TextInputLayout layoutMedicationSummary,
            TextInputLayout layoutLinkedName,
            TextInputLayout layoutLinkedPhone,
            TextInputLayout layoutLinkedEmail,
            TextInputLayout layoutHospitalName,
            TextInputLayout layoutDepartmentName,
            TextInputLayout layoutMeetingPlace,
            TextInputLayout layoutSpecialNotes,
            TextInputEditText inputHealthSummary,
            TextInputEditText inputMedicationSummary,
            TextInputEditText inputLinkedName,
            TextInputEditText inputLinkedPhone,
            TextInputEditText inputLinkedEmail,
            TextInputEditText inputHospitalName,
            TextInputEditText inputDepartmentName,
            TextInputEditText inputMeetingPlace,
            TextInputEditText inputSpecialNotes,
            MaterialButton buttonSelectHospital,
            MaterialButton buttonSelectMeetingPlace,
            MaterialButton buttonSubmitBooking,
            MaterialButton buttonCancelBookingEdit,
            MaterialButton buttonMobilityIndependent,
            MaterialButton buttonMobilityWalkingAid,
            MaterialButton buttonMobilityWheelchair,
            MaterialButton buttonTripOneWay,
            MaterialButton buttonTripRoundTrip,
            MaterialButton buttonManagerGenderAny,
            MaterialButton buttonManagerGenderFemale,
            MaterialButton buttonManagerGenderMale,
            MaterialButton buttonPaymentCard,
            MaterialButton buttonPaymentEasyPay,
            MaterialButton buttonPaymentOnSite,
            MaterialButton buttonCouponNone,
            MaterialButton buttonCouponFirstVisit,
            MaterialButton buttonCouponFamily
    ) {
        this.context = context;
        this.formatter = formatter;
        this.priceEstimator = priceEstimator;
        this.appointmentSelector = appointmentSelector;
        this.textFormTitle = textFormTitle;
        this.textFormBadge = textFormBadge;
        this.textFormHelper = textFormHelper;
        this.textLinkedSection = textLinkedSection;
        this.textLinkedHelper = textLinkedHelper;
        this.textEstimateBase = textEstimateBase;
        this.textEstimateOption = textEstimateOption;
        this.textEstimateDiscount = textEstimateDiscount;
        this.textEstimateFinal = textEstimateFinal;
        this.textPaymentHelper = textPaymentHelper;
        this.layoutHealthSummary = layoutHealthSummary;
        this.layoutMedicationSummary = layoutMedicationSummary;
        this.layoutLinkedName = layoutLinkedName;
        this.layoutLinkedPhone = layoutLinkedPhone;
        this.layoutLinkedEmail = layoutLinkedEmail;
        this.layoutHospitalName = layoutHospitalName;
        this.layoutDepartmentName = layoutDepartmentName;
        this.layoutMeetingPlace = layoutMeetingPlace;
        this.layoutSpecialNotes = layoutSpecialNotes;
        this.inputHealthSummary = inputHealthSummary;
        this.inputMedicationSummary = inputMedicationSummary;
        this.inputLinkedName = inputLinkedName;
        this.inputLinkedPhone = inputLinkedPhone;
        this.inputLinkedEmail = inputLinkedEmail;
        this.inputHospitalName = inputHospitalName;
        this.inputDepartmentName = inputDepartmentName;
        this.inputMeetingPlace = inputMeetingPlace;
        this.inputSpecialNotes = inputSpecialNotes;
        this.buttonSelectHospital = buttonSelectHospital;
        this.buttonSelectMeetingPlace = buttonSelectMeetingPlace;
        this.buttonSubmitBooking = buttonSubmitBooking;
        this.buttonCancelBookingEdit = buttonCancelBookingEdit;

        mobilityGroupBinder = new BookingOptionGroupBinder<>(
                context,
                buildMobilityButtons(buttonMobilityIndependent, buttonMobilityWalkingAid, buttonMobilityWheelchair),
                BookingMobilitySupport.INDEPENDENT
        );
        tripTypeGroupBinder = new BookingOptionGroupBinder<>(
                context,
                buildTripTypeButtons(buttonTripOneWay, buttonTripRoundTrip),
                BookingTripType.ONE_WAY
        );
        managerGenderGroupBinder = new BookingOptionGroupBinder<>(
                context,
                buildManagerButtons(buttonManagerGenderAny, buttonManagerGenderFemale, buttonManagerGenderMale),
                BookingManagerGenderPreference.ANY
        );
        paymentMethodGroupBinder = new BookingOptionGroupBinder<>(
                context,
                buildPaymentButtons(buttonPaymentCard, buttonPaymentEasyPay, buttonPaymentOnSite),
                BookingPaymentMethod.CARD
        );
        couponTypeGroupBinder = new BookingOptionGroupBinder<>(
                context,
                buildCouponButtons(buttonCouponNone, buttonCouponFirstVisit, buttonCouponFamily),
                BookingCouponType.NONE
        );

        Runnable refreshEstimateAction = this::refreshEstimate;
        mobilityGroupBinder.setSelectionChangedListener(refreshEstimateAction);
        tripTypeGroupBinder.setSelectionChangedListener(refreshEstimateAction);
        managerGenderGroupBinder.setSelectionChangedListener(refreshEstimateAction);
        paymentMethodGroupBinder.setSelectionChangedListener(refreshEstimateAction);
        couponTypeGroupBinder.setSelectionChangedListener(refreshEstimateAction);
        refreshEstimate();
    }

    public void bindCreateMode(User currentUser) {
        bindLinkedParticipantSection(currentUser);
        textFormTitle.setText(R.string.booking_form_section);
        textFormBadge.setText(R.string.booking_form_badge);
        textFormHelper.setText(R.string.booking_form_helper);
        buttonSubmitBooking.setText(R.string.booking_submit_button);
        buttonCancelBookingEdit.setVisibility(View.GONE);
        clearFormFields();
    }

    public void bindEditMode(User currentUser, AppointmentRequest request) {
        bindLinkedParticipantSection(currentUser);
        textFormTitle.setText(R.string.booking_form_edit_section);
        textFormBadge.setText(R.string.booking_form_edit_badge);
        textFormHelper.setText(context.getString(
                R.string.booking_form_edit_helper,
                request.getHospitalName(),
                request.getDepartmentName()
        ));
        buttonSubmitBooking.setText(R.string.booking_submit_update_button);
        buttonCancelBookingEdit.setVisibility(View.VISIBLE);

        inputHealthSummary.setText(request.getPatientConditionSummary());
        inputMedicationSummary.setText(request.getMedicationSummary());
        inputLinkedName.setText(resolveLinkedName(currentUser, request));
        inputLinkedPhone.setText(resolveLinkedPhone(currentUser, request));
        inputLinkedEmail.setText(resolveLinkedEmail(currentUser, request));
        applyHospitalSelection(
                new BookingHospitalSelection(
                        request.getHospitalName(),
                        request.getDepartmentName(),
                        request.getHospitalLatitude(),
                        request.getHospitalLongitude()
                ),
                false
        );
        applyMeetingLocationSelection(new BookingMeetingLocationSelection("", request.getMeetingPlace()));
        inputSpecialNotes.setText(request.getSpecialNotes());
        appointmentSelector.setAppointmentAt(request.getAppointmentAt());

        mobilityGroupBinder.setSelection(BookingMobilitySupport.fromValue(request.getMobilitySupportCode()));
        tripTypeGroupBinder.setSelection(BookingTripType.fromValue(request.getTripTypeCode()));
        managerGenderGroupBinder.setSelection(BookingManagerGenderPreference.fromValue(
                request.getManagerGenderPreferenceCode()
        ));
        paymentMethodGroupBinder.setSelection(BookingPaymentMethod.fromValue(request.getPaymentMethodCode()));
        couponTypeGroupBinder.setSelection(BookingCouponType.fromValue(request.getCouponCode()));
        refreshEstimate();
        clearErrors();
    }

    public BookingRequestDraft buildDraft() {
        clearErrors();

        String healthSummary = valueOf(inputHealthSummary);
        String linkedName = valueOf(inputLinkedName);
        String linkedPhone = valueOf(inputLinkedPhone);
        String linkedEmail = valueOf(inputLinkedEmail);
        BookingHospitalSelection hospitalSelection = getHospitalSelection();
        String hospitalName = hospitalSelection.getHospitalName();
        String departmentName = hospitalSelection.getDepartmentName();
        String meetingPlace = valueOf(inputMeetingPlace);

        boolean isValid = true;
        isValid &= validateRequired(layoutHealthSummary, healthSummary);
        isValid &= validateRequired(layoutLinkedName, linkedName);
        isValid &= validateRequired(layoutLinkedPhone, linkedPhone);
        isValid &= validateRequired(layoutHospitalName, hospitalName);
        isValid &= validateRequired(layoutDepartmentName, departmentName);
        isValid &= appointmentSelector.validateRequiredAndFormat();
        isValid &= validateRequired(layoutMeetingPlace, meetingPlace);
        isValid &= validateLinkedPhone(linkedPhone);
        isValid &= validateLinkedEmail(linkedEmail);

        if (!isValid) {
            return null;
        }

        BookingPriceSummary priceSummary = priceEstimator.estimate(
                tripTypeGroupBinder.getSelection(),
                mobilityGroupBinder.getSelection(),
                couponTypeGroupBinder.getSelection()
        );

        return BookingRequestDraft.builder()
                .patientConditionSummary(healthSummary)
                .medicationSummary(valueOf(inputMedicationSummary))
                .linkedParticipantName(linkedName)
                .linkedParticipantPhone(linkedPhone)
                .linkedParticipantEmail(linkedEmail)
                .hospitalName(hospitalName)
                .departmentName(departmentName)
                .hospitalLatitude(selectedHospitalLatitude)
                .hospitalLongitude(selectedHospitalLongitude)
                .appointmentAt(appointmentSelector.getAppointmentAt())
                .meetingPlace(meetingPlace)
                .specialNotes(valueOf(inputSpecialNotes))
                .mobilitySupport(mobilityGroupBinder.getSelection())
                .tripType(tripTypeGroupBinder.getSelection())
                .managerGenderPreference(managerGenderGroupBinder.getSelection())
                .paymentMethod(paymentMethodGroupBinder.getSelection())
                .couponType(couponTypeGroupBinder.getSelection())
                .priceSummary(priceSummary)
                .build();
    }

    public void setOnHospitalSelectorClickListener(View.OnClickListener listener) {
        buttonSelectHospital.setOnClickListener(listener);
    }

    public void setOnMeetingPlaceSelectorClickListener(View.OnClickListener listener) {
        buttonSelectMeetingPlace.setOnClickListener(listener);
    }

    public BookingHospitalSelection getHospitalSelection() {
        return new BookingHospitalSelection(
                valueOf(inputHospitalName),
                valueOf(inputDepartmentName),
                selectedHospitalLatitude,
                selectedHospitalLongitude
        );
    }

    public void applyHospitalSelection(BookingHospitalSelection selection) {
        applyHospitalSelection(selection, true);
    }

    public BookingMeetingLocationSelection getMeetingLocationSelection() {
        return new BookingMeetingLocationSelection(
                selectedMeetingPointId,
                valueOf(inputMeetingPlace)
        );
    }

    public void applyMeetingLocationSelection(BookingMeetingLocationSelection selection) {
        selectedMeetingPointId = selection.getPointId();
        inputMeetingPlace.setText(selection.getMeetingPlace());
        layoutMeetingPlace.setError(null);
    }

    public void setLoading(boolean loading) {
        appointmentSelector.setEnabled(!loading);
        inputHealthSummary.setEnabled(!loading);
        inputMedicationSummary.setEnabled(!loading);
        inputLinkedName.setEnabled(!loading);
        inputLinkedPhone.setEnabled(!loading);
        inputLinkedEmail.setEnabled(!loading);
        inputMeetingPlace.setEnabled(!loading);
        inputSpecialNotes.setEnabled(!loading);
        buttonSelectHospital.setEnabled(!loading);
        buttonSelectMeetingPlace.setEnabled(!loading);
        mobilityGroupBinder.setEnabled(!loading);
        tripTypeGroupBinder.setEnabled(!loading);
        managerGenderGroupBinder.setEnabled(!loading);
        paymentMethodGroupBinder.setEnabled(!loading);
        couponTypeGroupBinder.setEnabled(!loading);
        buttonSubmitBooking.setEnabled(!loading);
        buttonCancelBookingEdit.setEnabled(!loading);
    }

    private void bindLinkedParticipantSection(User user) {
        String counterpartRole = formatter.toCounterpartRoleLabel(user.getRole());
        textLinkedSection.setText(context.getString(R.string.booking_linked_section_format, counterpartRole));
        textLinkedHelper.setText(context.getString(R.string.booking_linked_helper_format, counterpartRole));
        layoutLinkedName.setHint(context.getString(R.string.booking_linked_name_hint_format, counterpartRole));
        layoutLinkedPhone.setHint(context.getString(R.string.booking_linked_phone_hint_format, counterpartRole));
        layoutLinkedEmail.setHint(context.getString(R.string.booking_linked_email_hint_format, counterpartRole));
    }

    private void refreshEstimate() {
        BookingPriceSummary summary = priceEstimator.estimate(
                tripTypeGroupBinder.getSelection(),
                mobilityGroupBinder.getSelection(),
                couponTypeGroupBinder.getSelection()
        );

        textEstimateBase.setText(context.getString(
                R.string.booking_price_base_line,
                formatter.formatPrice(summary.getBasePrice())
        ));
        textEstimateOption.setText(context.getString(
                R.string.booking_price_option_line,
                formatter.toTripTypeLabel(tripTypeGroupBinder.getSelection().name()),
                formatter.toMobilityLabel(mobilityGroupBinder.getSelection().name()),
                formatter.formatPrice(summary.getOptionSurchargePrice())
        ));
        textEstimateDiscount.setText(context.getString(
                R.string.booking_price_discount_line,
                formatter.toCouponLabel(couponTypeGroupBinder.getSelection().name()),
                formatter.formatPrice(summary.getCouponDiscountPrice())
        ));
        textEstimateFinal.setText(context.getString(
                R.string.booking_price_final_line,
                formatter.formatPrice(summary.getFinalPrice())
        ));
        textPaymentHelper.setText(context.getString(
                R.string.booking_payment_helper_format,
                formatter.toPaymentMethodLabel(paymentMethodGroupBinder.getSelection().name()),
                formatter.toManagerGenderPreferenceLabel(managerGenderGroupBinder.getSelection().name())
        ));
    }

    private void clearFormFields() {
        inputHealthSummary.setText(null);
        inputMedicationSummary.setText(null);
        inputLinkedName.setText(null);
        inputLinkedPhone.setText(null);
        inputLinkedEmail.setText(null);
        inputHospitalName.setText(null);
        inputDepartmentName.setText(null);
        selectedHospitalLatitude = 0.0;
        selectedHospitalLongitude = 0.0;
        inputMeetingPlace.setText(null);
        selectedMeetingPointId = "";
        inputSpecialNotes.setText(null);
        appointmentSelector.clear();
        mobilityGroupBinder.setSelection(BookingMobilitySupport.INDEPENDENT);
        tripTypeGroupBinder.setSelection(BookingTripType.ONE_WAY);
        managerGenderGroupBinder.setSelection(BookingManagerGenderPreference.ANY);
        paymentMethodGroupBinder.setSelection(BookingPaymentMethod.CARD);
        couponTypeGroupBinder.setSelection(BookingCouponType.NONE);
        clearErrors();
        refreshEstimate();
    }

    private void applyHospitalSelection(BookingHospitalSelection selection, boolean suggestMeetingPlace) {
        boolean hospitalChanged = !TextUtils.equals(valueOf(inputHospitalName), selection.getHospitalName())
                || !TextUtils.equals(valueOf(inputDepartmentName), selection.getDepartmentName());
        inputHospitalName.setText(selection.getHospitalName());
        inputDepartmentName.setText(selection.getDepartmentName());
        selectedHospitalLatitude = selection.getHospitalLatitude();
        selectedHospitalLongitude = selection.getHospitalLongitude();
        if (suggestMeetingPlace && hospitalChanged) {
            selectedMeetingPointId = "";
            inputMeetingPlace.setText(null);
        }
        layoutHospitalName.setError(null);
        layoutDepartmentName.setError(null);
    }

    private boolean validateRequired(TextInputLayout layout, String value) {
        if (TextUtils.isEmpty(value)) {
            layout.setError(context.getString(R.string.error_required_field));
            return false;
        }
        return true;
    }

    private boolean validateLinkedPhone(String phone) {
        if (UserProfileSanitizer.isValidPhone(phone)) {
            return true;
        }
        layoutLinkedPhone.setError(context.getString(R.string.error_phone_invalid));
        return false;
    }

    private boolean validateLinkedEmail(String email) {
        if (TextUtils.isEmpty(email) || Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return true;
        }
        layoutLinkedEmail.setError(context.getString(R.string.error_email_invalid));
        return false;
    }

    private void clearErrors() {
        layoutHealthSummary.setError(null);
        layoutMedicationSummary.setError(null);
        layoutLinkedName.setError(null);
        layoutLinkedPhone.setError(null);
        layoutLinkedEmail.setError(null);
        layoutHospitalName.setError(null);
        layoutDepartmentName.setError(null);
        layoutMeetingPlace.setError(null);
        layoutSpecialNotes.setError(null);
    }

    private String resolveLinkedName(User user, AppointmentRequest request) {
        return user.getRole() == UserRole.GUARDIAN
                ? request.getPatientName()
                : request.getGuardianName();
    }

    private String resolveLinkedPhone(User user, AppointmentRequest request) {
        return user.getRole() == UserRole.GUARDIAN
                ? request.getPatientPhone()
                : request.getGuardianPhone();
    }

    private String resolveLinkedEmail(User user, AppointmentRequest request) {
        return user.getRole() == UserRole.GUARDIAN
                ? request.getPatientEmail()
                : request.getGuardianEmail();
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private LinkedHashMap<BookingMobilitySupport, MaterialButton> buildMobilityButtons(
            MaterialButton independentButton,
            MaterialButton walkingAidButton,
            MaterialButton wheelchairButton
    ) {
        LinkedHashMap<BookingMobilitySupport, MaterialButton> optionButtons = new LinkedHashMap<>();
        optionButtons.put(BookingMobilitySupport.INDEPENDENT, independentButton);
        optionButtons.put(BookingMobilitySupport.WALKING_AID, walkingAidButton);
        optionButtons.put(BookingMobilitySupport.WHEELCHAIR, wheelchairButton);
        return optionButtons;
    }

    private LinkedHashMap<BookingTripType, MaterialButton> buildTripTypeButtons(
            MaterialButton oneWayButton,
            MaterialButton roundTripButton
    ) {
        LinkedHashMap<BookingTripType, MaterialButton> optionButtons = new LinkedHashMap<>();
        optionButtons.put(BookingTripType.ONE_WAY, oneWayButton);
        optionButtons.put(BookingTripType.ROUND_TRIP, roundTripButton);
        return optionButtons;
    }

    private LinkedHashMap<BookingManagerGenderPreference, MaterialButton> buildManagerButtons(
            MaterialButton anyButton,
            MaterialButton femaleButton,
            MaterialButton maleButton
    ) {
        LinkedHashMap<BookingManagerGenderPreference, MaterialButton> optionButtons = new LinkedHashMap<>();
        optionButtons.put(BookingManagerGenderPreference.ANY, anyButton);
        optionButtons.put(BookingManagerGenderPreference.FEMALE, femaleButton);
        optionButtons.put(BookingManagerGenderPreference.MALE, maleButton);
        return optionButtons;
    }

    private LinkedHashMap<BookingPaymentMethod, MaterialButton> buildPaymentButtons(
            MaterialButton cardButton,
            MaterialButton easyPayButton,
            MaterialButton onSiteButton
    ) {
        LinkedHashMap<BookingPaymentMethod, MaterialButton> optionButtons = new LinkedHashMap<>();
        optionButtons.put(BookingPaymentMethod.CARD, cardButton);
        optionButtons.put(BookingPaymentMethod.EASY_PAY, easyPayButton);
        optionButtons.put(BookingPaymentMethod.ON_SITE, onSiteButton);
        return optionButtons;
    }

    private LinkedHashMap<BookingCouponType, MaterialButton> buildCouponButtons(
            MaterialButton noneButton,
            MaterialButton firstVisitButton,
            MaterialButton familyButton
    ) {
        LinkedHashMap<BookingCouponType, MaterialButton> optionButtons = new LinkedHashMap<>();
        optionButtons.put(BookingCouponType.NONE, noneButton);
        optionButtons.put(BookingCouponType.FIRST_VISIT, firstVisitButton);
        optionButtons.put(BookingCouponType.FAMILY, familyButton);
        return optionButtons;
    }
}
