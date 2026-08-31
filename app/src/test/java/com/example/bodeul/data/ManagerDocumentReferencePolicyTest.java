package com.example.bodeul.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.ManagerDocumentFileType;

import org.junit.Test;

public class ManagerDocumentReferencePolicyTest {
    @Test
    public void licenseRequiresMatchingMetadataPathMapAndAlias() {
        String path = "manager-documents/manager-1/license/license.png";

        assertTrue(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.LICENSE,
                path,
                path,
                true,
                path
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.LICENSE,
                path,
                path,
                false,
                null
        ));
    }

    @Test
    public void nursingLicenseRejectsStaleLicenseAlias() {
        String path = "manager-documents/manager-1/nursingLicense/nursing.png";

        assertTrue(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.NURSING_LICENSE,
                path,
                path,
                false,
                null
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.NURSING_LICENSE,
                path,
                path,
                true,
                "manager-documents/manager-1/license/old.png"
        ));
    }

    @Test
    public void legacyHealthAllowsMissingAliasButRejectsMismatchedAlias() {
        String path = "manager-documents/manager-1/healthCertificate/legacy.png";

        assertTrue(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.HEALTH_CERTIFICATE,
                path,
                path,
                false,
                null
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.HEALTH_CERTIFICATE,
                path,
                path,
                true,
                "manager-documents/manager-1/healthCertificate/other.png"
        ));
    }

    @Test
    public void pathMustMatchUidDocumentKeyAndPathMap() {
        String path = "manager-documents/manager-1/license/license.png";

        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-2",
                ManagerDocumentFileType.LICENSE,
                path,
                path,
                true,
                path
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.LICENSE,
                path,
                "manager-documents/manager-1/license/other.png",
                true,
                path
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.NURSING_LICENSE,
                path,
                path,
                false,
                null
        ));
        assertFalse(ManagerDocumentReferencePolicy.isConsistent(
                "manager-1",
                ManagerDocumentFileType.LICENSE,
                path,
                path + " ",
                true,
                path
        ));
    }
}
