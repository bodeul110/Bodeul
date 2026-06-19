package com.example.bodeul.ui.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.ui.common.AttentionBannerBinder;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;
import com.example.bodeul.ui.common.AppointmentProgressStageItemBinder;
import com.example.bodeul.ui.common.AppointmentProgressStageModel;
import com.google.android.material.button.MaterialButton;

/**
 * 환자/보호자 홈에 필요한 텍스트와 안내 카드를 대시보드 모델 기준으로 바인딩한다.
 */
public final class ClientHomeDashboardBinder {
    private final Context context;
    private final LayoutInflater layoutInflater;
    private final TextView textGreeting;
    private final TextView textSubtitle;
    private final AttentionBannerBinder supportBannerBinder;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final MaterialButton buttonHeroPrimary;
    private final TextView textProgressBadge;
    private final TextView textProgressTitle;
    private final TextView textProgressBody;
    private final LinearLayout progressStageContainer;
    private final MaterialButton buttonOpenProgress;
    private final TextView textActionSecondaryBadge;
    private final TextView textActionSecondaryCounter;
    private final TextView textActionSecondaryTitle;
    private final TextView textActionSecondaryBody;
    private final TextView textRecentBadge;
    private final TextView textRecentTitle;
    private final TextView textRecentBody;
    private final MaterialButton buttonOpenRecent;
    private final LinearLayout noticeContainer;
    private final AppointmentProgressStageItemBinder stageItemBinder;
    private final BookingPresentationFormatter bookingPresentationFormatter;

    public ClientHomeDashboardBinder(
            Context context,
            LayoutInflater layoutInflater,
            TextView textGreeting,
            TextView textSubtitle,
            View supportBannerView,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            MaterialButton buttonHeroPrimary,
            TextView textProgressBadge,
            TextView textProgressTitle,
            TextView textProgressBody,
            LinearLayout progressStageContainer,
            MaterialButton buttonOpenProgress,
            TextView textActionSecondaryBadge,
            TextView textActionSecondaryCounter,
            TextView textActionSecondaryTitle,
            TextView textActionSecondaryBody,
            TextView textRecentBadge,
            TextView textRecentTitle,
            TextView textRecentBody,
            MaterialButton buttonOpenRecent,
            LinearLayout noticeContainer
    ) {
        this.context = context;
        this.layoutInflater = layoutInflater;
        this.textGreeting = textGreeting;
        this.textSubtitle = textSubtitle;
        this.supportBannerBinder = new AttentionBannerBinder(context, supportBannerView);
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.buttonHeroPrimary = buttonHeroPrimary;
        this.textProgressBadge = textProgressBadge;
        this.textProgressTitle = textProgressTitle;
        this.textProgressBody = textProgressBody;
        this.progressStageContainer = progressStageContainer;
        this.buttonOpenProgress = buttonOpenProgress;
        this.textActionSecondaryBadge = textActionSecondaryBadge;
        this.textActionSecondaryCounter = textActionSecondaryCounter;
        this.textActionSecondaryTitle = textActionSecondaryTitle;
        this.textActionSecondaryBody = textActionSecondaryBody;
        this.textRecentBadge = textRecentBadge;
        this.textRecentTitle = textRecentTitle;
        this.textRecentBody = textRecentBody;
        this.buttonOpenRecent = buttonOpenRecent;
        this.noticeContainer = noticeContainer;
        this.stageItemBinder = new AppointmentProgressStageItemBinder(context);
        this.bookingPresentationFormatter = new BookingPresentationFormatter(context);
    }

    public void bindDashboard(ClientHomeDashboard dashboard) {
        bindGreeting(dashboard);
        supportBannerBinder.bind(dashboard.getSupportBanner());
        bindHero(dashboard);
        bindProgress(dashboard);
        bindSecondaryAction(dashboard);
        bindRecentRequest(dashboard);
        bindNotices(dashboard);
    }

    private void bindGreeting(ClientHomeDashboard dashboard) {
        if (dashboard.isGuardianUser()) {
            textGreeting.setText(context.getString(
                    R.string.client_home_greeting_guardian,
                    dashboard.getUser().getName()
            ));
        } else {
            textGreeting.setText(context.getString(
                    R.string.client_home_greeting_patient,
                    dashboard.getUser().getName()
            ));
        }

        int subtitleResId;
        if (!dashboard.hasRequests()) {
            subtitleResId = R.string.client_home_subtitle_empty;
        } else if (dashboard.hasActiveRequest()) {
            subtitleResId = R.string.client_home_subtitle_active;
        } else {
            subtitleResId = R.string.client_home_subtitle_history;
        }
        textSubtitle.setText(subtitleResId);
    }

    public void setOnSupportBannerClickListener(View.OnClickListener listener) {
        supportBannerBinder.setOnActionClickListener(listener);
    }

    private void bindHero(ClientHomeDashboard dashboard) {
        AppointmentRequest primaryRequest = dashboard.getPrimaryRequest();
        if (primaryRequest == null) {
            textHeroBadge.setText(R.string.client_home_hero_badge_empty);
            textHeroTitle.setText(R.string.client_home_hero_empty_title);
            textHeroBody.setText(R.string.client_home_hero_empty_body);
            buttonHeroPrimary.setText(R.string.client_home_hero_request_button);
            return;
        }

        textHeroBadge.setText(dashboard.hasActiveRequest()
                ? R.string.client_home_hero_badge_active
                : R.string.client_home_hero_badge_history);
        textHeroTitle.setText(context.getString(
                R.string.client_home_hero_active_title,
                primaryRequest.getHospitalName(),
                primaryRequest.getDepartmentName()
        ));
        textHeroBody.setText(buildHeroBody(dashboard, primaryRequest));
        buttonHeroPrimary.setText(dashboard.isGuardianUser()
                ? R.string.client_home_hero_report_button
                : R.string.client_home_hero_progress_button);
    }

    private void bindProgress(ClientHomeDashboard dashboard) {
        AppointmentProgressOverviewModel progressOverview = dashboard.getProgressOverview();
        if (progressOverview == null || dashboard.getPrimaryRequest() == null) {
            textProgressBadge.setText(R.string.client_home_progress_empty_badge);
            tintProgressBadge(R.color.bodeul_soft_blue, R.color.bodeul_primary);
            textProgressTitle.setText(R.string.client_home_progress_empty_title);
            textProgressBody.setText(R.string.client_home_progress_empty_body);
            progressStageContainer.removeAllViews();
            buttonOpenProgress.setText(R.string.client_home_hero_request_button);
            return;
        }

        textProgressBadge.setText(progressOverview.getBadgeText());
        tintProgressBadge(
                resolveRecentBadgeBackground(dashboard.getPrimaryRequest().getStatus()),
                resolveRecentBadgeTextColor(dashboard.getPrimaryRequest().getStatus())
        );
        textProgressTitle.setText(progressOverview.getTitleText());
        textProgressBody.setText(progressOverview.getBodyText());
        bindProgressStages(progressOverview.getStages());
        buttonOpenProgress.setText(dashboard.isGuardianUser()
                ? R.string.client_home_hero_report_button
                : R.string.client_home_progress_detail_button);
    }

    private CharSequence buildHeroBody(ClientHomeDashboard dashboard, AppointmentRequest primaryRequest) {
        String recentUpdate = buildHomeUpdateLine(dashboard, primaryRequest);
        return context.getString(
                R.string.client_home_hero_active_body,
                toStatusLabel(primaryRequest.getStatus()),
                primaryRequest.getAppointmentAt(),
                TextUtils.isEmpty(primaryRequest.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing)
                        : primaryRequest.getMeetingPlace(),
                recentUpdate
        );
    }

    private void bindSecondaryAction(ClientHomeDashboard dashboard) {
        bindSecondaryBadge(dashboard);
        textActionSecondaryTitle.setText(R.string.client_home_action_manage_title);
        String body = context.getString(
                dashboard.isGuardianUser()
                        ? R.string.client_home_action_manage_body_guardian
                        : R.string.client_home_action_manage_body_patient,
                dashboard.getRequestCount(),
                dashboard.getActiveRequestCount(),
                dashboard.getCompletedRequestCount()
        );
        if (dashboard.hasUnreadSupportResponses()) {
            body = body + "\n\n" + context.getString(
                    R.string.client_home_action_manage_support_unread,
                    dashboard.getUnreadSupportResponseCount()
            );
        }
        if (dashboard.hasStaleUnreadSupportResponses()) {
            body = body + "\n" + context.getString(
                    R.string.client_home_action_manage_support_overdue,
                    dashboard.getStaleUnreadSupportResponseCount()
            );
        }
        textActionSecondaryBody.setText(body);
    }

    private void bindSecondaryBadge(ClientHomeDashboard dashboard) {
        if (dashboard.hasStaleUnreadSupportResponses()) {
            textActionSecondaryBadge.setVisibility(View.VISIBLE);
            textActionSecondaryCounter.setVisibility(View.VISIBLE);
            textActionSecondaryBadge.setText(context.getString(
                    R.string.client_home_action_manage_badge_overdue,
                    dashboard.getStaleUnreadSupportResponseCount()
            ));
            textActionSecondaryCounter.setText(context.getString(
                    R.string.client_home_action_manage_counter_overdue,
                    dashboard.getStaleUnreadSupportResponseCount()
            ));
            ViewCompat.setBackgroundTintList(
                    textActionSecondaryBadge,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bodeul_soft_red))
            );
            textActionSecondaryBadge.setTextColor(ContextCompat.getColor(context, R.color.bodeul_error));
            ViewCompat.setBackgroundTintList(
                    textActionSecondaryCounter,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bodeul_error))
            );
            textActionSecondaryCounter.setTextColor(ContextCompat.getColor(context, R.color.white));
            return;
        }
        if (dashboard.hasUnreadSupportResponses()) {
            textActionSecondaryBadge.setVisibility(View.VISIBLE);
            textActionSecondaryCounter.setVisibility(View.VISIBLE);
            textActionSecondaryBadge.setText(context.getString(
                    R.string.client_home_action_manage_badge_unread,
                    dashboard.getUnreadSupportResponseCount()
            ));
            textActionSecondaryCounter.setText(context.getString(
                    R.string.client_home_action_manage_counter_unread,
                    dashboard.getUnreadSupportResponseCount()
            ));
            ViewCompat.setBackgroundTintList(
                    textActionSecondaryBadge,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bodeul_warning))
            );
            textActionSecondaryBadge.setTextColor(ContextCompat.getColor(context, R.color.bodeul_text_primary));
            ViewCompat.setBackgroundTintList(
                    textActionSecondaryCounter,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bodeul_primary))
            );
            textActionSecondaryCounter.setTextColor(ContextCompat.getColor(context, R.color.white));
            return;
        }
        textActionSecondaryBadge.setVisibility(View.GONE);
        textActionSecondaryCounter.setVisibility(View.GONE);
    }

    private void bindRecentRequest(ClientHomeDashboard dashboard) {
        AppointmentRequest primaryRequest = dashboard.getPrimaryRequest();
        if (primaryRequest == null) {
            textRecentBadge.setText(R.string.client_home_recent_empty_badge);
            tintRecentBadge(R.color.bodeul_soft_blue, R.color.bodeul_primary);
            textRecentTitle.setText(R.string.client_home_recent_empty_title);
            textRecentBody.setText(R.string.client_home_recent_empty_body);
            buttonOpenRecent.setText(R.string.client_home_hero_request_button);
            return;
        }

        textRecentBadge.setText(toStatusLabel(primaryRequest.getStatus()));
        tintRecentBadge(resolveRecentBadgeBackground(primaryRequest.getStatus()), resolveRecentBadgeTextColor(primaryRequest.getStatus()));
        textRecentTitle.setText(context.getString(
                R.string.client_home_recent_request_title,
                primaryRequest.getHospitalName(),
                primaryRequest.getDepartmentName()
        ));
        textRecentBody.setText(context.getString(
                R.string.client_home_recent_request_body,
                primaryRequest.getAppointmentAt(),
                TextUtils.isEmpty(primaryRequest.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing)
                        : primaryRequest.getMeetingPlace(),
                buildRecentStatusLine(dashboard, primaryRequest)
        ));
        buttonOpenRecent.setText(dashboard.isGuardianUser()
                ? R.string.client_home_hero_report_button
                : R.string.client_home_recent_open);
    }

    private String buildRecentStatusLine(ClientHomeDashboard dashboard, AppointmentRequest primaryRequest) {
        if (primaryRequest.getStatus() == AppointmentStatus.COMPLETED
                && dashboard.getPrimaryFollowUpRecord() != null) {
            return bookingPresentationFormatter.buildFollowUpSummary(
                    dashboard.getPrimaryFollowUpRecord()
            );
        }
        GuardianReportEntry highlightEntry = dashboard.getHighlightGuardianEntry();
        if (highlightEntry == null || highlightEntry.getAppointmentRequest().getId().equals(primaryRequest.getId())) {
            if (highlightEntry != null && highlightEntry.getSession() != null) {
                return toSessionStatusLabel(highlightEntry.getSession());
            }
        }
        return toStatusLabel(primaryRequest.getStatus());
    }

    private void tintRecentBadge(int backgroundColorResId, int textColorResId) {
        ViewCompat.setBackgroundTintList(
                textRecentBadge,
                ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColorResId))
        );
        textRecentBadge.setTextColor(ContextCompat.getColor(context, textColorResId));
    }

    private void tintProgressBadge(int backgroundColorResId, int textColorResId) {
        ViewCompat.setBackgroundTintList(
                textProgressBadge,
                ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColorResId))
        );
        textProgressBadge.setTextColor(ContextCompat.getColor(context, textColorResId));
    }

    private int resolveRecentBadgeBackground(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
            case IN_PROGRESS:
                return R.color.bodeul_soft_green;
            case COMPLETED:
                return R.color.bodeul_soft_blue;
            case CANCELED:
                return R.color.bodeul_soft_red;
            case REQUESTED:
            default:
                return R.color.bodeul_soft_yellow;
        }
    }

    private int resolveRecentBadgeTextColor(AppointmentStatus status) {
        switch (status) {
            case CANCELED:
                return R.color.bodeul_error;
            case MATCHED:
            case IN_PROGRESS:
            case COMPLETED:
            case REQUESTED:
            default:
                return R.color.bodeul_text_primary;
        }
    }

    private void bindNotices(ClientHomeDashboard dashboard) {
        noticeContainer.removeAllViews();
        for (ClientHomeNotice notice : dashboard.getNotices()) {
            View itemView = layoutInflater.inflate(R.layout.item_client_home_notice, noticeContainer, false);
            View bannerView = itemView.findViewById(R.id.viewNoticeBanner);
            TextView eyebrowView = itemView.findViewById(R.id.textNoticeEyebrow);
            TextView titleView = itemView.findViewById(R.id.textNoticeTitle);
            TextView bodyView = itemView.findViewById(R.id.textNoticeBody);

            bannerView.setBackgroundResource(notice.getBannerBackgroundResId());
            eyebrowView.setText(notice.getEyebrowResId());
            titleView.setText(notice.getTitleResId());
            bodyView.setText(notice.getBodyResId());
            noticeContainer.addView(itemView);
        }
    }

    private void bindProgressStages(java.util.List<AppointmentProgressStageModel> stages) {
        progressStageContainer.removeAllViews();
        for (AppointmentProgressStageModel stage : stages) {
            View itemView = layoutInflater.inflate(R.layout.item_appointment_progress_stage, progressStageContainer, false);
            stageItemBinder.bind(itemView, stage);
            progressStageContainer.addView(itemView);
        }
    }

    private String buildGuardianUpdateLine(GuardianReportEntry entry) {
        if (entry == null) {
            return context.getString(R.string.client_home_hero_guardian_update_empty);
        }

        CompanionSession session = entry.getSession();
        if (session == null || TextUtils.isEmpty(session.getGuardianUpdate())) {
            return context.getString(R.string.client_home_hero_guardian_update_empty);
        }
        return session.getGuardianUpdate();
    }

    private String buildHomeUpdateLine(ClientHomeDashboard dashboard, AppointmentRequest primaryRequest) {
        if (primaryRequest.getStatus() == AppointmentStatus.COMPLETED
                && dashboard.getPrimaryFollowUpRecord() != null) {
            return bookingPresentationFormatter.buildFollowUpSummary(
                    dashboard.getPrimaryFollowUpRecord()
            );
        }
        if (dashboard.isGuardianUser()) {
            return buildGuardianUpdateLine(dashboard.getHighlightGuardianEntry());
        }
        return context.getString(R.string.client_home_hero_patient_update_empty);
    }

    private String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_requested);
        }
    }

    private String toSessionStatusLabel(CompanionSession session) {
        SessionStatus status = session.getStatus();
        switch (status) {
            case READY:
                return context.getString(R.string.guardian_report_session_ready);
            case WAITING:
                return context.getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return context.getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return context.getString(R.string.guardian_report_session_payment);
            case CANCELED:
                return context.getString(R.string.guardian_report_session_canceled);
            case COMPLETED:
                return context.getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return context.getString(R.string.guardian_report_session_meeting);
        }
    }
}
