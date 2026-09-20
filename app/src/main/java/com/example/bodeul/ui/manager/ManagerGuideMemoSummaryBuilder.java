package com.example.bodeul.ui.manager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 저장된 원문을 순서대로 묶고 같은 원문은 한 번만 표시한다. */
final class ManagerGuideMemoSummaryBuilder {
    static final class Candidate {
        private final String title;
        private final String body;

        Candidate(String title, String body) {
            this.title = safeTrim(title);
            this.body = body == null ? "" : body;
        }
    }

    private ManagerGuideMemoSummaryBuilder() {
    }

    static List<ManagerGuideMemoItem> build(
            @NonNull List<Candidate> candidates,
            @NonNull String emptyLabel
    ) {
        List<ManagerGuideMemoItem> result = new ArrayList<>();
        Map<String, Integer> contentIndexes = new LinkedHashMap<>();
        String safeEmptyLabel = safeTrim(emptyLabel);

        for (Candidate candidate : candidates) {
            String contentKey = safeTrim(candidate.body);
            if (contentKey.isEmpty()) {
                result.add(new ManagerGuideMemoItem(candidate.title, safeEmptyLabel, true));
                continue;
            }

            Integer existingIndex = contentIndexes.get(contentKey);
            if (existingIndex == null) {
                contentIndexes.put(contentKey, result.size());
                result.add(new ManagerGuideMemoItem(candidate.title, candidate.body, false));
                continue;
            }

            ManagerGuideMemoItem existing = result.get(existingIndex);
            result.set(existingIndex, new ManagerGuideMemoItem(
                    existing.getTitle() + " · " + candidate.title,
                    existing.getBody(),
                    false));
        }
        return result;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
