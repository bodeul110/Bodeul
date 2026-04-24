package com.example.bodeul.ui.admin;

import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionDeliveryChannel;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionOverview;
import com.example.bodeul.domain.model.AdminActionDeliveryState;
import com.example.bodeul.domain.model.AdminActionDeliveryStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영 알림 전송 현황 섹션을 화면 모델로 조합한다.
 */
public final class AdminActionDeliveryCoordinator {
    private final AdminActionDeliveryPresentationFormatter formatter;

    public AdminActionDeliveryCoordinator(AdminActionDeliveryPresentationFormatter formatter) {
        this.formatter = formatter;
    }

    public AdminActionDeliveryDashboardModel createDashboardModel(
            List<AdminActionDeliveryRecord> deliveries,
            AdminActionOverview actionOverview
    ) {
        AdminActionOverview resolvedOverview = actionOverview == null
                ? AdminActionContract.createOverview(
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                deliveries
        )
                : actionOverview;
        List<AdminActionDeliveryCardModel> cardModels = new ArrayList<>();
        for (AdminActionDeliveryRecord delivery : deliveries) {
            cardModels.add(new AdminActionDeliveryCardModel(
                    formatter.getChannelText(delivery.getChannel()),
                    resolveChannelTone(delivery.getChannel()),
                    formatter.getStatusText(delivery.getStatus()),
                    resolveStatusTone(delivery.getStatus()),
                    formatter.getStateText(delivery.getState()),
                    resolveStateTone(delivery.getState()),
                    formatter.buildTitle(delivery),
                    formatter.buildBody(delivery),
                    formatter.buildMeta(delivery)
            ));
        }
        return new AdminActionDeliveryDashboardModel(
                formatter.buildSummary(
                        resolvedOverview.getDeliveryCount(),
                        resolvedOverview.getPendingDeliveryCount(),
                        resolvedOverview.getFollowUpDeliveryCount(),
                        resolvedOverview.getCompletedDeliveryCount(),
                        resolvedOverview.getAppPushDeliveryCount(),
                        resolvedOverview.getOperationsFeedDeliveryCount()
                ),
                cardModels
        );
    }

    private AdminActionCenterTone resolveChannelTone(AdminActionDeliveryChannel channel) {
        return channel == AdminActionDeliveryChannel.APP_PUSH
                ? AdminActionCenterTone.PRIMARY
                : AdminActionCenterTone.SUCCESS;
    }

    private AdminActionCenterTone resolveStatusTone(AdminActionDeliveryStatus status) {
        switch (status) {
            case FAILED:
                return AdminActionCenterTone.WARNING;
            case CONFIRMED:
            case SKIPPED:
                return AdminActionCenterTone.SUCCESS;
            case SENT:
            default:
                return AdminActionCenterTone.PRIMARY;
        }
    }

    private AdminActionCenterTone resolveStateTone(AdminActionDeliveryState state) {
        switch (state) {
            case FOLLOW_UP_REQUIRED:
                return AdminActionCenterTone.WARNING;
            case DELIVERED:
            case SKIPPED:
                return AdminActionCenterTone.SUCCESS;
            case PENDING_CONFIRMATION:
            default:
                return AdminActionCenterTone.PRIMARY;
        }
    }
}
