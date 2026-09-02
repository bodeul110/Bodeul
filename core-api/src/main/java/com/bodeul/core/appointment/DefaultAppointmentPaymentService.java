package com.bodeul.core.appointment;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentPaymentRepository.BankTransferPaymentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultAppointmentPaymentService implements AppointmentPaymentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentPaymentRepository paymentRepository;

    DefaultAppointmentPaymentService(
            AppointmentRepository appointmentRepository,
            AppointmentPaymentRepository paymentRepository) {
        this.appointmentRepository = appointmentRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BankTransferPaymentView getPayment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        requirePatientOwner(appUser, appointmentId);
        try {
            return paymentRepository.findForPatient(appointmentId, appUser.id())
                    .map(this::toView)
                    .orElseThrow(AppointmentException::notFound);
        } catch (DataAccessException exception) {
            throw translateDatabaseFailure(exception);
        }
    }

    @Override
    @Transactional
    public BankTransferPaymentView setDepositor(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            SetDepositorCommand command) {
        requirePatientOwner(appUser, appointmentId);
        if (command == null || command.operationId() == null) {
            throw AppointmentException.invalidRequest("입금자명 변경 작업 ID가 필요합니다.");
        }
        if (command.paymentVersion() < 0) {
            throw AppointmentException.invalidRequest("결제 버전이 필요합니다.");
        }
        String depositorName = command.depositorName() == null
                ? ""
                : command.depositorName().trim().replaceAll("\\s+", " ");
        if (depositorName.isEmpty() || depositorName.length() > 100) {
            throw AppointmentException.invalidRequest("입금자명은 1자부터 100자까지 입력해 주세요.");
        }

        try {
            return paymentRepository.setDepositor(
                            appointmentId,
                            appUser.id(),
                            command.operationId(),
                            command.paymentVersion(),
                            depositorName)
                    .map(this::toView)
                    .orElseThrow(AppointmentException::versionConflict);
        } catch (DataAccessException exception) {
            throw translateDatabaseFailure(exception);
        }
    }

    private AppointmentRecord requirePatientOwner(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        if (appUser == null || appUser.role() != AppUserRole.PATIENT) {
            throw AppointmentException.permissionDenied();
        }
        AppointmentRecord appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(AppointmentException::notFound);
        if (!appUser.id().equals(appointment.patientUserId())) {
            throw AppointmentException.permissionDenied();
        }
        if (!"BANK_TRANSFER".equals(appointment.paymentMethodCode())) {
            throw AppointmentException.notFound();
        }
        return appointment;
    }

    private BankTransferPaymentView toView(BankTransferPaymentRecord payment) {
        return new BankTransferPaymentView(
                payment.appointmentId(),
                payment.paymentMethodCode(),
                payment.paymentStatusCode(),
                payment.expectedAmount(),
                payment.depositorName(),
                toText(payment.paymentDueAt()),
                payment.receivedAmount(),
                toText(payment.confirmedAt()),
                toText(payment.refundRequestedAt()),
                toText(payment.refundedAt()),
                payment.paymentVersion(),
                false);
    }

    private String toText(Instant value) {
        return value == null ? "" : value.toString();
    }

    private RuntimeException translateDatabaseFailure(DataAccessException exception) {
        String sqlState = findSqlState(exception);
        if ("42501".equals(sqlState)) {
            return AppointmentException.permissionDenied();
        }
        if ("P0002".equals(sqlState)) {
            return AppointmentException.notFound();
        }
        if ("40001".equals(sqlState)) {
            return AppointmentException.versionConflict();
        }
        if ("P0001".equals(sqlState)) {
            return AppointmentException.stateConflict();
        }
        if ("P0003".equals(sqlState)) {
            return AppointmentException.paymentOperationConflict();
        }
        if ("22023".equals(sqlState)) {
            return AppointmentException.invalidRequest("입금자명 변경 요청을 확인해 주세요.");
        }
        return exception;
    }

    private String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
