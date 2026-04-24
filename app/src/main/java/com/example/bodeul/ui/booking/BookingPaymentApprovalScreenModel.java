package com.example.bodeul.ui.booking;

/**
 * 결제 승인 화면의 표시 문구와 버튼 상태를 담는다.
 */
public final class BookingPaymentApprovalScreenModel {
    private final String badge;
    private final String title;
    private final String body;
    private final String approvalButtonText;
    private final String paymentMethod;
    private final String coupon;
    private final String amount;
    private final String schedule;
    private final String hospital;
    private final String meetingPlace;
    private final String consentText;
    private final boolean deferredPayment;
    private final String providerLabel;

    public BookingPaymentApprovalScreenModel(
            String badge,
            String title,
            String body,
            String approvalButtonText,
            String paymentMethod,
            String coupon,
            String amount,
            String schedule,
            String hospital,
            String meetingPlace,
            String consentText,
            boolean deferredPayment,
            String providerLabel
    ) {
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.approvalButtonText = approvalButtonText;
        this.paymentMethod = paymentMethod;
        this.coupon = coupon;
        this.amount = amount;
        this.schedule = schedule;
        this.hospital = hospital;
        this.meetingPlace = meetingPlace;
        this.consentText = consentText;
        this.deferredPayment = deferredPayment;
        this.providerLabel = providerLabel;
    }

    public String getBadge() {
        return badge;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getApprovalButtonText() {
        return approvalButtonText;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getCoupon() {
        return coupon;
    }

    public String getAmount() {
        return amount;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getHospital() {
        return hospital;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getConsentText() {
        return consentText;
    }

    public boolean isDeferredPayment() {
        return deferredPayment;
    }

    public String getProviderLabel() {
        return providerLabel;
    }
}
