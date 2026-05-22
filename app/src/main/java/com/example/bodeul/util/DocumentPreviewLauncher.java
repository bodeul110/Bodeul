package com.example.bodeul.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/**
 * 외부 앱이나 브라우저로 문서 미리보기를 연다.
 */
public final class DocumentPreviewLauncher {
    private DocumentPreviewLauncher() {
    }

    public static boolean open(Context context, Uri previewUri, String contentType) {
        if (context == null || previewUri == null) {
            return false;
        }

        Intent typedIntent = new Intent(Intent.ACTION_VIEW);
        typedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (TextUtils.isEmpty(contentType)) {
            typedIntent.setData(previewUri);
        } else {
            typedIntent.setDataAndType(previewUri, contentType);
        }
        if (tryStart(context, typedIntent)) {
            return true;
        }

        if (TextUtils.isEmpty(contentType)) {
            return false;
        }

        Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, previewUri);
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return tryStart(context, fallbackIntent);
    }

    private static boolean tryStart(Context context, Intent intent) {
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
