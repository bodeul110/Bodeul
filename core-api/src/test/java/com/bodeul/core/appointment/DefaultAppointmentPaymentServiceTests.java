package com.bodeul.core.appointment;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentPaymentRepository.BankTransferPaymentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.ParticipantSnapshot;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAppointmentPaymentServiceTests {

    private static final UUID PATIENT_ID = UUID.fromString("2d686cdc-4c05-4dfd-bbe8-889f8f036aed");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("9dd1f48d-70b6-4281-b97a-3faf9b76c152");
    private static final UUID APPOINTMENT_ID = UUID.fromString("ae83eef9-d063-447f-b7c9-fd33a1708624");
    private static final UUID OPERATION_ID = UUID.fromString("3a192ceb-d8d2-4881-8e0d-8d97096a7b0d");

    private AppointmentRepository appointmentRepository;
    private AppointmentPaymentRepository paymentRepository;
    private DefaultAppointmentPaymentService service;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        paymentRepository = mock(AppointmentPaymentRepository.class);
        service = new DefaultAppointmentPaymentService(appointmentRepository, paymentRepository);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));
    }

    @Test
    void patientReadsPaymentWithoutAccountInstruction() {
        when(paymentRepository.findForPatient(APPOINTMENT_ID, PATIENT_ID))
                .thenReturn(Optional.of(payment("AWAITING_DEPOSIT", 0)));

        var view = service.getPayment(patient(PATIENT_ID), APPOINTMENT_ID);

        assertThat(view.expectedAmount()).isEqualTo(96_000);
        assertThat(view.paymentStatusCode()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(view.instructionAvailable()).isFalse();
        assertThat(view.paymentDueAt()).isEmpty();
    }

    @Test
    void patientSubmitsNormalizedDepositorNameWithOptimisticVersion() {
        when(paymentRepository.setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                0,
                "홍 길동"))
                .thenReturn(Optional.of(payment("AWAITING_DEPOSIT", 1)));

        var view = service.setDepositor(
                patient(PATIENT_ID),
                APPOINTMENT_ID,
                new AppointmentPaymentService.SetDepositorCommand(
                        OPERATION_ID,
                        0,
                        "  홍   길동  "));

        assertThat(view.paymentVersion()).isEqualTo(1);
        verify(paymentRepository).setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                0,
                "홍 길동");
    }

    @Test
    void anotherPatientCannotReadOrWritePayment() {
        assertThatThrownBy(() -> service.getPayment(patient(OTHER_PATIENT_ID), APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("이 예약을 조회하거나 변경할 권한이 없습니다.");

        verify(paymentRepository, never()).findForPatient(any(), any());
    }

    @Test
    void stalePaymentVersionIsTranslatedFromDatabaseConflict() {
        when(paymentRepository.setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                1,
                "홍길동"))
                .thenThrow(new DataIntegrityViolationException(
                        "database conflict",
                        new SQLException("sensitive database message", "40001")));

        assertThatThrownBy(() -> service.setDepositor(
                patient(PATIENT_ID),
                APPOINTMENT_ID,
                new AppointmentPaymentService.SetDepositorCommand(OPERATION_ID, 1, "홍길동")))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("다른 변경이 먼저 반영되었습니다. 최신 예약 정보를 다시 확인해 주세요.");

        verify(paymentRepository).setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                1,
                "홍길동");
    }

    @Test
    void reusedOperationIdWithDifferentPayloadIsAConflict() {
        when(paymentRepository.setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                0,
                "다른 이름"))
                .thenThrow(new DataIntegrityViolationException(
                        "database conflict",
                        new SQLException("sensitive database message", "P0003")));

        assertThatThrownBy(() -> service.setDepositor(
                patient(PATIENT_ID),
                APPOINTMENT_ID,
                new AppointmentPaymentService.SetDepositorCommand(OPERATION_ID, 0, "다른 이름")))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("같은 결제 작업 ID를 다른 내용으로 다시 사용할 수 없습니다.");
    }

    @Test
    void paymentReadDatabaseNotFoundIsSafelyTranslated() {
        when(paymentRepository.findForPatient(APPOINTMENT_ID, PATIENT_ID))
                .thenThrow(new DataIntegrityViolationException(
                        "database failure",
                        new SQLException("sensitive database message", "P0002")));

        assertThatThrownBy(() -> service.getPayment(patient(PATIENT_ID), APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("예약 정보를 찾을 수 없습니다.");
    }

    @Test
    void sameOperationRetryReachesDatabaseWithOriginalVersion() {
        when(paymentRepository.setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                0,
                "홍길동"))
                .thenReturn(Optional.of(payment("AWAITING_DEPOSIT", 1)));

        var retried = service.setDepositor(
                patient(PATIENT_ID),
                APPOINTMENT_ID,
                new AppointmentPaymentService.SetDepositorCommand(OPERATION_ID, 0, "홍길동"));

        assertThat(retried.paymentVersion()).isEqualTo(1);
        verify(paymentRepository).setDepositor(
                APPOINTMENT_ID,
                PATIENT_ID,
                OPERATION_ID,
                0,
                "홍길동");
    }

    private AppUserRepository.AppUser patient(UUID id) {
        return new AppUserRepository.AppUser(id, "firebase-" + id, AppUserRole.PATIENT);
    }

    private BankTransferPaymentRecord payment(String status, long version) {
        return new BankTransferPaymentRecord(
                APPOINTMENT_ID,
                "BANK_TRANSFER",
                status,
                96_000,
                "",
                null,
                null,
                null,
                null,
                null,
                version);
    }

    private AppointmentRecord appointment() {
        ParticipantSnapshot patient = new ParticipantSnapshot("환자", "010-1111-2222", "patient@example.com");
        ParticipantSnapshot guardian = new ParticipantSnapshot("", "", "");
        return new AppointmentRecord(
                APPOINTMENT_ID,
                null,
                "BD-BANK01",
                PATIENT_ID,
                null,
                null,
                PATIENT_ID,
                AppUserRole.PATIENT,
                patient,
                guardian,
                "서울병원",
                "내과",
                37.5,
                127.0,
                Instant.parse("2026-12-20T01:30:00Z"),
                "본관",
                "",
                "",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                "REQUESTED",
                69_000,
                27_000,
                0,
                96_000,
                "BANK_TRANSFER",
                "NONE",
                "AWAITING_DEPOSIT",
                "",
                null,
                "",
                0);
    }
}
