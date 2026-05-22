package com.example.bodeul.ui.booking;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/**
 * 지도 API가 준비되기 전까지 외부 지도 앱이나 브라우저 검색으로 위치 확인을 돕는다.
 */
public final class BookingLiveLocationMapFallbackLauncher {
    private BookingLiveLocationMapFallbackLauncher() {
    }

    public static boolean open(Context context, BookingLiveLocationMapActionModel model) {
        if (!TextUtils.isEmpty(model.getDirectUrl())
                && tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(model.getDirectUrl())))) {
            return true;
        }

        String query = model.getQueryText();
        if (TextUtils.isEmpty(query)) {
            return false;
        }

        Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
        if (tryStart(context, new Intent(Intent.ACTION_VIEW, geoUri))) {
            return true;
        }

        Uri mapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query));
        if (tryStart(context, new Intent(Intent.ACTION_VIEW, mapsUri))) {
            return true;
        }

        Uri webUri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query));
        return tryStart(context, new Intent(Intent.ACTION_VIEW, webUri));
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
