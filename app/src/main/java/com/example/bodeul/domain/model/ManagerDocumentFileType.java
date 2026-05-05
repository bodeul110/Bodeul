package com.example.bodeul.domain.model;

/**
 * 매니저가 제출하는 원본 서류 파일의 종류를 식별한다.
 */
public enum ManagerDocumentFileType {
    ID_CARD("idCard"),
    LICENSE("license"),
    CRIMINAL_RECORD("criminalRecord"),
    HEALTH_CERTIFICATE("healthCertificate"),
    RESIDENT_REGISTRATION("residentRegistration"),
    BANKBOOK_COPY("bankbookCopy");

    private final String storageKey;

    ManagerDocumentFileType(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public static ManagerDocumentFileType fromStorageKey(String storageKey) {
        if (storageKey == null) {
            return null;
        }
        for (ManagerDocumentFileType type : values()) {
            if (type.storageKey.equals(storageKey)) {
                return type;
            }
        }
        return null;
    }
}
