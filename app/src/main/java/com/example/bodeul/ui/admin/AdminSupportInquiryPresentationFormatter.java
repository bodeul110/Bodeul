package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.UserRole;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 관리자 문의 응답 섹션의 문구와 상태 색상 규칙을 담당한다.
 */
public final class AdminSupportInquiryPresentationFormatter {
    private final Context context;
    private final SimpleDateFormat timestampFormatter;

    public AdminSupportInquiryPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
    }

    public String buildSummary(int totalCount, int waitingCount, int answeredCount) {
        if (totalCount == 0) {
            return context.getString(R.string.admin_support_summary_empty);
        }
        return context.getString(
                R.string.admin_support_summary,
                totalCount,
                waitingCount,
                answeredCount
        );
    }

    public String getFilterText(AdminSupportSourceFilter filter) {
        switch (filter) {
            case MANAGER:
                return context.getString(R.string.admin_support_filter_manager);
            case CLIENT:
                return context.getString(R.string.admin_support_filter_client);
            case ALL:
            default:
                return context.getString(R.string.admin_support_filter_all);
        }
    }

    public String getFilterText(AdminSupportStatusFilter filter) {
        switch (filter) {
            case WAITING:
                return context.getString(R.string.admin_support_filter_waiting);
            case ANSWERED:
                return context.getString(R.string.admin_support_filter_answered);
            case ALL:
            default:
                return context.getString(R.string.admin_support_filter_all);
        }
    }

    public String toCategoryText(SupportInquiryCategory category) {
        switch (category) {
            case DOCUMENT:
                return context.getString(R.string.manager_support_category_document);
            case SETTLEMENT:
                return context.getString(R.string.manager_support_category_settlement);
            case OTHER:
                return context.getString(R.string.manager_support_category_other);
            case MATCHING:
            default:
                return context.getString(R.string.manager_support_category_matching);
        }
    }

    public String toStatusText(SupportInquiryStatus status) {
        if (status == SupportInquiryStatus.ANSWERED) {
            return context.getString(R.string.admin_support_status_answered);
        }
        return context.getString(R.string.admin_support_status_received);
    }

    public int getStatusBackgroundColorResId(SupportInquiryStatus status) {
        return status == SupportInquiryStatus.ANSWERED
                ? R.color.bodeul_soft_green
                : R.color.bodeul_soft_blue;
    }

    public int getStatusTextColorResId(SupportInquiryStatus status) {
        return status == SupportInquiryStatus.ANSWERED
                ? R.color.bodeul_success
                : R.color.bodeul_primary;
    }

    public String buildManagerText(SupportInquiry inquiry) {
        return context.getString(
                R.string.admin_support_manager_value,
                TextUtils.isEmpty(inquiry.getManagerName())
                        ? context.getString(R.string.admin_manager_pending)
                        : inquiry.getManagerName()
        );
    }

    public String buildClientText(ClientSupportRequest request) {
        String roleText = request.getUserRole() == UserRole.GUARDIAN
                ? context.getString(R.string.login_role_guardian)
                : context.getString(R.string.login_role_patient);
        return context.getString(
                R.string.admin_support_client_value,
                roleText,
                TextUtils.isEmpty(request.getUserName())
                        ? context.getString(R.string.admin_manager_pending)
                        : request.getUserName()
        );
    }

    public String summarize(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }

    public String formatTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return context.getString(R.string.admin_operation_action_pending);
        }
        return timestampFormatter.format(new Date(timestampMillis));
    }

    public String buildResponseMeta(SupportInquiry inquiry) {
        return context.getString(
                R.string.admin_support_response_meta,
                formatTimestamp(inquiry.getRespondedAtMillis()),
                TextUtils.isEmpty(inquiry.getRespondedByName())
                        ? context.getString(R.string.admin_manager_pending)
                        : inquiry.getRespondedByName()
        );
    }

    public String buildActionButtonText(SupportInquiryStatus status) {
        return status == SupportInquiryStatus.ANSWERED
                ? context.getString(R.string.admin_support_action_edit)
                : context.getString(R.string.admin_support_action_reply);
    }

    public String toCategoryText(ClientSupportCategory category) {
        switch (category) {
            case PROGRESS:
                return context.getString(R.string.client_support_category_progress);
            case REPORT:
                return context.getString(R.string.client_support_category_report);
            case SETTLEMENT:
                return context.getString(R.string.client_support_category_settlement);
            case OTHER:
                return context.getString(R.string.client_support_category_other);
            case RESERVATION:
            default:
                return context.getString(R.string.client_support_category_reservation);
        }
    }

    public String toStatusText(ClientSupportStatus status) {
        return status == ClientSupportStatus.ANSWERED
                ? context.getString(R.string.admin_support_status_answered)
                : context.getString(R.string.admin_support_status_received);
    }

    public int getStatusBackgroundColorResId(ClientSupportStatus status) {
        return status == ClientSupportStatus.ANSWERED
                ? R.color.bodeul_soft_green
                : R.color.bodeul_soft_blue;
    }

    public int getStatusTextColorResId(ClientSupportStatus status) {
        return status == ClientSupportStatus.ANSWERED
                ? R.color.bodeul_success
                : R.color.bodeul_primary;
    }

    public String buildResponseMeta(ClientSupportRequest request) {
        return context.getString(
                R.string.admin_support_response_meta,
                formatTimestamp(request.getRespondedAtMillis()),
                TextUtils.isEmpty(request.getRespondedByName())
                        ? context.getString(R.string.admin_manager_pending)
                        : request.getRespondedByName()
        );
    }

    public String buildActionButtonText(ClientSupportStatus status) {
        return status == ClientSupportStatus.ANSWERED
                ? context.getString(R.string.admin_support_action_edit)
                : context.getString(R.string.admin_support_action_reply);
    }
}
