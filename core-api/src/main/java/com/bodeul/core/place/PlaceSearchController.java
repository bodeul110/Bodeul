package com.bodeul.core.place;

import java.util.List;

import com.bodeul.core.auth.AppUserRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    PlaceSearchController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    @GetMapping("/search")
    ResponseEntity<PlaceSearchResponse> search(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @RequestParam String query,
            @RequestParam String category) {
        List<PlaceResponse> places = placeSearchService.search(
                        appUser.id(),
                        query,
                        PlaceSearchCategory.fromRequestValue(category))
                .stream()
                .map(result -> new PlaceResponse(result.name(), result.latitude(), result.longitude()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PlaceSearchResponse(places));
    }

    private record PlaceSearchResponse(List<PlaceResponse> places) {
    }

    private record PlaceResponse(String name, double latitude, double longitude) {
    }
}
