package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 예약 신청 중 환자의 건강 정보와 이동 보조 수준을 입력받는다.
 */
public class BookingHealthProfileActivity extends AppCompatActivity {
    private static final String EXTRA_CONDITION = "patientConditionSummary";
    private static final String EXTRA_MEDICATION = "medicationSummary";
    private static final String EXTRA_SPECIAL_NOTES = "specialNotes";
    private static final String EXTRA_MOBILITY = "mobilitySupport";
    private static final String STATE_MOBILITY = "selectedMobility";

    private TextInputLayout layoutCondition;
    private TextInputEditText inputCondition;
    private TextInputEditText inputMedication;
    private TextInputEditText inputSpecialNotes;
    private MaterialButton buttonIndependent;
    private MaterialButton buttonWalkingAid;
    private MaterialButton buttonWheelchair;
    private ScrollView scrollView;
    private BookingMobilitySupport selectedMobility = BookingMobilitySupport.INDEPENDENT;

    public static Intent createIntent(Context context, BookingHealthProfileSelection selection) {
        BookingHealthProfileSelection safeSelection = selection == null
                ? new BookingHealthProfileSelection("", "", "", BookingMobilitySupport.INDEPENDENT)
                : selection;
        Intent intent = new Intent(context, BookingHealthProfileActivity.class);
        intent.putExtra(EXTRA_CONDITION, safeSelection.getPatientConditionSummary());
        intent.putExtra(EXTRA_MEDICATION, safeSelection.getMedicationSummary());
        intent.putExtra(EXTRA_SPECIAL_NOTES, safeSelection.getSpecialNotes());
        intent.putExtra(EXTRA_MOBILITY, safeSelection.getMobilitySupport().name());
        return intent;
    }

    public static BookingHealthProfileSelection parseResult(@Nullable Intent data) {
        if (data == null) {
            return new BookingHealthProfileSelection("", "", "", BookingMobilitySupport.INDEPENDENT);
        }
        return new BookingHealthProfileSelection(
                data.getStringExtra(EXTRA_CONDITION),
                data.getStringExtra(EXTRA_MEDICATION),
                data.getStringExtra(EXTRA_SPECIAL_NOTES),
                BookingMobilitySupport.fromValue(data.getStringExtra(EXTRA_MOBILITY))
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_health_profile);
        configureSystemBars();
        bindViews();
        restoreSelection(savedInstanceState);
        bindActions();
        renderMobilitySelection();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MOBILITY, selectedMobility.name());
    }

    private void bindViews() {
        layoutCondition = findViewById(R.id.layoutBookingHealthProfileCondition);
        inputCondition = findViewById(R.id.inputBookingHealthProfileCondition);
        inputMedication = findViewById(R.id.inputBookingHealthProfileMedication);
        inputSpecialNotes = findViewById(R.id.inputBookingHealthProfileSpecialNotes);
        buttonIndependent = findViewById(R.id.buttonBookingHealthProfileIndependent);
        buttonWalkingAid = findViewById(R.id.buttonBookingHealthProfileWalkingAid);
        buttonWheelchair = findViewById(R.id.buttonBookingHealthProfileWheelchair);
        bindHeadingAccent(findViewById(R.id.textBookingHealthProfileHeading));
        bindKeyboardScroll(inputCondition);
        bindKeyboardScroll(inputMedication);
        bindKeyboardScroll(inputSpecialNotes);
    }

    private void bindHeadingAccent(TextView heading) {
        String text = getString(R.string.booking_health_profile_heading);
        String accent = getString(R.string.booking_health_profile_heading_accent);
        int accentStart = text.indexOf(accent);
        if (accentStart < 0) {
            heading.setText(text);
            return;
        }
        SpannableString styledText = new SpannableString(text);
        styledText.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.figma_mvp_primary_end)),
                accentStart,
                accentStart + accent.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        heading.setText(styledText);
    }

    private void restoreSelection(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            inputCondition.setText(getIntent().getStringExtra(EXTRA_CONDITION));
            inputMedication.setText(getIntent().getStringExtra(EXTRA_MEDICATION));
            inputSpecialNotes.setText(getIntent().getStringExtra(EXTRA_SPECIAL_NOTES));
            selectedMobility = BookingMobilitySupport.fromValue(getIntent().getStringExtra(EXTRA_MOBILITY));
            return;
        }
        selectedMobility = BookingMobilitySupport.fromValue(savedInstanceState.getString(STATE_MOBILITY));
    }

    private void bindActions() {
        View.OnClickListener closeListener = view -> finish();
        findViewById(R.id.buttonBookingHealthProfileBack).setOnClickListener(closeListener);
        findViewById(R.id.buttonBookingHealthProfileClose).setOnClickListener(closeListener);
        buttonIndependent.setOnClickListener(view -> selectMobility(BookingMobilitySupport.INDEPENDENT));
        buttonWalkingAid.setOnClickListener(view -> selectMobility(BookingMobilitySupport.WALKING_AID));
        buttonWheelchair.setOnClickListener(view -> selectMobility(BookingMobilitySupport.WHEELCHAIR));
        findViewById(R.id.buttonBookingHealthProfileComplete).setOnClickListener(view -> returnSelection());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void bindKeyboardScroll(View inputView) {
        inputView.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                return;
            }
            view.postDelayed(() -> {
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                int targetScrollY = scrollView.getScrollY() + location[1] - dp(96);
                scrollView.smoothScrollTo(0, Math.max(0, targetScrollY));
            }, 450L);
        });
    }

    private void selectMobility(BookingMobilitySupport mobilitySupport) {
        selectedMobility = mobilitySupport;
        renderMobilitySelection();
    }

    private void renderMobilitySelection() {
        bindMobilityButton(buttonIndependent, selectedMobility == BookingMobilitySupport.INDEPENDENT);
        bindMobilityButton(buttonWalkingAid, selectedMobility == BookingMobilitySupport.WALKING_AID);
        bindMobilityButton(buttonWheelchair, selectedMobility == BookingMobilitySupport.WHEELCHAIR);
    }

    private void bindMobilityButton(MaterialButton button, boolean selected) {
        int backgroundColor = ContextCompat.getColor(
                this,
                selected ? R.color.figma_mvp_primary : R.color.figma_mvp_input
        );
        int contentColor = ContextCompat.getColor(
                this,
                selected ? R.color.white : R.color.figma_mvp_text_secondary
        );
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setTextColor(contentColor);
        button.setIconTint(ColorStateList.valueOf(contentColor));
        button.setSelected(selected);
    }

    private void returnSelection() {
        String condition = valueOf(inputCondition);
        if (condition.isEmpty()) {
            layoutCondition.setError(getString(R.string.error_required_field));
            inputCondition.requestFocus();
            return;
        }
        layoutCondition.setError(null);
        Intent result = new Intent();
        result.putExtra(EXTRA_CONDITION, condition);
        result.putExtra(EXTRA_MEDICATION, valueOf(inputMedication));
        result.putExtra(EXTRA_SPECIAL_NOTES, valueOf(inputSpecialNotes));
        result.putExtra(EXTRA_MOBILITY, selectedMobility.name());
        setResult(RESULT_OK, result);
        finish();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.figma_mvp_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.figma_mvp_background));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);

        ScrollView content = findViewById(R.id.scrollBookingHealthProfile);
        scrollView = content;
        View topBar = findViewById(R.id.layoutBookingHealthProfileTopBar);
        int contentTop = content.getPaddingTop();
        int contentBottom = content.getPaddingBottom();
        int topBarPaddingTop = topBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    insets.left,
                    contentTop,
                    insets.right,
                    contentBottom + Math.max(insets.bottom, imeInsets.bottom)
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(insets.left, topBarPaddingTop + insets.top, insets.right, 0);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
