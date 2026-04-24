package com.example.bodeul.ui.admin;

import com.example.bodeul.domain.model.HospitalGuide;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 병원 가이드 카드와 폼 상태를 조합한다.
 */
public final class AdminGuideCoordinator {
    private final AdminGuidePresentationFormatter formatter;

    public AdminGuideCoordinator(AdminGuidePresentationFormatter formatter) {
        this.formatter = formatter;
    }

    public List<AdminGuideCardModel> createGuideCards(List<HospitalGuide> guides) {
        List<AdminGuideCardModel> cards = new ArrayList<>();
        for (HospitalGuide guide : guides) {
            cards.add(new AdminGuideCardModel(
                    guide.getId(),
                    formatter.buildGuideTitle(guide),
                    formatter.buildGuideCount(guide),
                    formatter.buildGuidePreview(guide)
            ));
        }
        return cards;
    }

    public AdminGuideFormModel createGuideFormModel(HospitalGuide editingGuide, boolean loading) {
        return formatter.createFormModel(editingGuide, loading);
    }

    public String buildEditableGuideSteps(HospitalGuide guide) {
        return formatter.buildEditableGuideSteps(guide);
    }
}
