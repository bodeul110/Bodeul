package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 병원 예약에서 만남 위치를 지도 기반으로 고르는 화면이다.
 */
public class BookingLocationSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_HOSPITAL_NAME = "hospitalName";
    private static final String EXTRA_DEPARTMENT_NAME = "departmentName";
    private static final String EXTRA_POINT_ID = "pointId";
    private static final String EXTRA_MEETING_PLACE = "meetingPlace";
    private static final String STATE_SELECTED_POINT_ID = "selectedPointId";

    private BookingLocationOptionAdapter optionAdapter;
    @Nullable
    private BookingMeetingPointOption selectedPointOption;

    public static Intent createIntent(
            Context context,
            BookingHospitalSelection hospitalSelection,
            BookingMeetingLocationSelection currentSelection
    ) {
        Intent intent = new Intent(context, BookingLocationSelectorActivity.class);
        intent.putExtra(EXTRA_HOSPITAL_NAME, hospitalSelection.getHospitalName());
        intent.putExtra(EXTRA_DEPARTMENT_NAME, hospitalSelection.getDepartmentName());
        intent.putExtra(EXTRA_POINT_ID, currentSelection.getPointId());
        intent.putExtra(EXTRA_MEETING_PLACE, currentSelection.getMeetingPlace());
        return intent;
    }

    public static BookingMeetingLocationSelection parseResult(@Nullable Intent data) {
        if (data == null) {
            return new BookingMeetingLocationSelection("", "");
        }
        return new BookingMeetingLocationSelection(
                data.getStringExtra(EXTRA_POINT_ID),
                data.getStringExtra(EXTRA_MEETING_PLACE)
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_location_selector);

        BookingHospitalSelection hospitalSelection = new BookingHospitalSelection(
                getIntent().getStringExtra(EXTRA_HOSPITAL_NAME),
                getIntent().getStringExtra(EXTRA_DEPARTMENT_NAME)
        );
        BookingMeetingLocationSelection currentSelection = new BookingMeetingLocationSelection(
                getIntent().getStringExtra(EXTRA_POINT_ID),
                getIntent().getStringExtra(EXTRA_MEETING_PLACE)
        );
        BookingLocationSelectorScreenModel screenModel = new BookingLocationSelectorCoordinator(this)
                .createScreenModel(hospitalSelection, currentSelection);

        ListView listView = findViewById(R.id.listBookingLocationOptions);
        View headerView = LayoutInflater.from(this).inflate(
                R.layout.header_booking_location_selector,
                listView,
                false
        );
        listView.addHeaderView(headerView, null, false);

        optionAdapter = new BookingLocationOptionAdapter(this);
        BookingLocationMapView mapView = headerView.findViewById(R.id.viewBookingLocationMap);
        MaterialButton confirmButton = findViewById(R.id.buttonBookingLocationConfirm);

        ((TextView) headerView.findViewById(R.id.textBookingLocationBadge)).setText(screenModel.getBadge());
        ((TextView) headerView.findViewById(R.id.textBookingLocationTitle)).setText(screenModel.getTitle());
        ((TextView) headerView.findViewById(R.id.textBookingLocationBody)).setText(screenModel.getBody());
        ((TextView) headerView.findViewById(R.id.textBookingLocationHelper)).setText(screenModel.getHelper());
        headerView.findViewById(R.id.buttonBackBookingLocation).setOnClickListener(view -> finish());

        List<BookingMeetingPointOption> pointOptions = screenModel.getPointOptions();
        String candidatePointId = savedInstanceState == null
                ? screenModel.getSelectedPointId()
                : savedInstanceState.getString(
                        STATE_SELECTED_POINT_ID,
                        screenModel.getSelectedPointId()
                );
        selectedPointOption = findSelectedPoint(candidatePointId, pointOptions);
        String selectedPointId = selectedPointOption == null ? "" : selectedPointOption.getId();
        optionAdapter.submitList(pointOptions);
        optionAdapter.setSelectedPointId(selectedPointId);
        listView.setAdapter(optionAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            int adapterPosition = position - listView.getHeaderViewsCount();
            if (adapterPosition >= 0 && adapterPosition < optionAdapter.getCount()) {
                selectPoint(optionAdapter.getItem(adapterPosition), mapView);
            }
        });

        mapView.setPointOptions(pointOptions);
        mapView.setSelectedPointId(selectedPointId);
        mapView.setOnPointSelectedListener(option -> selectPoint(option, mapView));

        confirmButton.setOnClickListener(view -> finishWithSelection());
        configureSystemBarInsets(
                listView,
                findViewById(R.id.bookingLocationBottomAction)
        );
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(
                STATE_SELECTED_POINT_ID,
                selectedPointOption == null ? "" : selectedPointOption.getId()
        );
    }

    private void selectPoint(BookingMeetingPointOption option, BookingLocationMapView mapView) {
        selectedPointOption = option;
        optionAdapter.setSelectedPointId(option.getId());
        mapView.setSelectedPointId(option.getId());
    }

    @Nullable
    private BookingMeetingPointOption findSelectedPoint(
            String selectedPointId,
            List<BookingMeetingPointOption> pointOptions
    ) {
        for (BookingMeetingPointOption option : pointOptions) {
            if (option.getId().equals(selectedPointId)) {
                return option;
            }
        }
        return pointOptions.isEmpty() ? null : pointOptions.get(0);
    }

    private void finishWithSelection() {
        if (selectedPointOption == null) {
            return;
        }
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_POINT_ID, selectedPointOption.getId());
        resultIntent.putExtra(EXTRA_MEETING_PLACE, selectedPointOption.getMeetingPlace());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void configureSystemBarInsets(ListView listView, View bottomAction) {
        int listLeft = listView.getPaddingLeft();
        int listTop = listView.getPaddingTop();
        int listRight = listView.getPaddingRight();
        int listBottom = listView.getPaddingBottom();
        int actionLeft = bottomAction.getPaddingLeft();
        int actionTop = bottomAction.getPaddingTop();
        int actionRight = bottomAction.getPaddingRight();
        int actionBottom = bottomAction.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(listView, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    listLeft + systemInsets.left,
                    listTop + systemInsets.top,
                    listRight + systemInsets.right,
                    listBottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomAction, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    actionLeft + systemInsets.left,
                    actionTop,
                    actionRight + systemInsets.right,
                    actionBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(listView);
        ViewCompat.requestApplyInsets(bottomAction);
    }
}
