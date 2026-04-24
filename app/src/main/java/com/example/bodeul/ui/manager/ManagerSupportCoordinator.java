package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 문의 저장 목록을 지원 화면 표현 모델로 변환한다.
 */
public final class ManagerSupportCoordinator {
    private final Context context;
    private final ManagerHomePresentationFormatter formatter;

    public ManagerSupportCoordinator(Context context, ManagerHomePresentationFormatter formatter) {
        this.context = context;
        this.formatter = formatter;
    }

    public ManagerSupportScreenModel createScreenModel(
            List<SupportInquiry> inquiries,
            boolean firebaseBacked
    ) {
        return new ManagerSupportScreenModel(
                context.getString(firebaseBacked
                        ? R.string.manager_home_mode_firebase
                        : R.string.manager_home_mode_demo),
                context.getString(R.string.manager_support_hero_badge),
                context.getString(R.string.manager_support_hero_title),
                context.getString(firebaseBacked
                        ? R.string.manager_support_hero_body_firebase
                        : R.string.manager_support_hero_body_demo),
                buildLatestSummary(inquiries),
                createInquiryCards(inquiries)
        );
    }

    private String buildLatestSummary(List<SupportInquiry> inquiries) {
        if (inquiries.isEmpty()) {
            return context.getString(R.string.manager_support_latest_empty);
        }

        SupportInquiry latestInquiry = inquiries.get(0);
        if (latestInquiry.getStatus() == SupportInquiryStatus.ANSWERED) {
            return context.getString(
                    R.string.manager_support_latest_answered_value,
                    toCategoryText(latestInquiry.getCategory()),
                    formatter.formatTimestamp(latestInquiry.getRespondedAtMillis())
            );
        }
        return context.getString(
                R.string.manager_support_latest_value,
                toCategoryText(latestInquiry.getCategory()),
                formatter.formatTimestamp(latestInquiry.getCreatedAtMillis())
        );
    }

    private List<ManagerSupportInquiryCardModel> createInquiryCards(List<SupportInquiry> inquiries) {
        List<ManagerSupportInquiryCardModel> cards = new ArrayList<>();
        for (SupportInquiry inquiry : inquiries) {
            boolean answered = inquiry.getStatus() == SupportInquiryStatus.ANSWERED
                    && !TextUtils.isEmpty(inquiry.getResponseText());
            cards.add(new ManagerSupportInquiryCardModel(
                    toCategoryText(inquiry.getCategory()),
                    answered
                            ? context.getString(R.string.manager_support_status_answered)
                            : context.getString(R.string.manager_support_status_received),
                    answered ? R.color.bodeul_soft_green : R.color.bodeul_soft_blue,
                    answered ? R.color.bodeul_success : R.color.bodeul_primary,
                    inquiry.getTitle(),
                    formatter.summarizeCardText(inquiry.getBody()),
                    formatter.formatTimestamp(inquiry.getCreatedAtMillis()),
                    answered,
                    formatter.summarizeCardText(inquiry.getResponseText()),
                    buildResponseMeta(inquiry)
            ));
        }
        return cards;
    }

    private String buildResponseMeta(SupportInquiry inquiry) {
        if (inquiry.getStatus() != SupportInquiryStatus.ANSWERED) {
            return "";
        }
        return context.getString(
                R.string.manager_support_response_meta,
                formatter.formatTimestamp(inquiry.getRespondedAtMillis()),
                TextUtils.isEmpty(inquiry.getRespondedByName())
                        ? context.getString(R.string.admin_manager_pending)
                        : inquiry.getRespondedByName()
        );
    }

    private String toCategoryText(SupportInquiryCategory category) {
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
}
