package com.example.bodeul.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

import java.util.List;

/** 정상 동행 단계에서 선택 등록하는 결제·처방 첨부의 앱 사전 검증 규칙이다. */
public final class CompanionSessionArtifactUploadPolicy {
    public static final String PAYMENT_EVIDENCE = "PAYMENT_EVIDENCE";
    public static final String PRESCRIPTION_IMAGE = "PRESCRIPTION_IMAGE";

    private CompanionSessionArtifactUploadPolicy() {
    }

    public static String validate(
            ContentResolver resolver,
            String purpose,
            List<Uri> fileUris
    ) {
        if (!PAYMENT_EVIDENCE.equals(purpose) && !PRESCRIPTION_IMAGE.equals(purpose)) {
            return "첨부 파일 용도를 확인하지 못했습니다.";
        }
        int count = fileUris == null ? 0 : fileUris.size();
        int limit = PAYMENT_EVIDENCE.equals(purpose) ? 1 : 3;
        if (count == 0 || count > limit) {
            return PAYMENT_EVIDENCE.equals(purpose)
                    ? "결제 증빙은 1개만 선택할 수 있습니다."
                    : "처방 이미지는 1개부터 3개까지 선택할 수 있습니다.";
        }
        for (Uri fileUri : fileUris) {
            String error = CompanionChatAttachmentUploadPolicy.validate(resolver, fileUri);
            if (!TextUtils.isEmpty(error)) {
                return error.replace("안심 채팅 첨부", "동행 첨부");
            }
            String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(
                    resolver,
                    fileUri);
            if (PRESCRIPTION_IMAGE.equals(purpose)
                    && "application/pdf".equals(contentType)) {
                return "처방 이미지는 JPEG 또는 PNG 파일만 등록할 수 있습니다.";
            }
        }
        return "";
    }
}
