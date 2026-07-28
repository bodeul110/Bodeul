package com.example.bodeul.data.coreapi;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.example.bodeul.data.CompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.firebase.FirebaseCompanionChatAttachmentPreviewResolver;
import com.example.bodeul.domain.model.CompanionChatAttachment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Core API에서 참여 권한을 다시 확인한 첨부 파일만 앱 캐시에 내려받는다.
 */
public final class CoreApiCompanionChatAttachmentPreviewResolver
        implements CompanionChatAttachmentPreviewResolver {
    private static final long CACHE_MAX_AGE_MILLIS = 15L * 60L * 1000L;

    private final Context appContext;
    private final CoreApiAuthenticatedClient authenticatedClient;
    private final FirebaseCompanionChatAttachmentPreviewResolver legacyResolver;

    public CoreApiCompanionChatAttachmentPreviewResolver(Context context) {
        appContext = context.getApplicationContext();
        authenticatedClient = new CoreApiAuthenticatedClient(appContext);
        legacyResolver = new FirebaseCompanionChatAttachmentPreviewResolver();
    }

    @Override
    public void resolvePreviewUri(
            CompanionChatAttachment attachment,
            RepositoryCallback<Uri> callback
    ) {
        if (attachment == null) {
            callback.onError("첨부 파일 정보를 확인하지 못했습니다.");
            return;
        }
        String downloadPath = normalize(attachment.getPreviewUri());
        if (!downloadPath.startsWith("/api/companion-sessions/")) {
            legacyResolver.resolvePreviewUri(attachment, callback);
            return;
        }
        authenticatedClient.execute(
                (idToken, appCheckToken) -> cacheAttachment(
                        downloadPath,
                        attachment,
                        authenticatedClient.requestAttachment(
                                downloadPath,
                                idToken,
                                appCheckToken)),
                callback,
                "첨부 파일을 불러오지 못했습니다.",
                "동행 채팅 첨부 API");
    }

    private Uri cacheAttachment(
            String downloadPath,
            CompanionChatAttachment attachment,
            byte[] content
    ) throws Exception {
        if (content.length <= 0
                || (attachment.getSizeBytes() > 0L && content.length != attachment.getSizeBytes())) {
            throw new IllegalStateException("Attachment size mismatch");
        }
        File cacheDirectory = new File(appContext.getCacheDir(), "companion-chat-attachments");
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            throw new IllegalStateException("Attachment cache is unavailable");
        }
        removeExpiredCacheFiles(cacheDirectory);
        String cacheKey = UUID.nameUUIDFromBytes(
                downloadPath.getBytes(StandardCharsets.UTF_8)).toString();
        File cachedFile = new File(cacheDirectory, cacheKey + extension(attachment.getContentType()));
        try (FileOutputStream outputStream = new FileOutputStream(cachedFile, false)) {
            outputStream.write(content);
        }
        return FileProvider.getUriForFile(
                appContext,
                appContext.getPackageName() + ".fileprovider",
                cachedFile);
    }

    private void removeExpiredCacheFiles(File cacheDirectory) {
        File[] files = cacheDirectory.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MILLIS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                file.delete();
            }
        }
    }

    private String extension(String contentType) {
        return switch (normalize(contentType)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
