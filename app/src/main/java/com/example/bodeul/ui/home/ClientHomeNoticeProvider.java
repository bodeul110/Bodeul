package com.example.bodeul.ui.home;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 홈 하단 안내 카드 목록을 역할에 맞게 제공한다.
 */
public final class ClientHomeNoticeProvider {
    public List<ClientHomeNotice> createNotices(UserRole role) {
        List<ClientHomeNotice> notices = new ArrayList<>();
        notices.add(new ClientHomeNotice(
                R.drawable.bg_service_thumb_warm,
                R.string.client_home_notice_prepare_eyebrow,
                R.string.client_home_notice_prepare_title,
                R.string.client_home_notice_prepare_body
        ));
        notices.add(new ClientHomeNotice(
                R.drawable.bg_service_thumb_cool,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_eyebrow
                        : R.string.client_home_notice_patient_eyebrow,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_title
                        : R.string.client_home_notice_patient_title,
                role == UserRole.GUARDIAN
                        ? R.string.client_home_notice_guardian_body
                        : R.string.client_home_notice_patient_body
        ));
        return notices;
    }
}
