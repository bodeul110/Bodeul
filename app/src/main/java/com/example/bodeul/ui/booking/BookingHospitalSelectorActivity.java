package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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

import java.util.List;

/**
 * 예약 폼에서 병원과 진료과를 검색해 선택하는 전용 화면이다.
 */
public class BookingHospitalSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_INITIAL_HOSPITAL = "initialHospital";
    private static final String EXTRA_INITIAL_DEPARTMENT = "initialDepartment";

    private BookingHospitalSelectorCoordinator coordinator;
    private BookingHospitalOptionAdapter optionAdapter;

    private SearchView searchView;
    private TextView textResultCount;
    private TextView textEmpty;
    private ProgressBar progressHospitalSelector;
    private View statePanel;
    private View contentContainer;
    private ListView listHospitalOptions;
    private BookingHospitalCatalog catalog = BookingHospitalCatalog.empty();

    public static Intent createIntent(Context context, BookingHospitalSelection selection) {
        Intent intent = new Intent(context, BookingHospitalSelectorActivity.class);
        intent.putExtra(EXTRA_INITIAL_HOSPITAL, selection.getHospitalName());
        intent.putExtra(EXTRA_INITIAL_DEPARTMENT, selection.getDepartmentName());
        return intent;
    }

    public static BookingHospitalSelection parseResult(@Nullable Intent data) {
        if (data == null) {
            return new BookingHospitalSelection("", "");
        }
        return new BookingHospitalSelection(
                data.getStringExtra(EXTRA_INITIAL_HOSPITAL),
                data.getStringExtra(EXTRA_INITIAL_DEPARTMENT)
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_hospital_selector);

        coordinator = new BookingHospitalSelectorCoordinator(ServiceLocator.provideBookingRepository(this));
        optionAdapter = new BookingHospitalOptionAdapter(this);

        searchView = findViewById(R.id.searchBookingHospital);
        textResultCount = findViewById(R.id.textBookingHospitalResultCount);
        textEmpty = findViewById(R.id.textBookingHospitalEmpty);
        progressHospitalSelector = findViewById(R.id.progressBookingHospitalSelector);
        statePanel = findViewById(R.id.bookingHospitalSelectorStatePanel);
        contentContainer = findViewById(R.id.bookingHospitalSelectorContent);
        listHospitalOptions = findViewById(R.id.listBookingHospitalOptions);

        listHospitalOptions.setAdapter(optionAdapter);
        listHospitalOptions.setEmptyView(textEmpty);
        listHospitalOptions.setOnItemClickListener((parent, view, position, id) ->
                openDepartmentSelector(optionAdapter.getItem(position)));
        findViewById(R.id.buttonBackBookingHospitalSelector).setOnClickListener(view -> finish());

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
        List<BookingHospitalOption> filteredOptions = catalog.filter(query);
        optionAdapter.submitList(filteredOptions);
        textResultCount.setText(getString(
                R.string.booking_hospital_selector_result_count,
                filteredOptions.size()
        ));
        if (catalog.isEmpty()) {
            textEmpty.setText(R.string.booking_hospital_selector_empty_all);
            return;
        }
        if (filteredOptions.isEmpty()) {
            textEmpty.setText(R.string.booking_hospital_selector_empty_filtered);
        }
    }

    private void openDepartmentSelector(BookingHospitalOption option) {
        List<String> departmentNames = option.getDepartmentNames();
        if (departmentNames.size() == 1) {
            finishWithSelection(option.getHospitalName(), departmentNames.get(0));
            return;
        }

        CharSequence[] items = departmentNames.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(
                        R.string.booking_hospital_selector_department_title,
                        option.getHospitalName()
                ))
                .setItems(items, (dialogInterface, which) ->
                        finishWithSelection(option.getHospitalName(), departmentNames.get(which)))
                .setNegativeButton(R.string.booking_hospital_selector_department_cancel, null)
                .show();
    }

    private void finishWithSelection(String hospitalName, String departmentName) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_INITIAL_HOSPITAL, hospitalName);
        resultIntent.putExtra(EXTRA_INITIAL_DEPARTMENT, departmentName);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressHospitalSelector.setVisibility(loading ? View.VISIBLE : View.GONE);
        contentContainer.setVisibility(loading ? View.GONE : View.VISIBLE);
        searchView.setEnabled(!loading);
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
