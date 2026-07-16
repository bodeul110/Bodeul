package com.bodeul.core.place;

import java.util.List;
import java.util.UUID;

public interface PlaceSearchService {
    List<PlaceSearchResult> search(UUID userId, String query, PlaceSearchCategory category);
}
