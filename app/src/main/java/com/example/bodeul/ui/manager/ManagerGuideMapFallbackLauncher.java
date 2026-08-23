package com.example.bodeul.ui.manager;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.example.bodeul.util.KakaoMapExternalLauncher;

/**
 * 지도 API가 준비되기 전까지 외부 지도 앱이나 브라우저 검색으로 길 안내를 연다.
 */
public final class ManagerGuideMapFallbackLauncher {
    public enum OpenResult {
        OPENED,
        OPENED_WEB_FALLBACK,
        OPENED_APP_STORE,
        FAILED
    }

    private ManagerGuideMapFallbackLauncher() {
    }

    public static OpenResult open(Context context, ManagerGuideMapActionModel model) {
        if (model.isKakaoPlaceSearch()) {
            KakaoMapExternalLauncher.PlaceSearchResult result =
                    KakaoMapExternalLauncher.openPlaceSearch(context);
            switch (result) {
                case KAKAO_APP:
                    return OpenResult.OPENED;
                case MOBILE_WEB:
                    return OpenResult.OPENED_WEB_FALLBACK;
                case APP_STORE:
                    return OpenResult.OPENED_APP_STORE;
                case FAILED:
                default:
                    return OpenResult.FAILED;
            }
        }

        if (!TextUtils.isEmpty(model.getDirectUrl())
                && tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(model.getDirectUrl())))) {
            return OpenResult.OPENED;
        }

        String query = model.getQueryText();
        if (TextUtils.isEmpty(query)) {
            return OpenResult.FAILED;
        }
        return KakaoMapExternalLauncher.openSearch(context, query)
                ? OpenResult.OPENED
                : OpenResult.FAILED;
    }

    private static boolean tryStart(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }
}
