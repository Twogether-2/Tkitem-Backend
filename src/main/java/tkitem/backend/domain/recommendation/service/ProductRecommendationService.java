package tkitem.backend.domain.recommendation.service;

import tkitem.backend.domain.recommendation.dto.request.ProductRecommendationRequest;
import tkitem.backend.domain.recommendation.dto.response.ProductRecommendationResponse;

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
}
