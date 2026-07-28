package com.bodeul.core.session;

import java.util.Map;
import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("database")
class FirebaseCompanionAttachmentStorage implements CompanionAttachmentStorage {

    private static final String FIREBASE_APP_NAME = "bodeul-core-attachment-storage";
    private static final String SHA256_METADATA_KEY = "bodeul-sha256";

    private final String projectId;
    private final String bucketName;
    private volatile Bucket bucket;

    FirebaseCompanionAttachmentStorage(
            @Value("${FIREBASE_PROJECT_ID:}") String projectId,
            @Value("${FIREBASE_STORAGE_BUCKET:}") String configuredBucketName) {
        this.projectId = normalize(projectId);
        String normalizedBucket = normalize(configuredBucketName);
        this.bucketName = normalizedBucket.isEmpty() && !this.projectId.isEmpty()
                ? this.projectId + ".firebasestorage.app"
                : normalizedBucket;
    }

    @Override
    public StoreResult store(
            String storagePath,
            byte[] content,
            String contentType,
            String sha256) {
        Bucket targetBucket = requireBucket();
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath))
                .setContentType(contentType)
                .setCacheControl("private, no-store")
                .setMetadata(Map.of(SHA256_METADATA_KEY, sha256))
                .build();
        try {
            targetBucket.getStorage().create(
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
            Blob blob = requireBucket().get(storagePath);
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
            Blob blob = requireBucket().get(storagePath);
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
        Blob blob = requireBucket().get(storagePath);
        return blob != null
                && blob.getSize() == content.length
                && Objects.equals(contentType, blob.getContentType())
                && blob.getMetadata() != null
                && Objects.equals(sha256, blob.getMetadata().get(SHA256_METADATA_KEY));
    }

    private Bucket requireBucket() {
        if (projectId.isEmpty() || bucketName.isEmpty()) {
            throw CompanionSessionException.attachmentUnavailable();
        }
        if (bucket != null) {
            return bucket;
        }
        synchronized (this) {
            if (bucket == null) {
                try {
                    FirebaseApp app = FirebaseApp.getApps().stream()
                            .filter(candidate -> projectId.equals(candidate.getOptions().getProjectId()))
                            .findFirst()
                            .orElseGet(this::initializeFirebaseApp);
                    bucket = StorageClient.getInstance(app).bucket(bucketName);
                } catch (RuntimeException exception) {
                    throw CompanionSessionException.attachmentUnavailable();
                }
            }
        }
        return bucket;
    }

    private FirebaseApp initializeFirebaseApp() {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId)
                    .setStorageBucket(bucketName)
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (Exception exception) {
            throw new IllegalStateException("Firebase Storage를 초기화하지 못했습니다.", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
