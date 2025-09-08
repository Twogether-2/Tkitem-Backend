package tkitem.backend.domain.product_recommendation.service;

import tkitem.backend.domain.product_recommendation.dto.request.ProductRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.CandidateListResponse;
import tkitem.backend.domain.product_recommendation.dto.response.ProductRecommendationResponse;
import tkitem.backend.domain.product_recommendation.dto.response.ProductResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRecommendationService {

    ProductRecommendationResponse planWithBudget(
            Long tripId,
            List<Long> checklistItemIds,
            BigDecimal budget,
            ProductRecommendationRequest.Weights weights,
            String scheduleDateParam,
            int perItemCandidates,
            int step
    );

    CandidateListResponse getCandidatesForChecklistItem(Long tripId, Long checklistItemId, int limit);

    List<ProductResponse> relatedToRecent(Long productId, int limit);

    List<ProductResponse> nearTripItems(Long memberId, int limit);

    List<ProductResponse> personalClothing(Long memberId, int limit);
}
