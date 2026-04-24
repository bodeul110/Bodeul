package com.example.bodeul.ui.admin;

/**
 * 운영 알림 전송 현황 카드 한 건을 표현한다.
 */
public final class AdminActionDeliveryCardModel {
    private final String channelText;
    private final AdminActionCenterTone channelTone;
    private final String statusText;
    private final AdminActionCenterTone statusTone;
    private final String stateText;
    private final AdminActionCenterTone stateTone;
    private final String titleText;
    private final String bodyText;
    private final String metaText;

    public AdminActionDeliveryCardModel(
            String channelText,
            AdminActionCenterTone channelTone,
            String statusText,
            AdminActionCenterTone statusTone,
            String stateText,
            AdminActionCenterTone stateTone,
            String titleText,
            String bodyText,
            String metaText
    ) {
        this.channelText = channelText == null ? "" : channelText;
        this.channelTone = channelTone == null ? AdminActionCenterTone.PRIMARY : channelTone;
        this.statusText = statusText == null ? "" : statusText;
        this.statusTone = statusTone == null ? AdminActionCenterTone.PRIMARY : statusTone;
        this.stateText = stateText == null ? "" : stateText;
        this.stateTone = stateTone == null ? AdminActionCenterTone.PRIMARY : stateTone;
        this.titleText = titleText == null ? "" : titleText;
        this.bodyText = bodyText == null ? "" : bodyText;
        this.metaText = metaText == null ? "" : metaText;
    }

    public String getChannelText() {
        return channelText;
    }

    public AdminActionCenterTone getChannelTone() {
        return channelTone;
    }

    public String getStatusText() {
        return statusText;
    }

    public AdminActionCenterTone getStatusTone() {
        return statusTone;
    }

    public String getStateText() {
        return stateText;
    }

    public AdminActionCenterTone getStateTone() {
        return stateTone;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getMetaText() {
        return metaText;
    }
}
