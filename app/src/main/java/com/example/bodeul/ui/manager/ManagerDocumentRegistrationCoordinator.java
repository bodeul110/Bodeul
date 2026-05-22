package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 서류 등록 화면에서 사용할 상태 문구와 카드 모델을 조합한다.
 */
public final class ManagerDocumentRegistrationCoordinator {
    private static final ManagerDocumentFileType[] MANDATORY_SINGLE_FILE_TYPES = new ManagerDocumentFileType[]{
            ManagerDocumentFileType.ID_CARD,
            ManagerDocumentFileType.CRIMINAL_RECORD
    };

    private final Context context;
    private final ManagerHomePresentationFormatter formatter;

    public ManagerDocumentRegistrationCoordinator(
            Context context,
            ManagerHomePresentationFormatter formatter
    ) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ManagerDocumentRegistrationScreenModel createScreenModel(
            ManagerDocumentOverview overview,
            boolean firebaseBacked
    ) {
        ManagerHomeProfile profile = overview.getProfile();
        boolean allRequiredUploaded = hasRequiredFiles(profile);
        ManagerDocumentStatus status = profile.getDocumentStatus();

        return new ManagerDocumentRegistrationScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.manager_document_registration_status_badge),
                formatter.toDocumentStatusLabel(status),
                buildStatusBody(profile, allRequiredUploaded),
                createDocumentItems(profile),
                !TextUtils.isEmpty(profile.getDocumentReviewNote()),
                status == ManagerDocumentStatus.REJECTED
                        ? context.getString(R.string.manager_document_registration_review_rejected_title)
                        : context.getString(R.string.manager_document_registration_review_title),
                formatter.buildDocumentReviewNote(profile.getDocumentReviewNote()),
                buildRequestButtonText(status, allRequiredUploaded),
                canRequestReview(profile)
        );
    }

    public boolean canRequestReview(ManagerHomeProfile profile) {
        if (profile == null || !hasRequiredFiles(profile)) {
            return false;
        }
        ManagerDocumentStatus status = profile.getDocumentStatus();
        return status != ManagerDocumentStatus.PENDING_REVIEW
                && status != ManagerDocumentStatus.APPROVED;
    }

    public String buildRequestSummary(ManagerHomeProfile profile) {
        List<String> uploadedLabels = new ArrayList<>();
        for (ManagerDocumentFileType fileType : MANDATORY_SINGLE_FILE_TYPES) {
            if (isUploaded(profile.getDocumentFile(fileType))) {
                uploadedLabels.add(getDocumentLabel(fileType));
            }
        }
        
        // Handle combined license for summary
        if (isUploaded(profile.getDocumentFile(ManagerDocumentFileType.LICENSE)) 
                || isUploaded(profile.getDocumentFile(ManagerDocumentFileType.HEALTH_CERTIFICATE))) {
            uploadedLabels.add(context.getString(R.string.manager_document_registration_document_nursing_or_elderly_care_license));
        }

        return context.getString(
                R.string.manager_document_registration_request_summary_format,
                TextUtils.join(", ", uploadedLabels)
        );
    }

    private List<ManagerDocumentRegistrationItemModel> createDocumentItems(ManagerHomeProfile profile) {
        List<ManagerDocumentRegistrationItemModel> items = new ArrayList<>();
        
        // 1. ID Card
        items.add(createItemModel(profile, ManagerDocumentFileType.ID_CARD));

        // 2. Combined License (Nursing OR Elderly Care)
        items.add(createCombinedLicenseItemModel(profile));

        // 3. Criminal Record
        items.add(createItemModel(profile, ManagerDocumentFileType.CRIMINAL_RECORD));

        return items;
    }

    private ManagerDocumentRegistrationItemModel createItemModel(ManagerHomeProfile profile, ManagerDocumentFileType fileType) {
        ManagerDocumentFileMetadata metadata = profile.getDocumentFile(fileType);
        boolean uploaded = isUploaded(metadata);
        return new ManagerDocumentRegistrationItemModel(
                fileType,
                uploaded ? fileType : null,
                getDocumentLabel(fileType),
                getDocumentHelper(fileType),
                context.getString(uploaded
                        ? R.string.manager_profile_document_state_uploaded
                        : R.string.manager_document_registration_status_needed),
                uploaded ? R.color.bodeul_soft_blue : R.color.bodeul_soft_yellow,
                uploaded ? R.color.bodeul_primary : R.color.bodeul_text_primary,
                uploaded ? metadata.getFileName() : "",
                uploaded
                        ? context.getString(
                        R.string.manager_profile_document_file_card_uploaded_at,
                        formatter.formatTimestamp(metadata.getUploadedAtMillis())
                )
                        : context.getString(
                        R.string.manager_document_registration_missing_file_body,
                        getDocumentLabel(fileType)
                ),
                context.getString(uploaded
                        ? R.string.manager_document_registration_replace_button
                        : R.string.manager_document_registration_upload_button)
        );
    }

    private ManagerDocumentRegistrationItemModel createCombinedLicenseItemModel(ManagerHomeProfile profile) {
        ManagerDocumentFileMetadata licenseMetadata = profile.getDocumentFile(ManagerDocumentFileType.LICENSE);
        ManagerDocumentFileMetadata nursingMetadata = profile.getDocumentFile(ManagerDocumentFileType.HEALTH_CERTIFICATE);
        
        boolean licenseUploaded = isUploaded(licenseMetadata);
        boolean nursingUploaded = isUploaded(nursingMetadata);
        boolean uploaded = licenseUploaded || nursingUploaded;
        
        ManagerDocumentFileMetadata activeMetadata = nursingUploaded ? nursingMetadata : licenseMetadata;
        String label = context.getString(R.string.manager_document_registration_document_nursing_or_elderly_care_license);
        
        return new ManagerDocumentRegistrationItemModel(
                null,
                uploaded && activeMetadata != null ? activeMetadata.getFileType() : null,
                label,
                context.getString(R.string.manager_document_registration_nursing_or_elderly_care_license_helper),
                context.getString(uploaded
                        ? R.string.manager_profile_document_state_uploaded
                        : R.string.manager_document_registration_status_needed),
                uploaded ? R.color.bodeul_soft_blue : R.color.bodeul_soft_yellow,
                uploaded ? R.color.bodeul_primary : R.color.bodeul_text_primary,
                uploaded ? activeMetadata.getFileName() : "",
                uploaded
                        ? context.getString(
                        R.string.manager_profile_document_file_card_uploaded_at,
                        formatter.formatTimestamp(activeMetadata.getUploadedAtMillis())
                )
                        : context.getString(
                        R.string.manager_document_registration_missing_file_body,
                        label
                ),
                context.getString(uploaded
                        ? R.string.manager_document_registration_replace_button
                        : R.string.manager_document_registration_upload_button)
        );
    }

    private String buildStatusBody(ManagerHomeProfile profile, boolean allRequiredUploaded) {
        switch (profile.getDocumentStatus()) {
            case APPROVED:
                return context.getString(R.string.manager_document_registration_status_body_approved);
            case PENDING_REVIEW:
                return context.getString(R.string.manager_document_registration_status_body_pending);
            case REJECTED:
                return allRequiredUploaded
                        ? context.getString(R.string.manager_document_registration_status_body_rejected_ready)
                        : context.getString(R.string.manager_document_registration_status_body_rejected);
            case NOT_SUBMITTED:
            default:
                return allRequiredUploaded
                        ? context.getString(R.string.manager_document_registration_status_body_ready)
                        : context.getString(R.string.manager_document_registration_status_body_not_submitted);
        }
    }

    private String buildRequestButtonText(ManagerDocumentStatus status, boolean allRequiredUploaded) {
        if (status == ManagerDocumentStatus.PENDING_REVIEW) {
            return context.getString(R.string.manager_document_registration_request_pending_button);
        }
        if (status == ManagerDocumentStatus.APPROVED) {
            return context.getString(R.string.manager_document_registration_request_done_button);
        }
        if (!allRequiredUploaded) {
            return context.getString(R.string.manager_document_registration_request_disabled_button);
        }
        if (status == ManagerDocumentStatus.REJECTED) {
            return context.getString(R.string.manager_document_registration_request_retry_button);
        }
        return context.getString(R.string.manager_document_registration_request_button);
    }

    private boolean hasRequiredFiles(ManagerHomeProfile profile) {
        for (ManagerDocumentFileType fileType : MANDATORY_SINGLE_FILE_TYPES) {
            if (!isUploaded(profile.getDocumentFile(fileType))) {
                return false;
            }
        }
        
        // At least one of LICENSE or HEALTH_CERTIFICATE must be uploaded
        return isUploaded(profile.getDocumentFile(ManagerDocumentFileType.LICENSE)) 
                || isUploaded(profile.getDocumentFile(ManagerDocumentFileType.HEALTH_CERTIFICATE));
    }

    private boolean isUploaded(ManagerDocumentFileMetadata metadata) {
        return metadata != null && !metadata.isEmpty();
    }

    private String getDocumentLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return context.getString(R.string.manager_document_registration_document_id_card);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return context.getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return context.getString(R.string.manager_document_registration_document_nursing_license);
        }
        return context.getString(R.string.manager_document_registration_document_criminal_record);
    }

    private String getDocumentHelper(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return context.getString(R.string.manager_document_registration_id_card_helper);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return context.getString(R.string.manager_document_registration_elderly_care_license_helper);
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return context.getString(R.string.manager_document_registration_nursing_license_helper);
        }
        return context.getString(R.string.manager_document_registration_criminal_record_helper);
    }
}
