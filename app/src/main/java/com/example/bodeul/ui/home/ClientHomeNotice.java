package com.example.bodeul.ui.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * 환자/보호자 홈 하단에 노출할 안내 카드 한 장의 정보를 담는다.
 */
public final class ClientHomeNotice {
    @DrawableRes
    private final int bannerBackgroundResId;
    @StringRes
    private final int eyebrowResId;
    @StringRes
    private final int titleResId;
    @StringRes
    private final int bodyResId;

    public ClientHomeNotice(
            @DrawableRes int bannerBackgroundResId,
            @StringRes int eyebrowResId,
            @StringRes int titleResId,
            @StringRes int bodyResId
    ) {
        this.bannerBackgroundResId = bannerBackgroundResId;
        this.eyebrowResId = eyebrowResId;
        this.titleResId = titleResId;
        this.bodyResId = bodyResId;
    }

    public int getBannerBackgroundResId() {
        return bannerBackgroundResId;
    }

    public int getEyebrowResId() {
        return eyebrowResId;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getBodyResId() {
        return bodyResId;
    }
}
