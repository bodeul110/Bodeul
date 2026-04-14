package com.example.bodeul.ui.report;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;

import java.util.List;

/**
 * 보호자가 현재 진행 상황과 최종 리포트를 한 화면에서 확인하는 조회 화면이다.
 */
public class GuardianReportActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private GuardianReportRepository guardianReportRepository;
    private User currentUser;
    private boolean loading;

    private TextView textGuardianReportMode;
    private TextView textGuardianReportGreeting;
    private TextView textGuardianReportSummary;
    private TextView textGuardianReportHighlightStatus;
    private TextView textGuardianReportHighlightTitle;
    private TextView textGuardianReportHighlightBody;
    private LinearLayout guardianReportListContainer;
    private ProgressBar progressGuardianReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_report);

        authRepository = ServiceLocator.provideAuthRepository(this);
        guardianReportRepository = ServiceLocator.provideGuardianReportRepository(this);

        textGuardianReportMode = findViewById(R.id.textGuardianReportMode);
        textGuardianReportGreeting = findViewById(R.id.textGuardianReportGreeting);
        textGuardianReportSummary = findViewById(R.id.textGuardianReportSummary);
        textGuardianReportHighlightStatus = findViewById(R.id.textGuardianReportHighlightStatus);
        textGuardianReportHighlightTitle = findViewById(R.id.textGuardianReportHighlightTitle);
        textGuardianReportHighlightBody = findViewById(R.id.textGuardianReportHighlightBody);
        guardianReportListContainer = findViewById(R.id.guardianReportListContainer);
        progressGuardianReport = findViewById(R.id.progressGuardianReport);

        textGuardianReportMode.setText(guardianReportRepository.isFirebaseBacked()
                ? R.string.guardian_report_mode_firebase
                : R.string.guardian_report_mode_demo);

        findViewById(R.id.buttonBackGuardianReport).setOnClickListener(view -> finish());
        bindEmptyState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    Toast.makeText(GuardianReportActivity.this, R.string.toast_guardian_only, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentUser = result;
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                openRoleSelection();
            }
        });
    }

    private void loadDashboard() {
        guardianReportRepository.getGuardianDashboard(currentUser, new RepositoryCallback<GuardianReportDashboard>() {
            @Override
            public void onSuccess(GuardianReportDashboard result) {
                setLoading(false);
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptyState();
                Toast.makeText(GuardianReportActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindDashboard(GuardianReportDashboard dashboard) {
        List<GuardianReportEntry> entries = dashboard.getEntries();
        textGuardianReportGreeting.setText(getString(
                R.string.guardian_report_greeting,
                dashboard.getGuardian().getName()
        ));
        textGuardianReportSummary.setText(getString(
                R.string.guardian_report_summary,
                entries.size(),
                TextUtils.isEmpty(dashboard.getGuardian().getPhone())
                        ? getString(R.string.guardian_report_phone_missing)
                        : dashboard.getGuardian().getPhone()
        ));

        GuardianReportEntry highlightEntry = findHighlightEntry(entries);
        if (highlightEntry == null) {
            bindEmptyHighlight();
        } else {
            bindHighlight(highlightEntry, entries.size());
        }
        renderEntries(entries);
    }

    private GuardianReportEntry findHighlightEntry(List<GuardianReportEntry> entries) {
        for (GuardianReportEntry entry : entries) {
            AppointmentStatus status = entry.getAppointmentRequest().getStatus();
            if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.CANCELED) {
                return entry;
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private void bindHighlight(GuardianReportEntry entry, int count) {
        bindStatusBadge(textGuardianReportHighlightStatus, entry.getAppointmentRequest().getStatus());
        textGuardianReportHighlightTitle.setText(getString(
                R.string.guardian_report_highlight_title,
                entry.getAppointmentRequest().getHospitalName(),
                entry.getAppointmentRequest().getDepartmentName()
        ));
        textGuardianReportHighlightBody.setText(buildHighlightBody(entry, count));
    }

    private CharSequence buildHighlightBody(GuardianReportEntry entry, int count) {
        String patientName = entry.getPatient() == null
                ? getString(R.string.guardian_report_patient_missing)
                : entry.getPatient().getName();
        String managerName = entry.getManager() == null
                ? getString(R.string.guardian_report_manager_pending)
                : entry.getManager().getName();
        String progressLine = buildProgressLine(entry);
        String updateLine = buildGuardianUpdateLine(entry.getSession());
        return getString(
                R.string.guardian_report_highlight_body,
                count,
                patientName,
                entry.getAppointmentRequest().getAppointmentAt(),
                managerName,
                progressLine,
                updateLine
        );
    }

    private String buildProgressLine(GuardianReportEntry entry) {
        CompanionSession session = entry.getSession();
        HospitalGuide guide = entry.getHospitalGuide();
        if (session == null || guide == null) {
            return toStatusLabel(entry.getAppointmentRequest().getStatus());
        }
        return getString(
                R.string.guardian_report_progress_value,
                session.getCurrentStepOrder(),
                guide.getSteps().size(),
                toSessionStatusLabel(session)
        );
    }

    private String buildGuardianUpdateLine(CompanionSession session) {
        if (session == null || TextUtils.isEmpty(session.getGuardianUpdate())) {
            return getString(R.string.guardian_report_update_empty);
        }
        return session.getGuardianUpdate();
    }

    private void renderEntries(List<GuardianReportEntry> entries) {
        guardianReportListContainer.removeAllViews();
        if (entries.isEmpty()) {
            renderEmptyEntry();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (GuardianReportEntry entry : entries) {
            View entryView = inflater.inflate(R.layout.item_guardian_report, guardianReportListContainer, false);
            TextView statusView = entryView.findViewById(R.id.textGuardianReportItemStatus);
            TextView titleView = entryView.findViewById(R.id.textGuardianReportItemTitle);
            TextView patientView = entryView.findViewById(R.id.textGuardianReportItemPatient);
            TextView scheduleView = entryView.findViewById(R.id.textGuardianReportItemSchedule);
            TextView managerView = entryView.findViewById(R.id.textGuardianReportItemManager);
            TextView progressView = entryView.findViewById(R.id.textGuardianReportItemProgress);
            TextView updateView = entryView.findViewById(R.id.textGuardianReportItemUpdate);
            TextView pendingView = entryView.findViewById(R.id.textGuardianReportItemReportPending);
            TextView summaryView = entryView.findViewById(R.id.textGuardianReportItemReportSummary);
            TextView treatmentView = entryView.findViewById(R.id.textGuardianReportItemReportTreatment);
            TextView medicationView = entryView.findViewById(R.id.textGuardianReportItemReportMedication);
            TextView nextVisitView = entryView.findViewById(R.id.textGuardianReportItemReportNextVisit);

            bindStatusBadge(statusView, entry.getAppointmentRequest().getStatus());
            titleView.setText(getString(
                    R.string.guardian_report_item_title,
                    entry.getAppointmentRequest().getHospitalName(),
                    entry.getAppointmentRequest().getDepartmentName()
            ));
            patientView.setText(getString(
                    R.string.guardian_report_item_patient,
                    entry.getPatient() == null
                            ? getString(R.string.guardian_report_patient_missing)
                            : entry.getPatient().getName()
            ));
            scheduleView.setText(getString(
                    R.string.guardian_report_item_schedule,
                    entry.getAppointmentRequest().getAppointmentAt(),
                    entry.getAppointmentRequest().getMeetingPlace()
            ));
            managerView.setText(getString(
                    R.string.guardian_report_item_manager,
                    entry.getManager() == null
                            ? getString(R.string.guardian_report_manager_pending)
                            : entry.getManager().getName()
            ));
            progressView.setText(getString(
                    R.string.guardian_report_item_progress,
                    buildProgressLine(entry)
            ));
            updateView.setText(getString(
                    R.string.guardian_report_item_update,
                    buildGuardianUpdateLine(entry.getSession())
            ));

            SessionReport report = entry.getSessionReport();
            if (report == null) {
                pendingView.setVisibility(View.VISIBLE);
                summaryView.setVisibility(View.GONE);
                treatmentView.setVisibility(View.GONE);
                medicationView.setVisibility(View.GONE);
                nextVisitView.setVisibility(View.GONE);
            } else {
                pendingView.setVisibility(View.GONE);
                bindOptionalLine(summaryView, R.string.guardian_report_item_report_summary, report.getSummary());
                bindOptionalLine(treatmentView, R.string.guardian_report_item_report_treatment, report.getTreatmentNotes());
                bindOptionalLine(medicationView, R.string.guardian_report_item_report_medication, report.getMedicationNotes());
                bindOptionalLine(nextVisitView, R.string.guardian_report_item_report_next_visit, report.getNextVisitAt());
            }

            guardianReportListContainer.addView(entryView);
        }
    }

    private void bindOptionalLine(TextView textView, int formatResId, String value) {
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(getString(formatResId, value));
    }

    private void renderEmptyEntry() {
        guardianReportListContainer.removeAllViews();

        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emptyView.setPadding(padding, padding, padding, padding);
        emptyView.setText(R.string.guardian_report_list_empty);
        emptyView.setTextColor(getColor(R.color.bodeul_text_secondary));
        emptyView.setTextSize(14f);
        guardianReportListContainer.addView(emptyView);
    }

    private void bindEmptyState() {
        textGuardianReportGreeting.setText(R.string.guardian_report_empty_greeting);
        textGuardianReportSummary.setText(R.string.guardian_report_empty_summary);
        bindEmptyHighlight();
        renderEmptyEntry();
    }

    private void bindEmptyHighlight() {
        textGuardianReportHighlightStatus.setText(R.string.booking_empty_badge);
        textGuardianReportHighlightStatus.setTextColor(getColor(R.color.bodeul_text_secondary));
        textGuardianReportHighlightStatus.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bodeul_surface_alt)));
        textGuardianReportHighlightTitle.setText(R.string.guardian_report_empty_title);
        textGuardianReportHighlightBody.setText(R.string.guardian_report_empty_body);
    }

    private void bindStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case MATCHED:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case IN_PROGRESS:
            case COMPLETED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case CANCELED:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
            case REQUESTED:
            default:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
        }

        textView.setText(toStatusLabel(status));
        textView.setBackgroundTintList(ColorStateList.valueOf(getColor(backgroundColor)));
        textView.setTextColor(getColor(textColor));
    }

    private String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return getString(R.string.booking_status_completed);
            case CANCELED:
                return getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return getString(R.string.booking_status_requested);
        }
    }

    private String toSessionStatusLabel(CompanionSession session) {
        switch (session.getStatus()) {
            case READY:
                return getString(R.string.guardian_report_session_ready);
            case WAITING:
                return getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return getString(R.string.guardian_report_session_payment);
            case COMPLETED:
                return getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return getString(R.string.guardian_report_session_meeting);
        }
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressGuardianReport.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
