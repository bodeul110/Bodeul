package com.bodeul.core.session;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseCompanionAttachmentStorageTests {

    private static final String PROJECT_ID = "bodeul-dev";
    private static final String DEFAULT_BUCKET = "bodeul-dev.firebasestorage.app";
    private static final String STORAGE_PATH = "companion-chat-attachments/session/message/file.png";

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    private FirebaseCompanionAttachmentStorage attachmentStorage;

    @BeforeEach
    void setUp() {
        attachmentStorage = new FirebaseCompanionAttachmentStorage(PROJECT_ID, "", storage);
    }

    @Test
    void storesAttachmentThroughObjectApiWithPrivateMetadata() {
        byte[] content = "sample".getBytes(StandardCharsets.UTF_8);

        CompanionAttachmentStorage.StoreResult result = attachmentStorage.store(
                STORAGE_PATH,
                content,
                "image/png",
                "sha256-value");

        ArgumentCaptor<BlobInfo> blobInfoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(
                blobInfoCaptor.capture(),
                aryEq(content),
                any(Storage.BlobTargetOption.class));

        BlobInfo blobInfo = blobInfoCaptor.getValue();
        assertThat(result.created()).isTrue();
        assertThat(blobInfo.getBlobId()).isEqualTo(BlobId.of(DEFAULT_BUCKET, STORAGE_PATH));
        assertThat(blobInfo.getContentType()).isEqualTo("image/png");
        assertThat(blobInfo.getCacheControl()).isEqualTo("private, no-store");
        assertThat(blobInfo.getMetadata()).isEqualTo(Map.of("bodeul-sha256", "sha256-value"));
    }

    @Test
    void readsAttachmentThroughExactBucketAndObjectPath() {
        byte[] content = "download".getBytes(StandardCharsets.UTF_8);
        when(storage.get(BlobId.of(DEFAULT_BUCKET, STORAGE_PATH))).thenReturn(blob);
        when(blob.getSize()).thenReturn((long) content.length);
        when(blob.getContent()).thenReturn(content);

        assertThat(attachmentStorage.read(STORAGE_PATH, 1024L)).isEqualTo(content);

        verify(storage).get(BlobId.of(DEFAULT_BUCKET, STORAGE_PATH));
    }

    @Test
    void deletesAttachmentThroughExactBucketAndObjectPath() {
        when(storage.get(BlobId.of(DEFAULT_BUCKET, STORAGE_PATH))).thenReturn(blob);
        when(blob.delete()).thenReturn(true);

        attachmentStorage.delete(STORAGE_PATH);

        verify(storage).get(BlobId.of(DEFAULT_BUCKET, STORAGE_PATH));
        verify(blob).delete();
    }
}
