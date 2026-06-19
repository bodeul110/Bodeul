package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.PharmacyProgressDisplayHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 실시간 위치 확인 화면에 필요한 표시 모델을 조합한다.
 */
public final class BookingLiveLocationCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BookingLiveLocationCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public BookingLiveLocationScreenModel createScreenModel(
            User currentUser,
            AppointmentRequestDetail detail,
            boolean isFirebaseBacked
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        CompanionSession session = detail.getSession();
        return new BookingLiveLocationScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.booking_live_location_title),
                context.getString(currentUser.getRole() == UserRole.GUARDIAN
                        ? R.string.booking_live_location_subtitle_guardian
                        : R.string.booking_live_location_subtitle_patient),
                resolveHeroBadge(request, session),
                context.getString(R.string.booking_live_location_hero_title, request.getHospitalName()),
                buildHeroBody(request, session),
                context.getString(R.string.booking_live_location_section_status),
                context.getString(R.string.booking_live_location_section_memo),
                context.getString(R.string.booking_live_location_section_map),
                context.getString(R.string.booking_live_location_section_map_helper),
                buildMapHighlightTitle(detail),
                buildMapHighlightBody(detail),
                createStatusLines(detail),
                createMemoLines(detail),
                createMapActions(detail),
                context.getString(R.string.booking_live_location_action_open_booking_status),
                context.getString(R.string.booking_live_location_action_refresh)
        );
    }

    private String resolveHeroBadge(AppointmentRequest request, @Nullable CompanionSession session) {
        if (session != null) {
            return formatter.toSessionStatusLabel(session.getStatus());
        }
        return formatter.toStatusLabel(request.getStatus());
    }

    private String buildHeroBody(AppointmentRequest request, @Nullable CompanionSession session) {
        String meetingPlace = resolveMeetingPlace(request);
        String locationSummary = session == null ? "" : session.getLocationSummary();
        if (!TextUtils.isEmpty(locationSummary)) {
            return context.getString(
                    R.string.booking_live_location_hero_body_with_location_format,
                    request.getAppointmentAt(),
                    meetingPlace,
                    locationSummary
            );
        }
        return context.getString(
                R.string.booking_live_location_hero_body_format,
                request.getAppointmentAt(),
                meetingPlace
        );
    }

    private List<BookingStatusLineItem> createStatusLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        CompanionSession session = detail.getSession();
        HospitalGuide guide = detail.getHospitalGuide();
        List<BookingStatusLineItem> items = new ArrayList<>();

        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_session_status),
                session == null
                        ? context.getString(R.string.booking_live_location_value_pending)
                        : formatter.toSessionStatusLabel(session.getStatus()),
                true
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_step),
                buildStepValue(session, guide),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_manager),
                buildManagerValue(detail),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_hospital),
                context.getString(
                        R.string.health_info_hospital_value,
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_schedule),
                request.getAppointmentAt(),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_meeting_place),
                resolveMeetingPlace(request),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_location),
                fallbackValue(
                        session == null ? null : session.getLocationSummary(),
                        R.string.booking_live_location_value_pending
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_live_status),
                CompanionLocationDisplayHelper.buildLiveSharingStatus(context, session),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_location_history),
                CompanionLocationDisplayHelper.buildLocationHistory(context, session, 3),
                false
        ));
        if (session != null && session.getSharedLocationUpdatedAtMillis() > 0L) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_live_location_line_location_updated_at),
                    formatSharedLocationTime(session.getSharedLocationUpdatedAtMillis()),
                    false
            ));
        }
        return items;
    }

    private List<BookingStatusLineItem> createMemoLines(AppointmentRequestDetail detail) {
        CompanionSession session = detail.getSession();
        List<BookingStatusLineItem> items = new ArrayList<>();
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_guardian_update),
                fallbackValue(
                        session == null ? null : session.getGuardianUpdate(),
                        R.string.booking_live_location_value_pending
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_photo_note),
                fallbackValue(
                        session == null ? null : session.getFieldPhotoNote(),
                        R.string.booking_live_location_value_pending
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_medication_note),
                fallbackValue(
                        session == null ? null : session.getMedicationNote(),
                        R.string.booking_live_location_value_pending
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_pharmacy_summary),
                fallbackValue(
                        session == null ? null : session.getPharmacySummary(),
                        R.string.booking_live_location_value_pending
                ),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_pharmacy_state),
                PharmacyProgressDisplayHelper.buildOverallStateLabel(context, session),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_live_location_line_pharmacy_detail),
                PharmacyProgressDisplayHelper.buildStepSummary(context, session),
                false
        ));
        return items;
    }

    private List<BookingLiveLocationMapActionModel> createMapActions(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        CompanionSession session = detail.getSession();
        List<BookingLiveLocationMapActionModel> items = new ArrayList<>();

        String meetingPlace = resolveMeetingPlace(request);
        String hospitalQuery = request.getHospitalName() + " " + request.getDepartmentName();
        String liveQuery = !TextUtils.isEmpty(session == null ? null : session.getLocationSummary())
                ? request.getHospitalName() + " " + session.getLocationSummary()
                : request.getHospitalName() + " " + meetingPlace;
        String sharedDirectUrl = buildSharedLocationDirectUrl(session);

        items.add(new BookingLiveLocationMapActionModel(
                context.getString(R.string.booking_live_location_map_shared_title),
                context.getString(
                        R.string.booking_live_location_map_shared_body,
                        fallbackValue(session == null ? null : session.getLocationSummary(), meetingPlace)
                ),
                context.getString(R.string.booking_live_location_map_shared_button),
                liveQuery,
                sharedDirectUrl
        ));
        items.add(new BookingLiveLocationMapActionModel(
                context.getString(R.string.booking_live_location_map_meeting_title),
                context.getString(R.string.booking_live_location_map_meeting_body, meetingPlace),
                context.getString(R.string.booking_live_location_map_meeting_button),
                request.getHospitalName() + " " + meetingPlace,
                null
        ));
        items.add(new BookingLiveLocationMapActionModel(
                context.getString(R.string.booking_live_location_map_hospital_title),
                context.getString(
                        R.string.booking_live_location_map_hospital_body,
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                context.getString(R.string.booking_live_location_map_hospital_button),
                hospitalQuery,
                null
        ));
        items.add(new BookingLiveLocationMapActionModel(
                context.getString(R.string.booking_live_location_map_pharmacy_title),
                context.getString(R.string.booking_live_location_map_pharmacy_body, request.getHospitalName()),
                context.getString(R.string.booking_live_location_map_pharmacy_button),
                request.getHospitalName() + " 약국",
                null
        ));
        return items;
    }

    private String buildMapHighlightTitle(AppointmentRequestDetail detail) {
        CompanionSession session = detail.getSession();
        if (session != null && session.hasSharedLocationCoordinates()) {
            return context.getString(R.string.booking_live_location_map_highlight_title_shared);
        }
        return context.getString(R.string.booking_live_location_map_highlight_title_default);
    }

    private String buildMapHighlightBody(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        CompanionSession session = detail.getSession();
        String locationSummary = fallbackValue(
                session == null ? null : session.getLocationSummary(),
                resolveMeetingPlace(request)
        );
        String updatedAt = session != null && session.getSharedLocationUpdatedAtMillis() > 0L
                ? formatSharedLocationTime(session.getSharedLocationUpdatedAtMillis())
                : context.getString(R.string.booking_live_location_map_highlight_updated_pending);
        return context.getString(
                R.string.booking_live_location_map_highlight_body,
                locationSummary,
                request.getHospitalName(),
                request.getDepartmentName(),
                updatedAt
        );
    }

    private String buildStepValue(@Nullable CompanionSession session, @Nullable HospitalGuide guide) {
        if (session == null) {
            return context.getString(R.string.booking_live_location_value_pending);
        }
        if (guide != null && !guide.getSteps().isEmpty()) {
            return context.getString(
                    R.string.booking_live_location_step_format,
                    session.getCurrentStepOrder(),
                    guide.getSteps().size()
            );
        }
        return context.getString(
                R.string.booking_live_location_step_simple_format,
                session.getCurrentStepOrder()
        );
    }

    private String buildManagerValue(AppointmentRequestDetail detail) {
        if (detail.getManager() == null) {
            return context.getString(R.string.booking_status_manager_pending);
        }
        String name = detail.getManager().getName();
        String phone = detail.getManager().getPhone();
        String email = detail.getManager().getEmail();
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, phone);
        }
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(email)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, email);
        }
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        if (!TextUtils.isEmpty(phone)) {
            return phone;
        }
        if (!TextUtils.isEmpty(email)) {
            return email;
        }
        return context.getString(R.string.booking_status_manager_pending);
    }

    private String resolveMeetingPlace(AppointmentRequest request) {
        if (!TextUtils.isEmpty(request.getMeetingPlace())) {
            return request.getMeetingPlace();
        }
        return context.getString(
                R.string.guide_map_default_meeting_place,
                request.getHospitalName()
        );
    }

    @Nullable
    private String buildSharedLocationDirectUrl(@Nullable CompanionSession session) {
        if (session == null || !session.hasSharedLocationCoordinates()) {
            return null;
        }
        return String.format(
                Locale.US,
                "kakaomap://look?p=%1$.6f,%2$.6f",
                session.getSharedLatitude(),
                session.getSharedLongitude()
        );
    }

    private String fallbackValue(@Nullable String value, int fallbackResId) {
        return TextUtils.isEmpty(value) ? context.getString(fallbackResId) : value;
    }

    private String fallbackValue(@Nullable String value, String fallbackValue) {
        return TextUtils.isEmpty(value) ? fallbackValue : value;
    }

    private String formatSharedLocationTime(long updatedAtMillis) {
        return CompanionLocationDisplayHelper.formatSharedLocationTime(updatedAtMillis);
    }
}
