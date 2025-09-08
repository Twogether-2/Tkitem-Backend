package tkitem.backend.domain.product_recommendation.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.product_recommendation.dto.request.ProductRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.CandidateListResponse;
import tkitem.backend.domain.product_recommendation.dto.response.ProductRecommendationResponse;
import tkitem.backend.domain.product_recommendation.dto.response.ProductResponse;
import tkitem.backend.domain.product_recommendation.service.ProductRecommendationServiceImpl;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/product-recommendation")
@RestController
public class ProductRecommendationController {

    private final ProductRecommendationServiceImpl productRecommendationService;

    @PostMapping("/{tripId}")
    public ResponseEntity<ProductRecommendationResponse> planWithBudget(
            @PathVariable Long tripId,
            @RequestParam(name="scheduleDate", required=false) String scheduleDate,
            @RequestParam(name="perItemCandidates", defaultValue="20") int perItemCandidates,
            @RequestParam(name="step", defaultValue="100") int step,
            @RequestBody ProductRecommendationRequest request
    ) {
        ProductRecommendationResponse response = productRecommendationService.planWithBudget(
                tripId,
                request.getChecklistItemIds(),
                request.getBudget(),
                request.getWeights(),
                scheduleDate,
                perItemCandidates,
                step
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}/candidates/{checklistItemId}")
    public ResponseEntity<CandidateListResponse> getCandidatesForChecklistItem(
            @PathVariable Long tripId,
            @PathVariable Long checklistItemId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(
                productRecommendationService.getCandidatesForChecklistItem(tripId, checklistItemId, limit)
        );
    }

    @GetMapping("/related-to-recent")
    public ResponseEntity<List<ProductResponse>> relatedToRecent(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(productRecommendationService.relatedToRecent(productId, limit));
    }

    @GetMapping("/near-trip-items")
    public ResponseEntity<List<ProductResponse>> nearTripItems(
            @AuthenticationPrincipal Member member,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(productRecommendationService.nearTripItems(member.getMemberId(), limit));
    }

    @GetMapping("/personal-clothing")
    public ResponseEntity<List<ProductResponse>> personalClothing(
            @AuthenticationPrincipal Member member,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(productRecommendationService.personalClothing(member.getMemberId(), limit));
    }
}