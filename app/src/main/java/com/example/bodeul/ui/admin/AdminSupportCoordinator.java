package com.example.bodeul.ui.admin;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
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

    public AdminSupportDashboardModel createDashboardModel(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests,
            AdminSupportFilter filter
    ) {
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
                    AdminSupportInquirySourceType.MANAGER,
                    inquiry.getId(),
                    formatter.toCategoryText(inquiry.getCategory()),
                    formatter.toStatusText(inquiry.getStatus()),
                    formatter.getStatusBackgroundColorResId(inquiry.getStatus()),
                    formatter.getStatusTextColorResId(inquiry.getStatus()),
                    formatter.buildManagerText(inquiry),
                    inquiry.getTitle(),
                    formatter.summarize(inquiry.getBody()),
                    formatter.formatTimestamp(inquiry.getCreatedAtMillis()),
                    inquiry.getCreatedAtMillis(),
                    answered,
                    formatter.summarize(inquiry.getResponseText()),
                    answered ? formatter.buildResponseMeta(inquiry) : "",
                    formatter.buildActionButtonText(inquiry.getStatus())
            ));
        }
        for (ClientSupportRequest request : clientRequests) {
            boolean answered = request.getStatus() == ClientSupportStatus.ANSWERED;
            if (answered) {
                answeredCount++;
            } else {
                waitingCount++;
            }
            cards.add(new AdminSupportInquiryCardModel(
                    AdminSupportInquirySourceType.CLIENT,
                    request.getId(),
                    formatter.toCategoryText(request.getCategory()),
                    formatter.toStatusText(request.getStatus()),
                    formatter.getStatusBackgroundColorResId(request.getStatus()),
                    formatter.getStatusTextColorResId(request.getStatus()),
                    formatter.buildClientText(request),
                    request.getTitle(),
                    formatter.summarize(request.getBody()),
                    formatter.formatTimestamp(request.getCreatedAtMillis()),
                    request.getCreatedAtMillis(),
                    answered,
                    formatter.summarize(request.getResponseText()),
                    answered ? formatter.buildResponseMeta(request) : "",
                    formatter.buildActionButtonText(request.getStatus())
            ));
        }
        List<AdminSupportInquiryCardModel> filteredCards = new ArrayList<>();
        for (AdminSupportInquiryCardModel card : cards) {
            if (filter == AdminSupportFilter.WAITING && card.isShowResponse()) {
                continue;
            }
            if (filter == AdminSupportFilter.ANSWERED && !card.isShowResponse()) {
                continue;
            }
            if (filter == AdminSupportFilter.MANAGER
                    && card.getSourceType() != AdminSupportInquirySourceType.MANAGER) {
                continue;
            }
            if (filter == AdminSupportFilter.CLIENT
                    && card.getSourceType() != AdminSupportInquirySourceType.CLIENT) {
                continue;
            }
            filteredCards.add(card);
        }
        filteredCards.sort((left, right) ->
                Long.compare(right.getSortTimestampMillis(), left.getSortTimestampMillis()));
        return new AdminSupportDashboardModel(
                formatter.buildSummary(inquiries.size() + clientRequests.size(), waitingCount, answeredCount),
                createFilterChips(inquiries, clientRequests, filter),
                filteredCards
        );
    }

    private List<AdminSupportFilterChipModel> createFilterChips(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests,
            AdminSupportFilter selectedFilter
    ) {
        List<AdminSupportFilterChipModel> chips = new ArrayList<>();
        chips.add(new AdminSupportFilterChipModel(
                AdminSupportFilter.ALL,
                formatter.getFilterText(AdminSupportFilter.ALL),
                selectedFilter == AdminSupportFilter.ALL
        ));
        if (waitingCount(inquiries, clientRequests) > 0) {
            chips.add(new AdminSupportFilterChipModel(
                    AdminSupportFilter.WAITING,
                    formatter.getFilterText(AdminSupportFilter.WAITING),
                    selectedFilter == AdminSupportFilter.WAITING
            ));
        }
        if (answeredCount(inquiries, clientRequests) > 0) {
            chips.add(new AdminSupportFilterChipModel(
                    AdminSupportFilter.ANSWERED,
                    formatter.getFilterText(AdminSupportFilter.ANSWERED),
                    selectedFilter == AdminSupportFilter.ANSWERED
            ));
        }
        if (!inquiries.isEmpty()) {
            chips.add(new AdminSupportFilterChipModel(
                    AdminSupportFilter.MANAGER,
                    formatter.getFilterText(AdminSupportFilter.MANAGER),
                    selectedFilter == AdminSupportFilter.MANAGER
            ));
        }
        if (!clientRequests.isEmpty()) {
            chips.add(new AdminSupportFilterChipModel(
                    AdminSupportFilter.CLIENT,
                    formatter.getFilterText(AdminSupportFilter.CLIENT),
                    selectedFilter == AdminSupportFilter.CLIENT
            ));
        }
        return chips;
    }

    private int waitingCount(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests
    ) {
        int count = 0;
        for (SupportInquiry inquiry : inquiries) {
            if (inquiry.getStatus() != SupportInquiryStatus.ANSWERED) {
                count++;
            }
        }
        for (ClientSupportRequest request : clientRequests) {
            if (request.getStatus() != ClientSupportStatus.ANSWERED) {
                count++;
            }
        }
        return count;
    }

    private int answeredCount(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests
    ) {
        int count = 0;
        for (SupportInquiry inquiry : inquiries) {
            if (inquiry.getStatus() == SupportInquiryStatus.ANSWERED) {
                count++;
            }
        }
        for (ClientSupportRequest request : clientRequests) {
            if (request.getStatus() == ClientSupportStatus.ANSWERED) {
                count++;
            }
        }
        return count;
    }
}
