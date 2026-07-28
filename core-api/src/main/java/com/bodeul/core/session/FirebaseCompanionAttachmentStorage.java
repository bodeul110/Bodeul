package com.bodeul.core.session;

import java.util.Map;
import java.util.Objects;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("database")
class FirebaseCompanionAttachmentStorage implements CompanionAttachmentStorage {

    private static final String SHA256_METADATA_KEY = "bodeul-sha256";

    private final String projectId;
    private final String bucketName;
    private volatile Storage storage;

    @Autowired
    FirebaseCompanionAttachmentStorage(
            @Value("${FIREBASE_PROJECT_ID:}") String projectId,
            @Value("${FIREBASE_STORAGE_BUCKET:}") String configuredBucketName) {
        this(projectId, configuredBucketName, null);
    }

    FirebaseCompanionAttachmentStorage(
            String projectId,
            String configuredBucketName,
            Storage storage) {
        this.projectId = normalize(projectId);
        String normalizedBucket = normalize(configuredBucketName);
        this.bucketName = normalizedBucket.isEmpty() && !this.projectId.isEmpty()
                ? this.projectId + ".firebasestorage.app"
                : normalizedBucket;
        this.storage = storage;
    }

    @Override
    public StoreResult store(
            String storagePath,
            byte[] content,
            String contentType,
            String sha256) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath))
                .setContentType(contentType)
                .setCacheControl("private, no-store")
                .setMetadata(Map.of(SHA256_METADATA_KEY, sha256))
                .build();
        try {
            requireStorage().create(
                    blobInfo,
                    content,
                    Storage.BlobTargetOption.doesNotExist());
            return new StoreResult(true);
        } catch (StorageException exception) {
            if (exception.getCode() != 412) {
                throw CompanionSessionException.attachmentUnavailable();
            }
            try {
                if (matchesExisting(storagePath, content, contentType, sha256)) {
                    return new StoreResult(false);
                }
            } catch (StorageException ignored) {
                // 안정적인 API 오류 계약을 위해 Storage SDK 예외를 외부로 노출하지 않는다.
            }
            throw CompanionSessionException.attachmentUnavailable();
        }
    }

    @Override
    public byte[] read(String storagePath, long maxBytes) {
        try {
            Blob blob = requireStorage().get(BlobId.of(bucketName, storagePath));
            if (blob == null || blob.getSize() <= 0L || blob.getSize() > maxBytes) {
                throw CompanionSessionException.attachmentNotFound();
            }
            byte[] content = blob.getContent();
            if (content.length > maxBytes) {
                throw CompanionSessionException.attachmentUnavailable();
            }
            return content;
        } catch (CompanionSessionException exception) {
            throw exception;
        } catch (StorageException exception) {
            throw CompanionSessionException.attachmentUnavailable();
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Blob blob = requireStorage().get(BlobId.of(bucketName, storagePath));
            if (blob != null && !blob.delete()) {
                throw CompanionSessionException.attachmentUnavailable();
            }
        } catch (CompanionSessionException exception) {
            throw exception;
        } catch (StorageException exception) {
            throw CompanionSessionException.attachmentUnavailable();
        }
    }

    private boolean matchesExisting(
            String storagePath,
            byte[] content,
            String contentType,
            String sha256) {
        Blob blob = requireStorage().get(BlobId.of(bucketName, storagePath));
        return blob != null
                && blob.getSize() == content.length
                && Objects.equals(contentType, blob.getContentType())
                && blob.getMetadata() != null
                && Objects.equals(sha256, blob.getMetadata().get(SHA256_METADATA_KEY));
    }

    private Storage requireStorage() {
        if (projectId.isEmpty() || bucketName.isEmpty()) {
            throw CompanionSessionException.attachmentUnavailable();
        }
        if (storage != null) {
            return storage;
        }
        synchronized (this) {
            if (storage == null) {
                try {
                    storage = StorageOptions.newBuilder()
                            .setProjectId(projectId)
                            .build()
                            .getService();
                } catch (RuntimeException exception) {
                    throw CompanionSessionException.attachmentUnavailable();
                }
            }
        }
        return storage;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
