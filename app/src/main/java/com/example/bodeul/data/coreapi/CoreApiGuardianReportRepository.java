package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 보호자 화면의 예약·세션·리포트를 Core API 원본만으로 조합한다.
 */
public final class CoreApiGuardianReportRepository implements GuardianReportRepository {
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiCompanionSessionClient sessionClient;

    public CoreApiGuardianReportRepository(Context context) {
        this.appointmentClient = new CoreApiAppointmentClient(context);
        this.sessionClient = new CoreApiCompanionSessionClient(context);
    }

    @Override
    public void getGuardianDashboard(
            User currentUser,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.GUARDIAN) {
            callback.onError("보호자 계정으로 로그인해주세요.");
            return;
        }
        appointmentClient.getAppointments(new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> appointments) {
                sessionClient.getSessions(
                        new RepositoryCallback<List<CoreApiCompanionSessionClient.SessionSnapshot>>() {
                            @Override
                            public void onSuccess(
                                    List<CoreApiCompanionSessionClient.SessionSnapshot> sessions
                            ) {
                                loadEntries(
                                        currentUser,
                                        appointments,
                                        sessions,
                                        0,
                                        new ArrayList<>(),
                                        callback);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void loadEntries(
            User guardian,
            List<AppointmentRequest> appointments,
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions,
            int index,
            List<GuardianReportEntry> output,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        if (index >= appointments.size()) {
            callback.onSuccess(new GuardianReportDashboard(guardian, output));
            return;
        }

        AppointmentRequest appointment = appointments.get(index);
        appointmentClient.resolveCoreId(
                appointment.getId(),
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String coreAppointmentId) {
                        CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot =
                                findSession(sessions, coreAppointmentId);
                        if (sessionSnapshot == null) {
                            output.add(toEntry(appointment, null, null));
                            loadEntries(
                                    guardian,
                                    appointments,
                                    sessions,
                                    index + 1,
                                    output,
                                    callback);
                            return;
                        }

                        CompanionSession session = sessionSnapshot.merge(null, appointment.getId());
                        if (sessionSnapshot.getStatus() != SessionStatus.COMPLETED) {
                            output.add(toEntry(appointment, session, null));
                            loadEntries(
                                    guardian,
                                    appointments,
                                    sessions,
                                    index + 1,
                                    output,
                                    callback);
                            return;
                        }

                        sessionClient.getReport(
                                sessionSnapshot,
                                new RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.ReportSnapshot report
                                    ) {
                                        output.add(toEntry(
                                                appointment,
                                                session,
                                                report.toModel(session.getId())));
                                        loadEntries(
                                                guardian,
                                                appointments,
                                                sessions,
                                                index + 1,
                                                output,
                                                callback);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    @Nullable
    private CoreApiCompanionSessionClient.SessionSnapshot findSession(
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions,
            String coreAppointmentId
    ) {
        for (CoreApiCompanionSessionClient.SessionSnapshot session : sessions) {
            if (session.getCoreAppointmentId().equals(coreAppointmentId)) {
                return session;
            }
        }
        return null;
    }

    private GuardianReportEntry toEntry(
            AppointmentRequest appointment,
            @Nullable CompanionSession session,
            @Nullable SessionReport report
    ) {
        return new GuardianReportEntry(
                appointment,
                new User(
                        appointment.getPatientUserId(),
                        UserRole.PATIENT,
                        appointment.getPatientName(),
                        appointment.getPatientEmail(),
                        appointment.getPatientPhone()),
                toManager(appointment),
                session,
                report,
                HospitalGuideFallbackFactory.create(
                        appointment.getHospitalName(),
                        appointment.getDepartmentName()));
    }

    @Nullable
    private User toManager(AppointmentRequest appointment) {
        if (appointment.getManagerUserId().isEmpty()) {
            return null;
        }
        String name = appointment.getManagerName().isEmpty()
                ? "배정 매니저"
                : appointment.getManagerName();
        return new User(
                appointment.getManagerUserId(),
                UserRole.MANAGER,
                name,
                appointment.getManagerEmail(),
                appointment.getManagerPhone());
    }
}
