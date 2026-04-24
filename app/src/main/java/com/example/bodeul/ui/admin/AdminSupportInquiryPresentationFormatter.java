package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;

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
}
