package tkitem.backend.domain.product_recommendation.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.product_recommendation.dto.request.BudgetRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.request.MinimumBudgetRequest;
import tkitem.backend.domain.product_recommendation.dto.response.*;
import tkitem.backend.domain.product_recommendation.service.ProductRecommendationServiceImpl;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/product-recommendation")
@RestController
public class ProductRecommendationController {

    private final ProductRecommendationServiceImpl recommendationService;

    // 예산에 맞는 추천리스트
    @PostMapping("/{tripId}")
    public ResponseEntity<BudgetRecommendationResponse> getBudgetRecommendations(
            @PathVariable Long tripId,
            @RequestBody BudgetRecommendationRequest request,
            @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(recommendationService.recommendByBudget(request, member.getGender()));
    }

    // 추천 아이템 후보군
    @GetMapping("/{tripId}/candidates/{checklistItemId}")
    public ResponseEntity<ProductCandidatesResponse> getProductCandidates(
            @PathVariable Long tripId,
            @PathVariable Long checklistItemId,
            @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(recommendationService.getProductCandidates(tripId, checklistItemId, member.getGender()));
    }

    // 최근 본 상품의 연관상품
    @GetMapping("/related-to-recent")
    public ResponseEntity<List<ProductResponse>> getRelatedProducts(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(recommendationService.getRelatedProducts(productId, limit, member.getGender()));
    }

    // 사용자의 가장 가까운 trip에서 추천 아이템
    @GetMapping("/near-trip-items")
    public ResponseEntity<List<ProductResponse>> getUpcomingTripRecommendations(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(recommendationService.getUpcomingTripItems(member.getMemberId(), limit, member.getGender()));
    }

    // 사용자의 선호 취향에 맞는 옷
    @GetMapping("/personal-clothing")
    public ResponseEntity<List<ProductResponse>> getFashionByPreference(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(recommendationService.getFashionByPreference(member.getMemberId(), limit, member.getGender()));
    }

    // 체크리스트 아이템들의 최소 예산 계산
    @PostMapping("/minimum-budget")
    public ResponseEntity<MinimumBudgetResponse> calculateMinimumBudget(
            @RequestBody MinimumBudgetRequest request,
            @AuthenticationPrincipal Member member) {
        MinimumBudgetResponse response = recommendationService.calculateMinimumBudget(request, member.getGender());
        return ResponseEntity.ok(response);
    }

}