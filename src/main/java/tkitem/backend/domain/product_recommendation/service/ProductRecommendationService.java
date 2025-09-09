package tkitem.backend.domain.product_recommendation.service;

import tkitem.backend.domain.product_recommendation.dto.request.BudgetRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.*;

import java.util.List;

public interface ProductRecommendationService {

    BudgetRecommendationResponse recommendByBudget(BudgetRecommendationRequest request, Character gender);
    ProductCandidatesResponse getProductCandidates(Long tripId, Long checklistItemId, Character gender);
    List<ProductResponse> getRelatedProducts(Long productId, int limit, Character gender);
    List<ProductResponse> getUpcomingTripItems(Long memberId, int limit, Character gender);
    List<ProductResponse> getFashionByPreference(Long memberId, int limit, Character gender);
}
