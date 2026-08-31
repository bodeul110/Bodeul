package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.ManagerDocumentFileType;

import org.junit.Test;

/**
 * 매니저 원본 서류의 현재 기술상 업로드 하한선을 고정한다.
 */
public class ManagerDocumentUploadPolicyTest {
    @Test
    public void canonicalQualificationTypesAreTheOnlyNewUploadTypes() {
        assertTrue(ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                ManagerDocumentFileType.LICENSE
        ));
        assertTrue(ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                ManagerDocumentFileType.NURSING_LICENSE
        ));
        assertFalse(ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                ManagerDocumentFileType.ID_CARD
        ));
        assertFalse(ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                ManagerDocumentFileType.CRIMINAL_RECORD
        ));
        assertFalse(ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                ManagerDocumentFileType.HEALTH_CERTIFICATE
        ));
    }

    @Test
    public void healthCertificateIsLegacyReadOnlyType() {
        assertTrue(ManagerDocumentUploadPolicy.isLegacyReadOnlyType(
                ManagerDocumentFileType.HEALTH_CERTIFICATE
        ));
        assertFalse(ManagerDocumentUploadPolicy.isLegacyReadOnlyType(
                ManagerDocumentFileType.NURSING_LICENSE
        ));
    }

    @Test
    public void validateFileTypeUsesCurrentQualificationLabels() {
        assertNull(ManagerDocumentUploadPolicy.validateFileType(
                ManagerDocumentFileType.LICENSE
        ));
        assertNull(ManagerDocumentUploadPolicy.validateFileType(
                ManagerDocumentFileType.NURSING_LICENSE
        ));
        assertEquals(
                "간호사 면허증 또는 현재 직무 관련 자격 증빙 중 1종만 업로드할 수 있습니다.",
                ManagerDocumentUploadPolicy.validateFileType(ManagerDocumentFileType.ID_CARD)
        );
    }

    @Test
    public void validateContentType_allowsReviewableImages() {
        assertNull(ManagerDocumentUploadPolicy.validateContentType("image/jpeg"));
        assertNull(ManagerDocumentUploadPolicy.validateContentType("image/png"));
        assertNull(ManagerDocumentUploadPolicy.validateContentType("image/webp"));
    }

    @Test
    public void validateContentType_blocksPdfAndUnsupportedImages() {
        assertEquals(
                "원본 서류는 JPEG, PNG 또는 WebP 이미지로만 업로드할 수 있습니다.",
                ManagerDocumentUploadPolicy.validateContentType("application/pdf")
        );
        assertEquals(
                "원본 서류는 JPEG, PNG 또는 WebP 이미지로만 업로드할 수 있습니다.",
                ManagerDocumentUploadPolicy.validateContentType("image/gif")
        );
    }

    @Test
    public void validateFileSize_blocksFileOverTenMegabytes() {
        UploadFileSizePolicy.Result sizeResult = UploadFileSizePolicy.fromKnownSize(
                ManagerDocumentUploadPolicy.MAX_FILE_SIZE_BYTES + 1L,
                ManagerDocumentUploadPolicy.MAX_FILE_SIZE_BYTES
        );

        assertEquals(
                "원본 서류는 10MB 이하 파일만 업로드할 수 있습니다.",
                ManagerDocumentUploadPolicy.validateFileSize(sizeResult)
        );
    }

    @Test
    public void validateFileSize_blocksUnknownSize() {
        UploadFileSizePolicy.Result sizeResult = UploadFileSizePolicy.fromKnownSize(
                -1L,
                ManagerDocumentUploadPolicy.MAX_FILE_SIZE_BYTES
        );

        assertEquals(
                "원본 서류 파일 크기를 확인할 수 없습니다. 다시 선택해주세요.",
                ManagerDocumentUploadPolicy.validateFileSize(sizeResult)
        );
    }
}
