package com.bodeul.core.session;

interface CompanionAttachmentStorage {

    StoreResult store(
            String storagePath,
            byte[] content,
            String contentType,
            String sha256);

    byte[] read(String storagePath, long maxBytes);

    void delete(String storagePath);

    record StoreResult(boolean created) {
    }
}
