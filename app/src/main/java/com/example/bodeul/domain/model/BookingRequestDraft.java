package com.example.bodeul.domain.model;

import com.example.bodeul.util.UserProfileSanitizer;

/**
 * 예약 신청 폼에서 수집한 값을 객체 하나로 묶는다.
 */
public final class BookingRequestDraft {
    private final String linkedParticipantName;
    private final String linkedParticipantPhone;
    private final String linkedParticipantEmail;
    private final String patientConditionSummary;
    private final String medicationSummary;
    private final String hospitalName;
    private final String departmentName;
    private final double hospitalLatitude;
    private final double hospitalLongitude;
    private final String appointmentAt;
    private final String meetingPlace;
    private final String specialNotes;
    private final BookingMobilitySupport mobilitySupport;
    private final BookingTripType tripType;
    private final BookingManagerGenderPreference managerGenderPreference;
    private final BookingPaymentMethod paymentMethod;
    private final BookingCouponType couponType;
    private final BookingPriceSummary priceSummary;
    private final BookingPaymentApproval paymentApproval;

    private BookingRequestDraft(Builder builder) {
        linkedParticipantName = normalize(builder.linkedParticipantName);
        linkedParticipantPhone = UserProfileSanitizer.normalizePhone(builder.linkedParticipantPhone);
        linkedParticipantEmail = UserProfileSanitizer.normalizeEmail(builder.linkedParticipantEmail);
        patientConditionSummary = normalize(builder.patientConditionSummary);
        medicationSummary = normalize(builder.medicationSummary);
        hospitalName = normalize(builder.hospitalName);
        departmentName = normalize(builder.departmentName);
        hospitalLatitude = builder.hospitalLatitude;
        hospitalLongitude = builder.hospitalLongitude;
        appointmentAt = normalize(builder.appointmentAt);
        meetingPlace = normalize(builder.meetingPlace);
        specialNotes = normalize(builder.specialNotes);
        mobilitySupport = builder.mobilitySupport;
        tripType = builder.tripType;
        managerGenderPreference = builder.managerGenderPreference;
        paymentMethod = builder.paymentMethod;
        couponType = builder.couponType;
        priceSummary = builder.priceSummary;
        paymentApproval = builder.paymentApproval;
    }

    public String getLinkedParticipantName() {
        return linkedParticipantName;
    }

    public String getLinkedParticipantPhone() {
        return linkedParticipantPhone;
    }

    public String getLinkedParticipantEmail() {
        return linkedParticipantEmail;
    }

    public String getPatientConditionSummary() {
        return patientConditionSummary;
    }

    public String getMedicationSummary() {
        return medicationSummary;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public double getHospitalLatitude() {
        return hospitalLatitude;
    }

    public double getHospitalLongitude() {
        return hospitalLongitude;
    }

    public String getAppointmentAt() {
        return appointmentAt;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public BookingMobilitySupport getMobilitySupport() {
        return mobilitySupport;
    }

    public BookingTripType getTripType() {
        return tripType;
    }

    public BookingManagerGenderPreference getManagerGenderPreference() {
        return managerGenderPreference;
    }

    public BookingPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BookingCouponType getCouponType() {
        return couponType;
    }

    public BookingPriceSummary getPriceSummary() {
        return priceSummary;
    }

    public BookingPaymentApproval getPaymentApproval() {
        return paymentApproval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .linkedParticipantName(linkedParticipantName)
                .linkedParticipantPhone(linkedParticipantPhone)
                .linkedParticipantEmail(linkedParticipantEmail)
                .patientConditionSummary(patientConditionSummary)
                .medicationSummary(medicationSummary)
                .hospitalName(hospitalName)
                .departmentName(departmentName)
                .hospitalLatitude(hospitalLatitude)
                .hospitalLongitude(hospitalLongitude)
                .appointmentAt(appointmentAt)
                .meetingPlace(meetingPlace)
                .specialNotes(specialNotes)
                .mobilitySupport(mobilitySupport)
                .tripType(tripType)
                .managerGenderPreference(managerGenderPreference)
                .paymentMethod(paymentMethod)
                .couponType(couponType)
                .priceSummary(priceSummary)
                .paymentApproval(paymentApproval);
    }

    public BookingRequestDraft withPaymentApproval(BookingPaymentApproval paymentApproval) {
        return toBuilder()
                .paymentApproval(paymentApproval)
                .build();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String linkedParticipantName = "";
        private String linkedParticipantPhone = "";
        private String linkedParticipantEmail = "";
        private String patientConditionSummary = "";
        private String medicationSummary = "";
        private String hospitalName = "";
        private String departmentName = "";
        private double hospitalLatitude = 0.0;
        private double hospitalLongitude = 0.0;
        private String appointmentAt = "";
        private String meetingPlace = "";
        private String specialNotes = "";
        private BookingMobilitySupport mobilitySupport = BookingMobilitySupport.INDEPENDENT;
        private BookingTripType tripType = BookingTripType.ONE_WAY;
        private BookingManagerGenderPreference managerGenderPreference = BookingManagerGenderPreference.ANY;
        private BookingPaymentMethod paymentMethod = BookingPaymentMethod.CARD;
        private BookingCouponType couponType = BookingCouponType.NONE;
        private BookingPriceSummary priceSummary = BookingPriceSummary.empty();
        private BookingPaymentApproval paymentApproval = BookingPaymentApproval.empty();

        public Builder linkedParticipantName(String linkedParticipantName) {
            this.linkedParticipantName = linkedParticipantName;
            return this;
        }

        public Builder linkedParticipantPhone(String linkedParticipantPhone) {
            this.linkedParticipantPhone = linkedParticipantPhone;
            return this;
        }

        public Builder linkedParticipantEmail(String linkedParticipantEmail) {
            this.linkedParticipantEmail = linkedParticipantEmail;
            return this;
        }

        public Builder patientConditionSummary(String patientConditionSummary) {
            this.patientConditionSummary = patientConditionSummary;
            return this;
        }

        public Builder medicationSummary(String medicationSummary) {
            this.medicationSummary = medicationSummary;
            return this;
        }

        public Builder hospitalName(String hospitalName) {
            this.hospitalName = hospitalName;
            return this;
        }

        public Builder departmentName(String departmentName) {
            this.departmentName = departmentName;
            return this;
        }

        public Builder hospitalLatitude(double hospitalLatitude) {
            this.hospitalLatitude = hospitalLatitude;
            return this;
        }

        public Builder hospitalLongitude(double hospitalLongitude) {
            this.hospitalLongitude = hospitalLongitude;
            return this;
        }

        public Builder appointmentAt(String appointmentAt) {
            this.appointmentAt = appointmentAt;
            return this;
        }

        public Builder meetingPlace(String meetingPlace) {
            this.meetingPlace = meetingPlace;
            return this;
        }

        public Builder specialNotes(String specialNotes) {
            this.specialNotes = specialNotes;
            return this;
        }

        public Builder mobilitySupport(BookingMobilitySupport mobilitySupport) {
            this.mobilitySupport = mobilitySupport == null
                    ? BookingMobilitySupport.INDEPENDENT
                    : mobilitySupport;
            return this;
        }

        public Builder tripType(BookingTripType tripType) {
            this.tripType = tripType == null ? BookingTripType.ONE_WAY : tripType;
            return this;
        }

        public Builder managerGenderPreference(BookingManagerGenderPreference managerGenderPreference) {
            this.managerGenderPreference = managerGenderPreference == null
                    ? BookingManagerGenderPreference.ANY
                    : managerGenderPreference;
            return this;
        }

        public Builder paymentMethod(BookingPaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod == null ? BookingPaymentMethod.CARD : paymentMethod;
            return this;
        }

        public Builder couponType(BookingCouponType couponType) {
            this.couponType = couponType == null ? BookingCouponType.NONE : couponType;
            return this;
        }

        public Builder priceSummary(BookingPriceSummary priceSummary) {
            this.priceSummary = priceSummary == null ? BookingPriceSummary.empty() : priceSummary;
            return this;
        }

        public Builder paymentApproval(BookingPaymentApproval paymentApproval) {
            this.paymentApproval = paymentApproval == null
                    ? BookingPaymentApproval.empty()
                    : paymentApproval;
            return this;
        }

        public BookingRequestDraft build() {
            return new BookingRequestDraft(this);
        }
    }
}
