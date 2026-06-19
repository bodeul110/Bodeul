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
            AdminSupportSourceFilter sourceFilter,
            AdminSupportStatusFilter statusFilter
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
            if (statusFilter == AdminSupportStatusFilter.WAITING && card.isShowResponse()) {
                continue;
            }
            if (statusFilter == AdminSupportStatusFilter.ANSWERED && !card.isShowResponse()) {
                continue;
            }
            if (sourceFilter == AdminSupportSourceFilter.MANAGER
                    && card.getSourceType() != AdminSupportInquirySourceType.MANAGER) {
                continue;
            }
            if (sourceFilter == AdminSupportSourceFilter.CLIENT
                    && card.getSourceType() != AdminSupportInquirySourceType.CLIENT) {
                continue;
            }
            filteredCards.add(card);
        }
        filteredCards.sort((left, right) ->
                Long.compare(right.getSortTimestampMillis(), left.getSortTimestampMillis()));
        return new AdminSupportDashboardModel(
                formatter.buildSummary(inquiries.size() + clientRequests.size(), waitingCount, answeredCount),
                createSourceFilterChips(inquiries, clientRequests, sourceFilter),
                createStatusFilterChips(inquiries, clientRequests, statusFilter),
                filteredCards
        );
    }

    private List<AdminSupportSourceFilterChipModel> createSourceFilterChips(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests,
            AdminSupportSourceFilter selectedFilter
    ) {
        List<AdminSupportSourceFilterChipModel> chips = new ArrayList<>();
        chips.add(new AdminSupportSourceFilterChipModel(
                AdminSupportSourceFilter.ALL,
                formatter.getFilterText(AdminSupportSourceFilter.ALL),
                selectedFilter == AdminSupportSourceFilter.ALL
        ));
        if (!inquiries.isEmpty()) {
            chips.add(new AdminSupportSourceFilterChipModel(
                    AdminSupportSourceFilter.MANAGER,
                    formatter.getFilterText(AdminSupportSourceFilter.MANAGER),
                    selectedFilter == AdminSupportSourceFilter.MANAGER
            ));
        }
        if (!clientRequests.isEmpty()) {
            chips.add(new AdminSupportSourceFilterChipModel(
                    AdminSupportSourceFilter.CLIENT,
                    formatter.getFilterText(AdminSupportSourceFilter.CLIENT),
                    selectedFilter == AdminSupportSourceFilter.CLIENT
            ));
        }
        return chips;
    }

    private List<AdminSupportStatusFilterChipModel> createStatusFilterChips(
            List<SupportInquiry> inquiries,
            List<ClientSupportRequest> clientRequests,
            AdminSupportStatusFilter selectedFilter
    ) {
        List<AdminSupportStatusFilterChipModel> chips = new ArrayList<>();
        chips.add(new AdminSupportStatusFilterChipModel(
                AdminSupportStatusFilter.ALL,
                formatter.getFilterText(AdminSupportStatusFilter.ALL),
                selectedFilter == AdminSupportStatusFilter.ALL
        ));
        if (waitingCount(inquiries, clientRequests) > 0) {
            chips.add(new AdminSupportStatusFilterChipModel(
                    AdminSupportStatusFilter.WAITING,
                    formatter.getFilterText(AdminSupportStatusFilter.WAITING),
                    selectedFilter == AdminSupportStatusFilter.WAITING
            ));
        }
        if (answeredCount(inquiries, clientRequests) > 0) {
            chips.add(new AdminSupportStatusFilterChipModel(
                    AdminSupportStatusFilter.ANSWERED,
                    formatter.getFilterText(AdminSupportStatusFilter.ANSWERED),
                    selectedFilter == AdminSupportStatusFilter.ANSWERED
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
