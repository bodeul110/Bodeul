package com.example.bodeul.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 업로드 대상 파일의 크기를 앱 단에서 같은 기준으로 판정한다.
 */
final class UploadFileSizePolicy {
    static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private static final int READ_BUFFER_SIZE = 8 * 1024;

    private UploadFileSizePolicy() {
    }

    static Result validate(ContentResolver resolver, Uri fileUri, long maxFileSizeBytes) {
        long metadataSize = resolveOpenableSize(resolver, fileUri);
        if (metadataSize >= 0L) {
            return fromKnownSize(metadataSize, maxFileSizeBytes);
        }

        long localFileSize = resolveLocalFileSize(fileUri);
        if (localFileSize >= 0L) {
            return fromKnownSize(localFileSize, maxFileSizeBytes);
        }

        return inspectResolverStream(resolver, fileUri, maxFileSizeBytes);
    }

    static Result fromKnownSize(long fileSizeBytes, long maxFileSizeBytes) {
        if (fileSizeBytes < 0L) {
            return Result.unknown();
        }
        if (fileSizeBytes > maxFileSizeBytes) {
            return Result.tooLarge(fileSizeBytes);
        }
        return Result.allowed(fileSizeBytes);
    }

    static Result inspectStream(InputStream inputStream, long maxFileSizeBytes) {
        if (inputStream == null || maxFileSizeBytes < 0L) {
            return Result.unknown();
        }

        byte[] buffer = new byte[READ_BUFFER_SIZE];
        long totalBytes = 0L;
        try {
            while (totalBytes <= maxFileSizeBytes) {
                long bytesToLimit = maxFileSizeBytes - totalBytes + 1L;
                int bytesToRead = (int) Math.min(buffer.length, bytesToLimit);
                int readBytes = inputStream.read(buffer, 0, bytesToRead);
                if (readBytes < 0) {
                    return Result.allowed(totalBytes);
                }

                totalBytes += readBytes;
                if (totalBytes > maxFileSizeBytes) {
                    return Result.tooLarge(totalBytes);
                }
            }
        } catch (IOException ignored) {
            return Result.unknown();
        }

        return Result.unknown();
    }

    private static Result inspectResolverStream(
            ContentResolver resolver,
            Uri fileUri,
            long maxFileSizeBytes
    ) {
        try (InputStream inputStream = resolver.openInputStream(fileUri)) {
            return inspectStream(inputStream, maxFileSizeBytes);
        } catch (IOException | SecurityException ignored) {
            return Result.unknown();
        }
    }

    private static long resolveOpenableSize(ContentResolver resolver, Uri fileUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(fileUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
            // SAF 메타데이터가 비어 있거나 조회에 실패하면 다른 크기 판정 경로로 넘긴다.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    private static long resolveLocalFileSize(Uri fileUri) {
        if (!"file".equalsIgnoreCase(fileUri.getScheme())) {
            return -1L;
        }

        File localFile = new File(normalizeText(fileUri.getPath()));
        if (!localFile.isFile()) {
            return -1L;
        }
        return localFile.length();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    enum Status {
        ALLOWED,
        TOO_LARGE,
        UNKNOWN
    }

    static final class Result {
        private final Status status;
        private final long fileSizeBytes;

        private Result(Status status, long fileSizeBytes) {
            this.status = status;
            this.fileSizeBytes = fileSizeBytes;
        }

        static Result allowed(long fileSizeBytes) {
            return new Result(Status.ALLOWED, fileSizeBytes);
        }

        static Result tooLarge(long fileSizeBytes) {
            return new Result(Status.TOO_LARGE, fileSizeBytes);
        }

        static Result unknown() {
            return new Result(Status.UNKNOWN, -1L);
        }

        Status getStatus() {
            return status;
        }

        long getFileSizeBytes() {
            return fileSizeBytes;
        }

        boolean isTooLarge() {
            return status == Status.TOO_LARGE;
        }

        boolean isUnknown() {
            return status == Status.UNKNOWN;
        }
    }
}
