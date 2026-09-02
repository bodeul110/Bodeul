package com.bodeul.core.appointment;

import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/{appointmentId}/payment")
@Profile({"database", "appointment-test"})
class AppointmentPaymentController {

    private final AppointmentPaymentService paymentService;

    AppointmentPaymentController(AppointmentPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    ResponseEntity<AppointmentPaymentService.BankTransferPaymentView> getPayment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId) {
        return noStore(paymentService.getPayment(appUser, appointmentId));
    }

    @PatchMapping("/depositor")
    ResponseEntity<AppointmentPaymentService.BankTransferPaymentView> setDepositor(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId,
            @RequestBody SetDepositorRequest request) {
        return noStore(paymentService.setDepositor(
                appUser,
                appointmentId,
                new AppointmentPaymentService.SetDepositorCommand(
                        request == null ? null : request.operationId(),
                        request == null || request.paymentVersion() == null
                                ? -1
                                : request.paymentVersion(),
                        request == null ? null : request.depositorName())));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record SetDepositorRequest(
            UUID operationId,
            Long paymentVersion,
            String depositorName) {
    }
}
