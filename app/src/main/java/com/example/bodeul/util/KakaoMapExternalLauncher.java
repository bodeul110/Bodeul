package com.example.bodeul.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/**
 * 카카오 지도 앱과 모바일웹을 우선 사용하고, 실패하면 일반 지도 검색으로 내려가는 외부 지도 실행기다.
 */
public final class KakaoMapExternalLauncher {
    private KakaoMapExternalLauncher() {
    }

    public static boolean openSearch(Context context, String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }

        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "kakaomap://search?q=" + Uri.encode(query)
        )))) {
            return true;
        }

        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "http://m.map.kakao.com/scheme/search?q=" + Uri.encode(query)
        )))) {
            return true;
        }

        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://map.kakao.com/link/search/" + Uri.encode(query)
        )))) {
            return true;
        }

        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "geo:0,0?q=" + Uri.encode(query)
        )))) {
            return true;
        }

        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query)
        )))) {
            return true;
        }

        return tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/search?q=" + Uri.encode(query)
        )));
    }

    private static boolean tryStart(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }
}
