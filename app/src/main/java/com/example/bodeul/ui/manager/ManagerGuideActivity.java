package com.example.bodeul.ui.manager;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 매니저가 단계별 동행을 진행하고 메모와 리포트를 기록하는 실행 화면이다.
 */
public class ManagerGuideActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private User currentUser;

    private TextView textGuidePatient;
    private TextView textGuideGuardian;
    private TextView textGuideSchedule;
    private TextView textGuidePlace;
    private TextView textGuideRequestNote;
    private LinearLayout guideStepsContainer;
    private MaterialButton buttonAdvanceGuide;
    private MaterialButton buttonSubmitReport;
    private TextInputEditText inputGuardianUpdate;
    private TextInputEditText inputMedicationNote;
    private TextInputEditText inputReportSummary;
    private TextInputEditText inputReportTreatment;
    private TextInputEditText inputNextVisit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_guide);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);

        textGuidePatient = findViewById(R.id.textGuidePatient);
        textGuideGuardian = findViewById(R.id.textGuideGuardian);
        textGuideSchedule = findViewById(R.id.textGuideSchedule);
        textGuidePlace = findViewById(R.id.textGuidePlace);
        textGuideRequestNote = findViewById(R.id.textGuideRequestNote);
        guideStepsContainer = findViewById(R.id.guideStepsContainer);
        buttonAdvanceGuide = findViewById(R.id.buttonAdvanceGuide);
        buttonSubmitReport = findViewById(R.id.buttonSubmitReport);
        inputGuardianUpdate = findViewById(R.id.inputGuardianUpdate);
        inputMedicationNote = findViewById(R.id.inputMedicationNote);
        inputReportSummary = findViewById(R.id.inputReportSummary);
        inputReportTreatment = findViewById(R.id.inputReportTreatment);
        inputNextVisit = findViewById(R.id.inputNextVisit);

        findViewById(R.id.buttonBackGuide).setOnClickListener(view -> finish());
        findViewById(R.id.buttonSaveGuardianUpdate).setOnClickListener(view -> saveGuardianUpdate());
        findViewById(R.id.buttonSaveMedicationNote).setOnClickListener(view -> saveMedicationNote());
        buttonAdvanceGuide.setOnClickListener(view -> advanceStep());
        buttonSubmitReport.setOnClickListener(view -> submitReport());
    }

    @Override
    protected void onStart() {
        super.onStart();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result.getRole() != UserRole.MANAGER) {
                    Toast.makeText(ManagerGuideActivity.this, R.string.toast_manager_only, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentUser = result;
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                openRoleSelection();
            }
        });
    }

    private void loadDashboard() {
        // 로그인된 매니저 기준으로 현재 진행 중인 동행 정보를 다시 조회한다.
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindDashboard(ManagerDashboard dashboard) {
        // 화면 전체가 하나의 대시보드 모델을 기준으로 다시 그려지도록 값을 채운다.
        textGuidePatient.setText(getString(R.string.guide_patient_label) + ": "
                + dashboard.getPatient().getName());
        textGuideGuardian.setText(getString(R.string.guide_guardian_label) + ": "
                + dashboard.getGuardian().getName() + " / " + dashboard.getGuardian().getPhone());
        textGuideSchedule.setText(getString(R.string.guide_schedule_label) + ": "
                + dashboard.getAppointmentRequest().getAppointmentAt());
        textGuidePlace.setText(getString(R.string.guide_place_label) + ": "
                + dashboard.getAppointmentRequest().getMeetingPlace());
        textGuideRequestNote.setText(getString(R.string.guide_note_label) + ": "
                + dashboard.getAppointmentRequest().getSpecialNotes());

        CompanionSession session = dashboard.getSession();
        inputGuardianUpdate.setText(nullToEmpty(session.getGuardianUpdate()));
        inputMedicationNote.setText(nullToEmpty(session.getMedicationNote()));

        SessionReport sessionReport = dashboard.getSessionReport();
        if (sessionReport != null) {
            inputReportSummary.setText(sessionReport.getSummary());
            inputReportTreatment.setText(sessionReport.getTreatmentNotes());
            if (TextUtils.isEmpty(valueOf(inputMedicationNote))) {
                inputMedicationNote.setText(sessionReport.getMedicationNotes());
            }
            inputNextVisit.setText(sessionReport.getNextVisitAt());
            buttonSubmitReport.setText(R.string.guide_report_update);
        } else {
            buttonSubmitReport.setText(R.string.guide_report_submit);
        }

        renderSteps(dashboard);
        updateAdvanceButton(dashboard);
    }

    private void renderSteps(ManagerDashboard dashboard) {
        guideStepsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        CompanionSession session = dashboard.getSession();

        for (GuideStep step : dashboard.getHospitalGuide().getSteps()) {
            View stepView = inflater.inflate(R.layout.item_manager_step, guideStepsContainer, false);
            MaterialCardView cardView = (MaterialCardView) stepView;
            TextView badge = stepView.findViewById(R.id.textStepBadge);
            TextView title = stepView.findViewById(R.id.textStepTitle);
            TextView description = stepView.findViewById(R.id.textStepDescription);
            TextView state = stepView.findViewById(R.id.textStepState);

            badge.setText(String.valueOf(step.getOrder()));
            title.setText(step.getTitle());
            description.setText(step.getDescription());

            boolean completed = session.getStatus() == SessionStatus.COMPLETED
                    ? step.getOrder() <= session.getCurrentStepOrder()
                    : step.getOrder() < session.getCurrentStepOrder();
            boolean active = !completed && step.getOrder() == session.getCurrentStepOrder();

            // 단계 상태에 따라 카드 색상과 라벨을 달리 표시한다.
            int tintColor;
            int textColor;
            int strokeColor;
            int stateText;
            if (completed) {
                tintColor = getColor(R.color.bodeul_success);
                textColor = getColor(R.color.white);
                strokeColor = getColor(R.color.bodeul_success);
                stateText = R.string.guide_status_completed;
            } else if (active) {
                tintColor = getColor(R.color.bodeul_primary);
                textColor = getColor(R.color.white);
                strokeColor = getColor(R.color.bodeul_primary);
                stateText = R.string.guide_status_active;
            } else {
                tintColor = getColor(R.color.bodeul_surface_alt);
                textColor = getColor(R.color.bodeul_primary);
                strokeColor = getColor(R.color.bodeul_outline);
                stateText = R.string.guide_status_pending;
            }

            badge.setBackgroundTintList(ColorStateList.valueOf(tintColor));
            badge.setTextColor(textColor);
            state.setBackgroundTintList(ColorStateList.valueOf(tintColor));
            state.setTextColor(textColor);
            state.setText(stateText);
            cardView.setStrokeColor(strokeColor);

            guideStepsContainer.addView(stepView);
        }
    }

    private void updateAdvanceButton(ManagerDashboard dashboard) {
        // 현재 단계와 종료 여부에 따라 다음 단계 버튼의 문구와 활성화를 바꾼다.
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        CompanionSession session = dashboard.getSession();
        if (session.getStatus() == SessionStatus.COMPLETED) {
            buttonAdvanceGuide.setText(R.string.guide_button_done);
            buttonAdvanceGuide.setEnabled(false);
            return;
        }

        if (session.getCurrentStepOrder() >= totalSteps) {
            buttonAdvanceGuide.setText(R.string.guide_button_last);
            buttonAdvanceGuide.setEnabled(false);
            return;
        }

        buttonAdvanceGuide.setText(R.string.guide_button_next);
        buttonAdvanceGuide.setEnabled(true);
    }

    private void advanceStep() {
        if (currentUser == null) {
            return;
        }

        // 단계 이동 후에는 서버 응답으로 화면 전체를 다시 바인딩한다.
        managerRepository.advanceCurrentStep(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_status_completed, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveGuardianUpdate() {
        if (currentUser == null) {
            return;
        }

        // 보호자에게 공유할 핵심 현황만 따로 저장할 수 있게 분리했다.
        String message = valueOf(inputGuardianUpdate);
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveGuardianUpdate(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_guardian_save, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveMedicationNote() {
        if (currentUser == null) {
            return;
        }

        // 약 수령 여부와 복약 안내 메모를 별도 저장해 리포트 작성 전에 누적한다.
        String note = valueOf(inputMedicationNote);
        if (TextUtils.isEmpty(note)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.saveMedicationNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                Toast.makeText(ManagerGuideActivity.this, R.string.guide_medication_save, Toast.LENGTH_SHORT).show();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitReport() {
        if (currentUser == null) {
            return;
        }

        // 최종 리포트는 요약 내용을 필수로 받고 나머지 항목은 선택 입력으로 저장한다.
        String summary = valueOf(inputReportSummary);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(this, R.string.toast_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }

        managerRepository.submitSessionReport(
                currentUser.getId(),
                summary,
                valueOf(inputReportTreatment),
                valueOf(inputMedicationNote),
                valueOf(inputNextVisit),
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        Toast.makeText(ManagerGuideActivity.this, R.string.guide_report_submit, Toast.LENGTH_SHORT).show();
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ManagerGuideActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void openRoleSelection() {
        // 세션이 없거나 권한이 다르면 로그인 흐름으로 되돌린다.
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
