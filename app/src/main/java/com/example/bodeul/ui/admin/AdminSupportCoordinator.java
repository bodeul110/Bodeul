package com.example.bodeul.ui.admin;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 문의 응답 섹션에 필요한 카드와 요약을 조합한다.
 */
public final class AdminSupportCoordinator {
    private final AdminSupportInquiryPresentationFormatter formatter;

    public AdminSupportCoordinator(AdminSupportInquiryPresentationFormatter formatter) {
        this.formatter = formatter;
    }

    public AdminSupportDashboardModel createDashboardModel(List<SupportInquiry> inquiries) {
        int waitingCount = 0;
        int answeredCount = 0;
        List<AdminSupportInquiryCardModel> cards = new ArrayList<>();
        for (SupportInquiry inquiry : inquiries) {
            boolean answered = inquiry.getStatus() == SupportInquiryStatus.ANSWERED;
            if (answered) {
                answeredCount++;
            } else {
                waitingCount++;
            }
            cards.add(new AdminSupportInquiryCardModel(
                    inquiry.getId(),
                    formatter.toCategoryText(inquiry.getCategory()),
                    formatter.toStatusText(inquiry.getStatus()),
                    formatter.getStatusBackgroundColorResId(inquiry.getStatus()),
                    formatter.getStatusTextColorResId(inquiry.getStatus()),
                    formatter.buildManagerText(inquiry),
                    inquiry.getTitle(),
                    formatter.summarize(inquiry.getBody()),
                    formatter.formatTimestamp(inquiry.getCreatedAtMillis()),
                    answered,
                    formatter.summarize(inquiry.getResponseText()),
                    answered ? formatter.buildResponseMeta(inquiry) : "",
                    formatter.buildActionButtonText(inquiry.getStatus())
            ));
        }
        return new AdminSupportDashboardModel(
                formatter.buildSummary(inquiries.size(), waitingCount, answeredCount),
                cards
        );
    }
}
