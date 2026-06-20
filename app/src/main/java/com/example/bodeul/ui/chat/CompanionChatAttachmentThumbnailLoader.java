package com.example.bodeul.ui.chat;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 안심 채팅 첨부 이미지의 썸네일을 로컬 URI와 다운로드 URL 모두에서 안전하게 불러온다.
 */
public final class CompanionChatAttachmentThumbnailLoader {
    private static final int MAX_PREVIEW_SIZE = 720;

    private final ContentResolver contentResolver;
    private final ExecutorService executorService;

    public CompanionChatAttachmentThumbnailLoader(@NonNull Context context) {
        this.contentResolver = context.getApplicationContext().getContentResolver();
        this.executorService = Executors.newCachedThreadPool();
    }

    public void loadInto(@NonNull ImageView imageView, @Nullable Uri previewUri) {
        if (previewUri == null) {
            clear(imageView);
            return;
        }

        String requestKey = previewUri.toString();
        imageView.setTag(requestKey);
        imageView.setImageDrawable(null);

        executorService.execute(() -> {
            Bitmap bitmap = decodeBitmap(previewUri);
            imageView.post(() -> {
                Object currentTag = imageView.getTag();
                if (!TextUtils.equals(requestKey, currentTag instanceof String ? (String) currentTag : "")) {
                    return;
                }
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else {
                    imageView.setImageDrawable(null);
                }
            });
        });
    }

    public void clear(@NonNull ImageView imageView) {
        imageView.setTag(null);
        imageView.setImageDrawable(null);
    }

    @Nullable
    private Bitmap decodeBitmap(@NonNull Uri previewUri) {
        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            try (InputStream inputStream = openInputStream(previewUri)) {
                if (inputStream == null) {
                    return null;
                }
                BitmapFactory.decodeStream(inputStream, null, boundsOptions);
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions);
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream inputStream = openInputStream(previewUri)) {
                if (inputStream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(inputStream, null, decodeOptions);
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private InputStream openInputStream(@NonNull Uri previewUri) throws IOException {
        String scheme = previewUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            HttpURLConnection connection = (HttpURLConnection) new URL(previewUri.toString()).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoInput(true);
            connection.connect();
            return connection.getInputStream();
        }
        return contentResolver.openInputStream(previewUri);
    }

    private int calculateInSampleSize(@NonNull BitmapFactory.Options boundsOptions) {
        int width = Math.max(boundsOptions.outWidth, 1);
        int height = Math.max(boundsOptions.outHeight, 1);
        int sampleSize = 1;
        while ((width / sampleSize) > MAX_PREVIEW_SIZE || (height / sampleSize) > MAX_PREVIEW_SIZE) {
            sampleSize *= 2;
        }
        return Math.max(sampleSize, 1);
    }
}
