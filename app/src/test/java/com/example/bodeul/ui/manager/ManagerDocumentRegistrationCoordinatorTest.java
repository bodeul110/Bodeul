package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ManagerDocumentRegistrationCoordinatorTest {
    @Test
    public void jobQualificationImageEnablesReviewRequest() {
        assertTrue(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profileWithFiles(
                document(ManagerDocumentFileType.LICENSE, "license.png", "image/png")
        )));
    }

    @Test
    public void nursingLicenseImageEnablesReviewRequest() {
        assertTrue(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profileWithFiles(
                document(ManagerDocumentFileType.NURSING_LICENSE, "nursing.webp", "image/webp")
        )));
    }

    @Test
    public void bothCanonicalQualificationsMustBeReplacedWithOneBeforeReviewRequest() {
        assertFalse(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profileWithFiles(
                document(ManagerDocumentFileType.LICENSE, "license.png", "image/png"),
                document(ManagerDocumentFileType.NURSING_LICENSE, "nursing.jpg", "image/jpeg")
        )));
    }

    @Test
    public void legacyHealthCertificateRemainsReadableButCannotRequestNewReview() {
        ManagerHomeProfile profile = profileWithFiles(
                document(ManagerDocumentFileType.HEALTH_CERTIFICATE, "legacy.jpg", "image/jpeg")
        );

        assertFalse(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profile));
        assertTrue(ManagerDocumentRegistrationCoordinator.requiresQualificationReplacement(profile));
    }

    @Test
    public void identityAndCriminalRecordDoNotSatisfyQualificationRequirement() {
        assertFalse(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profileWithFiles(
                document(ManagerDocumentFileType.ID_CARD, "id-card.jpg", "image/jpeg"),
                document(ManagerDocumentFileType.CRIMINAL_RECORD, "record.webp", "image/webp")
        )));
    }

    @Test
    public void unsupportedQualificationFileMustBeReplacedBeforeReviewRequest() {
        ManagerHomeProfile profile = profileWithFiles(
                document(ManagerDocumentFileType.LICENSE, "license.pdf", "application/pdf")
        );

        assertFalse(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profile));
        assertTrue(ManagerDocumentRegistrationCoordinator.requiresQualificationReplacement(profile));
    }

    @Test
    public void inconsistentCanonicalReferenceMustBeReplacedBeforeReviewRequest() {
        ManagerHomeProfile profile = profileWithFiles(new ManagerDocumentFileMetadata(
                ManagerDocumentFileType.LICENSE,
                "manager-documents/manager-1/license/license.png",
                "license.png",
                "image/png",
                100L,
                "",
                false
        ));

        assertFalse(ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profile));
        assertTrue(ManagerDocumentRegistrationCoordinator.requiresQualificationReplacement(profile));
    }

    private static ManagerHomeProfile profileWithFiles(ManagerDocumentFileMetadata... files) {
        List<ManagerDocumentFileMetadata> documentFiles = Arrays.asList(files);
        return new ManagerHomeProfile(
                "제출 요약",
                "",
                ManagerDocumentStatus.REJECTED,
                "이미지로 다시 제출해 주세요.",
                100L,
                90L,
                "관리자",
                documentFiles
        );
    }

    private static ManagerDocumentFileMetadata document(
            ManagerDocumentFileType fileType,
            String fileName,
            String contentType
    ) {
        return new ManagerDocumentFileMetadata(
                fileType,
                "manager-documents/manager-1/" + fileType.getStorageKey() + "/" + fileName,
                fileName,
                contentType,
                100L,
                ""
        );
    }
}
