package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 예약 폼에서 병원과 진료과를 검색해 선택하는 전용 화면이다.
 */
public class BookingHospitalSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_INITIAL_HOSPITAL = "initialHospital";
    private static final String EXTRA_INITIAL_DEPARTMENT = "initialDepartment";
    private static final String EXTRA_HOSPITAL_LATITUDE = "hospitalLatitude";
    private static final String EXTRA_HOSPITAL_LONGITUDE = "hospitalLongitude";

    private BookingHospitalSelectorCoordinator coordinator;
    private BookingHospitalOptionAdapter optionAdapter;

    private SearchView searchView;
    private TextView textResultCount;
    private TextView textEmpty;
    private ProgressBar progressHospitalSelector;
    private View statePanel;
    private View contentContainer;
    private ListView listHospitalOptions;
    private MaterialButton buttonManualInput;
    private BookingHospitalCatalog catalog = BookingHospitalCatalog.empty();
    private com.example.bodeul.data.map.KakaoLocalPlaceSearchClient kakaoClient;
    private int searchRequestVersion;

    public static Intent createIntent(Context context, BookingHospitalSelection selection) {
        Intent intent = new Intent(context, BookingHospitalSelectorActivity.class);
        intent.putExtra(EXTRA_INITIAL_HOSPITAL, selection.getHospitalName());
        intent.putExtra(EXTRA_INITIAL_DEPARTMENT, selection.getDepartmentName());
        intent.putExtra(EXTRA_HOSPITAL_LATITUDE, selection.getHospitalLatitude());
        intent.putExtra(EXTRA_HOSPITAL_LONGITUDE, selection.getHospitalLongitude());
        return intent;
    }

    public static BookingHospitalSelection parseResult(@Nullable Intent data) {
        if (data == null) {
            return new BookingHospitalSelection("", "");
        }
        return new BookingHospitalSelection(
                data.getStringExtra(EXTRA_INITIAL_HOSPITAL),
                data.getStringExtra(EXTRA_INITIAL_DEPARTMENT),
                data.getDoubleExtra(EXTRA_HOSPITAL_LATITUDE, 0.0),
                data.getDoubleExtra(EXTRA_HOSPITAL_LONGITUDE, 0.0)
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_hospital_selector);

        coordinator = new BookingHospitalSelectorCoordinator(ServiceLocator.provideBookingRepository(this));
        optionAdapter = new BookingHospitalOptionAdapter(this);
        kakaoClient = new com.example.bodeul.data.map.KakaoLocalPlaceSearchClient(this);

        searchView = findViewById(R.id.searchBookingHospital);
        textResultCount = findViewById(R.id.textBookingHospitalResultCount);
        textEmpty = findViewById(R.id.textBookingHospitalEmpty);
        progressHospitalSelector = findViewById(R.id.progressBookingHospitalSelector);
        statePanel = findViewById(R.id.bookingHospitalSelectorStatePanel);
        contentContainer = findViewById(R.id.bookingHospitalSelectorContent);
        listHospitalOptions = findViewById(R.id.listBookingHospitalOptions);
        buttonManualInput = findViewById(R.id.buttonBookingHospitalManualInput);

        listHospitalOptions.setAdapter(optionAdapter);
        listHospitalOptions.setEmptyView(textEmpty);
        listHospitalOptions.setOnItemClickListener((parent, view, position, id) ->
                openDepartmentSelector(optionAdapter.getItem(position)));
        findViewById(R.id.buttonBackBookingHospitalSelector).setOnClickListener(view -> finish());
        buttonManualInput.setOnClickListener(view -> showManualInputDialog(
                searchView.getQuery() == null ? "" : searchView.getQuery().toString(),
                "",
                0.0,
                0.0
        ));

        searchView.setQueryHint(getString(R.string.booking_hospital_selector_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                updateOptionList(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                updateOptionList(newText);
                return true;
            }
        });

        String initialHospital = getIntent().getStringExtra(EXTRA_INITIAL_HOSPITAL);
        if (!TextUtils.isEmpty(initialHospital)) {
            searchView.setQuery(initialHospital, false);
        }

        loadCatalog();
    }

    private void loadCatalog() {
        setLoading(true);
        hideStatePanel();
        coordinator.loadCatalog(new RepositoryCallback<BookingHospitalCatalog>() {
            @Override
            public void onSuccess(BookingHospitalCatalog result) {
                catalog = result == null ? BookingHospitalCatalog.empty() : result;
                setLoading(false);
                updateOptionList(searchView.getQuery() == null ? "" : searchView.getQuery().toString());
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showErrorState(message);
            }
        });
    }

    private void updateOptionList(String query) {
        String requestedQuery = query == null ? "" : query;
        int requestVersion = ++searchRequestVersion;
        if (catalog.isEmpty()) {
            textEmpty.setText(R.string.booking_hospital_selector_empty_all);
            optionAdapter.submitList(java.util.Collections.emptyList());
            textResultCount.setText(getString(R.string.booking_hospital_selector_result_count, 0));
            return;
        }

        coordinator.searchOptions(requestedQuery, catalog, kakaoClient, new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override
            public void onSuccess(List<BookingHospitalOption> filteredOptions) {
                if (requestVersion != searchRequestVersion) {
                    return;
                }
                optionAdapter.submitList(filteredOptions);
                textResultCount.setText(getString(
                        R.string.booking_hospital_selector_result_count,
                        filteredOptions.size()
                ));
                if (filteredOptions.isEmpty()) {
                    textEmpty.setText(R.string.booking_hospital_selector_empty_filtered);
                }
            }

            @Override
            public void onError(String message) {
                // 검색 오류는 내부 fallback 결과로 흡수한다.
            }
        });
    }

    private void openDepartmentSelector(BookingHospitalOption option) {
        List<String> departmentNames = option.getDepartmentNames();
        if (departmentNames.isEmpty()) {
            showManualInputDialog(option.getHospitalName(), "", option.getLatitude(), option.getLongitude());
            return;
        }
        if (departmentNames.size() == 1) {
            finishWithSelection(option.getHospitalName(), option.getLatitude(), option.getLongitude(), departmentNames.get(0));
            return;
        }

        CharSequence[] items = departmentNames.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(
                        R.string.booking_hospital_selector_department_title,
                        option.getHospitalName()
                ))
                .setItems(items, (dialogInterface, which) ->
                        finishWithSelection(option.getHospitalName(), option.getLatitude(), option.getLongitude(), departmentNames.get(which)))
                .setNegativeButton(R.string.booking_hospital_selector_department_cancel, null)
                .show();
    }

    private void finishWithSelection(String hospitalName, double latitude, double longitude, String departmentName) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_INITIAL_HOSPITAL, hospitalName);
        resultIntent.putExtra(EXTRA_INITIAL_DEPARTMENT, departmentName);
        resultIntent.putExtra(EXTRA_HOSPITAL_LATITUDE, latitude);
        resultIntent.putExtra(EXTRA_HOSPITAL_LONGITUDE, longitude);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showManualInputDialog(
            String initialHospitalName,
            String initialDepartmentName,
            double latitude,
            double longitude
    ) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int spacing = Math.round(12 * getResources().getDisplayMetrics().density);
        container.setPadding(spacing, spacing, spacing, 0);

        TextInputLayout hospitalLayout = new TextInputLayout(this);
        hospitalLayout.setHint(getString(R.string.admin_guide_hospital_hint));
        TextInputEditText hospitalInput = new TextInputEditText(hospitalLayout.getContext());
        hospitalInput.setSingleLine(true);
        hospitalInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hospitalInput.setText(initialHospitalName);
        hospitalLayout.addView(hospitalInput);
        container.addView(hospitalLayout);

        TextInputLayout departmentLayout = new TextInputLayout(this);
        departmentLayout.setHint(getString(R.string.admin_guide_department_hint));
        TextInputEditText departmentInput = new TextInputEditText(departmentLayout.getContext());
        departmentInput.setSingleLine(true);
        departmentInput.setInputType(InputType.TYPE_CLASS_TEXT);
        departmentInput.setText(initialDepartmentName);
        departmentLayout.addView(departmentInput);
        LinearLayout.LayoutParams departmentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        departmentParams.topMargin = spacing;
        container.addView(departmentLayout, departmentParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.booking_hospital_selector_manual_title)
                .setMessage(R.string.booking_hospital_selector_manual_body)
                .setView(container)
                .setPositiveButton(R.string.booking_hospital_selector_manual_confirm, null)
                .setNegativeButton(R.string.booking_hospital_selector_department_cancel, null)
                .create();
        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String hospitalName = valueOf(hospitalInput);
                    String departmentName = valueOf(departmentInput);
                    hospitalLayout.setError(null);
                    departmentLayout.setError(null);
                    boolean valid = true;
                    if (TextUtils.isEmpty(hospitalName)) {
                        hospitalLayout.setError(getString(R.string.error_required_field));
                        valid = false;
                    }
                    if (TextUtils.isEmpty(departmentName)) {
                        departmentLayout.setError(getString(R.string.error_required_field));
                        valid = false;
                    }
                    if (!valid) {
                        return;
                    }
                    finishWithSelection(hospitalName, latitude, longitude, departmentName);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        progressHospitalSelector.setVisibility(loading ? View.VISIBLE : View.GONE);
        contentContainer.setVisibility(loading ? View.GONE : View.VISIBLE);
        searchView.setEnabled(!loading);
        buttonManualInput.setEnabled(!loading);
    }

    private void showErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        StatePanelHelper.show(
                statePanel,
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.booking_hospital_selector_error_title),
                body,
                getString(R.string.state_action_retry),
                view -> loadCatalog(),
                null,
                null
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void hideStatePanel() {
        StatePanelHelper.hide(statePanel);
        contentContainer.setVisibility(View.VISIBLE);
    }
}
