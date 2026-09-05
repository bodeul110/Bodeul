package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.google.android.material.card.MaterialCardView;

/** Figma MVP의 상봉 단계 전용 정보 위계를 기존 동행 가이드 화면에 적용한다. */
final class ManagerGuideMeetingOverviewBinder {
    private final View meetingOverview;
    private final View legacySummary;
    private final View legacyFocus;
    private final View guideSubtitle;
    private final View mapHeader;
    private final MaterialCardView mapCard;
    private final View mapView;
    private final TextView patient;
    private final TextView meetingPlace;
    private final TextView department;
    private final float legacyMapRadius;
    private final float meetingMapRadius;
    private final int legacyMapHeight;
    private final int meetingMapHeight;

    ManagerGuideMeetingOverviewBinder(View root) {
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        guideSubtitle = root.findViewById(R.id.textGuideSubtitle);
        mapHeader = root.findViewById(R.id.layoutGuideMapHeader);
        mapCard = root.findViewById(R.id.cardGuideMap);
        mapView = root.findViewById(R.id.mapViewManagerGuide);
        patient = root.findViewById(R.id.textGuideMeetingPatient);
        meetingPlace = root.findViewById(R.id.textGuideMeetingPlace);
        department = root.findViewById(R.id.textGuideMeetingDepartment);
        float density = root.getResources().getDisplayMetrics().density;
        legacyMapRadius = 28f * density;
        meetingMapRadius = 48f * density;
        legacyMapHeight = Math.round(250f * density);
        meetingMapHeight = Math.round(160f * density);
    }

    void bind(ManagerGuideScreenModel model) {
        boolean meetingStep = model.isMeetingStep();
        meetingOverview.setVisibility(meetingStep ? View.VISIBLE : View.GONE);
        legacySummary.setVisibility(meetingStep ? View.GONE : View.VISIBLE);
        legacyFocus.setVisibility(meetingStep ? View.GONE : View.VISIBLE);
        guideSubtitle.setVisibility(meetingStep ? View.GONE : View.VISIBLE);
        mapHeader.setVisibility(meetingStep ? View.GONE : View.VISIBLE);
        mapCard.setRadius(meetingStep ? meetingMapRadius : legacyMapRadius);
        android.view.ViewGroup.LayoutParams mapLayoutParams = mapView.getLayoutParams();
        mapLayoutParams.height = meetingStep ? meetingMapHeight : legacyMapHeight;
        mapView.setLayoutParams(mapLayoutParams);
        if (!meetingStep) {
            return;
        }
        patient.setText(model.getMeetingPatient());
        meetingPlace.setText(model.getMeetingPlace());
        department.setText(model.getMeetingDepartment());
    }
}
