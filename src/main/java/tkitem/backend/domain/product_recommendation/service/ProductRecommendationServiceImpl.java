package tkitem.backend.domain.product_recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.product_recommendation.dto.request.BudgetRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.*;
import tkitem.backend.domain.product_recommendation.mapper.ProductRecommendationMapper;
import tkitem.backend.domain.product_recommendation.vo.*;

import java.math.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductRecommendationServiceImpl implements ProductRecommendationService {

    private final ProductRecommendationMapper recommendationMapper;

    // 1. 예산에 맞는 추천리스트
    @Override
    @Transactional(readOnly = true)
    public BudgetRecommendationResponse recommendByBudget(BudgetRecommendationRequest request, Character gender) {
        // 체크리스트 아이템들 조회
        List<ChecklistItem> checklistItems = recommendationMapper.findChecklistItemsByIds(request.getChecklistItemIds());

        if (checklistItems.isEmpty()) {
            throw new IllegalArgumentException("체크리스트 아이템을 찾을 수 없습니다.");
        }

        Long tripId = checklistItems.get(0).getTripId();
        Trip trip = recommendationMapper.findTripById(tripId);

        // 일자별로 그룹핑
        Map<Integer, List<ChecklistItem>> groupedByDate = checklistItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getScheduleDate() != null ? item.getScheduleDate() : -1
                ));

        List<ScheduleGroupResponse> groups = new ArrayList<>();
        BigDecimal remainingBudget = request.getBudget();
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (Map.Entry<Integer, List<ChecklistItem>> entry : groupedByDate.entrySet()) {
            Integer scheduleDate = entry.getKey() == -1 ? null : entry.getKey();
            List<ChecklistItem> items = entry.getValue();

            // score 높은 순으로 정렬
            items.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            List<ChecklistProductResponse> productResponses = new ArrayList<>();

            for (ChecklistItem item : items) {
                if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) break;

                // 예산 내 최적 상품 조회
                Map<String, Object> params = new HashMap<>();
                params.put("categoryId", item.getProductCategorySubId());
                params.put("maxPrice", remainingBudget);
                params.put("memberId", trip.getMemberId());
                params.put("notes", item.getNotes());
                params.put("gender", gender);

                Product recommendedProduct = recommendationMapper.findBestProductForBudget(params);

                if (recommendedProduct != null) {
                    // 추천 이유 생성
                    String reason = generateRecommendReason(item, recommendedProduct, trip.getMemberId());

                    ProductResponse productResponse = ProductResponse.builder()
                            .productId(recommendedProduct.getProductId())
                            .name(recommendedProduct.getName())
                            .brandName(recommendedProduct.getBrandName())
                            .category(recommendedProduct.getCategoryName())
                            .url(recommendedProduct.getUrl())
                            .imgUrl(recommendedProduct.getImgUrl())
                            .code(recommendedProduct.getCode())
                            .price(recommendedProduct.getPrice())
                            .avgReview(recommendedProduct.getAvgReview())
                            .recommendReason(reason)
                            .build();

                    productResponses.add(ChecklistProductResponse.builder()
                            .checklistItemId(item.getChecklistItemId())
                            .product(productResponse)
                            .build());

                    remainingBudget = remainingBudget.subtract(recommendedProduct.getPrice());
                    totalSpent = totalSpent.add(recommendedProduct.getPrice());
                }
            }

            if (!productResponses.isEmpty()) {
                groups.add(ScheduleGroupResponse.builder()
                        .scheduleDate(scheduleDate)
                        .items(productResponses)
                        .build());
            }
        }

        return BudgetRecommendationResponse.builder()
                .tripId(tripId)
                .budget(request.getBudget())
                .groups(groups)
                .totalPrice(totalSpent)
                .remainingBudget(remainingBudget)
                .build();
    }

    // 2. 추천 아이템 후보군
    @Override
    @Transactional(readOnly = true)
    public ProductCandidatesResponse getProductCandidates(Long tripId, Long checklistItemId, Character gender) {
        ChecklistItem checklistItem = recommendationMapper.findChecklistItemById(checklistItemId);
        Trip trip = recommendationMapper.findTripById(tripId);

        // 상품 후보군 조회 파라미터
        Map<String, Object> params = new HashMap<>();
        params.put("categoryId", checklistItem.getProductCategorySubId());
        params.put("memberId", trip.getMemberId());
        params.put("notes", checklistItem.getNotes());
        params.put("scheduleDate", checklistItem.getScheduleDate());
        params.put("limit", 20);
        params.put("gender", gender);

        List<ProductWithScore> products = recommendationMapper.findProductCandidates(params);

        // ProductResponse로 변환
        List<ProductResponse> productResponses = products.stream()
                .map(p -> ProductResponse.builder()
                        .productId(p.getProductId())
                        .name(p.getName())
                        .brandName(p.getBrandName())
                        .category(p.getCategoryName())
                        .code(p.getCode())
                        .url(p.getUrl())
                        .imgUrl(p.getImgUrl())
                        .price(p.getPrice())
                        .avgReview(p.getAvgReview())
                        .matchScore(p.getMatchScore())
                        .matchedTags(Arrays.asList(p.getMatchedTags().split(",")))
                        .recommendReason(p.getRecommendReason())
                        .build())
                .collect(Collectors.toList());

        return ProductCandidatesResponse.builder()
                .tripId(tripId)
                .checklistItemId(checklistItemId)
                .productCategorySubId(checklistItem.getProductCategorySubId())
                .itemName(checklistItem.getItemName())
                .products(productResponses)
                .categoryContext(checklistItem.getNotes())
                .build();
    }

    // 3. 최근 본 상품의 연관상품
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getRelatedProducts(Long productId, int limit, Character gender) {
        Map<String, Object> params = new HashMap<>();
        params.put("productId", productId);
        params.put("limit", limit);
        params.put("gender", gender);

        List<ProductWithSimilarity> relatedProducts = recommendationMapper.findRelatedProducts(params);

        return relatedProducts.stream()
                .map(p -> ProductResponse.builder()
                        .productId(p.getProductId())
                        .name(p.getName())
                        .brandName(p.getBrandName())
                        .category(p.getCategoryName())
                        .code(p.getCode())
                        .url(p.getUrl())
                        .imgUrl(p.getImgUrl())
                        .price(p.getPrice())
                        .avgReview(p.getAvgReview())
                        .matchScore(p.getSimilarity())
                        .recommendReason(String.format("%s와 유사한 스타일 (유사도: %.0f%%)",
                                p.getBaseName(), p.getSimilarity() * 100))
                        .build())
                .collect(Collectors.toList());
    }

    // 4. 사용자의 가장 가까운 trip에서 추천 아이템
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getUpcomingTripItems(Long memberId, int limit, Character gender) {
        // 가장 가까운 미래 여행 조회
        Trip upcomingTrip = recommendationMapper.findUpcomingTrip(memberId);
        if (upcomingTrip == null) {
            throw new IllegalArgumentException("예정된 여행이 없습니다.");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("tripId", upcomingTrip.getTripId());
        params.put("memberId", memberId);
        params.put("minScore", 0.7);
        params.put("limit", limit);
        params.put("gender", gender);

        List<ProductForTrip> products = recommendationMapper.findUpcomingTripProducts(params);

        return products.stream()
                .map(p -> ProductResponse.builder()
                        .productId(p.getProductId())
                        .name(p.getName())
                        .brandName(p.getBrandName())
                        .category(p.getCategoryName())
                        .code(p.getCode())
                        .url(p.getUrl())
                        .imgUrl(p.getImgUrl())
                        .price(p.getPrice())
                        .avgReview(p.getAvgReview())
                        .recommendReason(String.format("%s 여행 필수템 - %s (중요도: %.0f%%)",
                                upcomingTrip.getTitle(), p.getTier(), p.getItemScore() * 100))
                        .build())
                .collect(Collectors.toList());
    }

    // 5. 사용자의 선호 취향에 맞는 옷
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getFashionByPreference(Long memberId, int limit, Character gender) {
        Preference preference = recommendationMapper.findPreferenceByMemberId(memberId);
        if (preference == null) {
            throw new IllegalArgumentException("사용자 취향 정보를 찾을 수 없습니다.");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("memberId", memberId);
        params.put("firstLook", preference.getFirstLook());
        params.put("secondLook", preference.getSecondLook());
        params.put("brightness", preference.getBrightness());
        params.put("boldness", preference.getBoldness());
        params.put("fit", preference.getFit());
        params.put("limit", limit);
        params.put("gender", gender);

        List<FashionProduct> fashionProducts = recommendationMapper.findFashionByPreference(params);

        return fashionProducts.stream()
                .map(p -> {
                    String reason = generateFashionReason(preference, p.getMatchedStyles());
                    return ProductResponse.builder()
                            .productId(p.getProductId())
                            .name(p.getName())
                            .brandName(p.getBrandName())
                            .category(p.getCategoryName())
                            .imgUrl(p.getImgUrl())
                            .price(p.getPrice())
                            .avgReview(p.getAvgReview())
                            .matchScore(p.getPreferenceScore())
                            .matchedTags(Arrays.asList(p.getMatchedStyles().split(",")))
                            .recommendReason(reason)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // Helper Methods
    private String generateRecommendReason(ChecklistItem item, Product product, Long memberId) {
        StringBuilder reason = new StringBuilder();

        if (item.getTier() != null) {
            reason.append(item.getTier()).append(" 아이템");
        }

        if (item.getNotes() != null) {
            if (item.getNotes().contains("RAIN")) reason.append(" - 우천 대비");
            if (item.getNotes().contains("HOT")) reason.append(" - 더운 날씨용");
            if (item.getNotes().contains("COLD")) reason.append(" - 추운 날씨용");
        }

        return reason.toString();
    }

    private String generateFashionReason(Preference preference, String matchedStyles) {
        StringBuilder reason = new StringBuilder();

        if (preference.getFirstLook() != null) {
            reason.append(getFashionStyleName(preference.getFirstLook()));
            if (preference.getSecondLook() != null) {
                reason.append(" & ").append(getFashionStyleName(preference.getSecondLook()));
            }
            reason.append(" 스타일");
        }

        if (matchedStyles != null && !matchedStyles.isEmpty()) {
            reason.append(" - ").append(matchedStyles);
        }

        return reason.toString();
    }

    private String getFashionStyleName(String style) {
        Map<String, String> styleNames = Map.of(
                "MODERN", "모던",
                "STREET", "스트릿",
                "CASUAL", "캐주얼",
                "SPORT", "스포티",
                "MILITARY", "밀리터리",
                "BOHO", "보헤미안",
                "ROMANTIC", "로맨틱",
                "DANDY", "댄디"
        );
        return styleNames.getOrDefault(style, style);
    }

}


