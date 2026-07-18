package com.example.bodeul.data.coreapi;

import android.content.Context;

import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.firebase.FirebaseGuardianReportRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * 보호자 화면의 예약·세션·리포트 원본은 Core API에서 읽고 보조 프로필과 가이드는 Firebase에서 합성한다.
 */
public final class CoreApiGuardianReportRepository implements GuardianReportRepository {
    private final FirebaseGuardianReportRepository firebaseRepository;
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiCompanionSessionClient sessionClient;

    public CoreApiGuardianReportRepository(
            Context context,
            FirebaseGuardianReportRepository firebaseRepository
    ) {
        this.firebaseRepository = firebaseRepository;
        this.appointmentClient = new CoreApiAppointmentClient(context);
        this.sessionClient = new CoreApiCompanionSessionClient(context);
    }

    @Override
    public void getGuardianDashboard(
            User currentUser,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        firebaseRepository.getGuardianDashboard(
                currentUser,
                new RepositoryCallback<GuardianReportDashboard>() {
                    @Override
                    public void onSuccess(GuardianReportDashboard result) {
                        overlayEntries(
                                result.getGuardian(),
                                result.getEntries(),
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
    public boolean isFirebaseBacked() {
        return true;
    }

    private void overlayEntries(
            User guardian,
            List<GuardianReportEntry> source,
            int index,
            List<GuardianReportEntry> output,
            RepositoryCallback<GuardianReportDashboard> callback
    ) {
        if (index >= source.size()) {
            callback.onSuccess(new GuardianReportDashboard(guardian, output));
            return;
        }
        GuardianReportEntry entry = source.get(index);
        appointmentClient.getAppointment(
                entry.getAppointmentRequest().getId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        if (entry.getSession() == null) {
                            output.add(copyEntry(entry, appointment, null, null));
                            overlayEntries(guardian, source, index + 1, output, callback);
                            return;
                        }
                        sessionClient.findSession(
                                entry.getSession().getId(),
                                null,
                                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
                                    ) {
                                        if (sessionSnapshot == null) {
                                            callback.onError("PostgreSQL 동행 세션 정보를 찾지 못했습니다.");
                                            return;
                                        }
                                        CompanionSession session = sessionSnapshot.merge(
                                                entry.getSession(),
                                                appointment.getId());
                                        if (sessionSnapshot.getStatus() != SessionStatus.COMPLETED) {
                                            output.add(copyEntry(entry, appointment, session, null));
                                            overlayEntries(
                                                    guardian,
                                                    source,
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
                                                        output.add(copyEntry(
                                                                entry,
                                                                appointment,
                                                                session,
                                                                report.toModel(session.getId())));
                                                        overlayEntries(
                                                                guardian,
                                                                source,
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

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private GuardianReportEntry copyEntry(
            GuardianReportEntry source,
            AppointmentRequest appointment,
            CompanionSession session,
            SessionReport report
    ) {
        return new GuardianReportEntry(
                appointment,
                source.getPatient(),
                source.getManager(),
                session,
                report,
                source.getHospitalGuide());
    }
}
