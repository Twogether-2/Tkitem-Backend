package tkitem.backend.domain.product_recommendation.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.product_recommendation.dto.request.ProductRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.ProductRecommendationResponse;
import tkitem.backend.domain.product_recommendation.service.ProductRecommendationServiceImpl;

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
}