package tkitem.backend.domain.product_recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.product_recommendation.dto.request.BudgetRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.request.MinimumBudgetRequest;
import tkitem.backend.domain.product_recommendation.dto.response.*;
import tkitem.backend.domain.product_recommendation.mapper.ProductRecommendationMapper;
import tkitem.backend.domain.product_recommendation.util.RecommendReasonFormatter;
import tkitem.backend.domain.product_recommendation.vo.*;

import java.math.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

            // checklistItemId 순으로 정렬
            items.sort(Comparator.comparing(ChecklistItem::getChecklistItemId));

            List<ChecklistProductResponse> productResponses = new ArrayList<>();

            for (ChecklistItem item : items) {
                if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) break;

                // 예산 내 최적 상품 조회
                Map<String, Object> params = new HashMap<>();
                params.put("categoryId", item.getProductCategorySubId());
                params.put("tripId", item.getTripId());
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
                            .code(p.getCode())
                            .url(p.getUrl())
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

    @Override
    @Transactional(readOnly = true)
    public MinimumBudgetResponse calculateMinimumBudget(MinimumBudgetRequest request, Character gender) {
        List<ChecklistItem> items =
                recommendationMapper.findChecklistItemsByIds(request.getChecklistItemIds());
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("체크리스트 아이템을 찾을 수 없습니다.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (ChecklistItem item : items) {
            BigDecimal minPrice = recommendationMapper.getCategoryMinPrice(
                    Map.of(
                            "categoryId", item.getProductCategorySubId(),
                            "userGender", gender
                    )
            );

            if (minPrice == null) {
                minPrice = BigDecimal.ZERO;
            }
            total = total.add(minPrice);
        }

        return MinimumBudgetResponse.builder()
                .minimumBudget(total)
                .build();
    }

    // Helper Methods
    private String generateRecommendReason(ChecklistItem item, Product product, Long memberId) {
        final String tier = safe(item.getTier());        // "필수" / "강력추천" / ...
        final String notes = safe(item.getNotes());

        List<String> tokens = new ArrayList<>();

        // 1) 티어 / 일정일차
        if (!isBlank(tier)) tokens.add(tier);            // "아이템" 제거, 토큰만

        // 2) 룰 노트 요약(앞에서부터 최대 2개, ACT/FLAGS/DURATION/HIST 제외)
        List<String> ruleNotes = extractRuleNotes(notes, 2);
        tokens.addAll(ruleNotes);

        // 3) 활동(ACT)
        List<String> acts = parseActivities(notes);
        if (!acts.isEmpty()) tokens.add("ACT: " + joinComma(acts));

        // 4) 날씨 플래그(FLAGS)
        List<String> flagsKo = parseFlags(notes);
        if (!flagsKo.isEmpty()) tokens.add("FLAGS: " + joinComma(flagsKo));

        // 5) 히스토리(HIST)
        HistInfo hist = parseHist(notes);
        if (hist != null) {
            String rec = isBlank(hist.recPhrase) ? "" : " " + hist.recPhrase;
            tokens.add("HIST: +" + hist.gain + hist.unit + rec);
        }

        // 6) (옵션) 평점 토큰 — 과장 없이 짧게
        if (product != null && product.getAvgReview() != null) {
            double r = product.getAvgReview();
            if (r >= 4.2) tokens.add("평점 " + trim1(r) + "/5");
        }

        // 최종: ' | ' 로 연결
        return String.join(" | ", tokens);
    }

    /* ====================== 헬퍼들 ====================== */

    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String joinComma(List<String> parts) {
        return String.join(", ", parts);
    }

    /** 룰 노트: ' | '로 분리 후 ACT/FLAGS/DURATION/HIST 아닌 앞에서부터 maxCount개 */
    private static List<String> extractRuleNotes(String notes, int maxCount) {
        if (isBlank(notes)) return List.of();
        String[] toks = notes.split("\\s*\\|\\s*");
        List<String> out = new ArrayList<>();
        for (String t : toks) {
            String tt = t.trim();
            if (tt.isEmpty()) continue;
            String up = tt.toUpperCase(Locale.ROOT);
            if (up.startsWith("ACT:") || up.startsWith("FLAGS:") || up.startsWith("DURATION:") || up.startsWith("HIST:")) continue;
            if (tt.equals("룰 기반")) continue;
            out.add(tt);
            if (out.size() >= maxCount) break;
        }
        return out;
    }

    /** FLAGS:RAIN,HUMID → ["비","습도"] */
    private static List<String> parseFlags(String notes) {
        if (isBlank(notes)) return List.of();
        Matcher m = Pattern.compile("FLAGS:([A-Z,]+)").matcher(notes.toUpperCase(Locale.ROOT));
        if (!m.find()) return List.of();
        String[] codes = m.group(1).split("\\s*,\\s*");
        Set<String> set = new LinkedHashSet<>();
        for (String c : codes) {
            switch (c) {
                case "RAIN"  -> set.add("비");
                case "HUMID" -> set.add("습도");
                case "WIND"  -> set.add("강풍");
                case "SUN"   -> set.add("강한 햇빛");
            }
        }
        return new ArrayList<>(set);
    }

    /** ACT:FLIGHT, MUSEUM_HERITAGE → ["공항 환승/보안검색", "박물관·유적 관람"] */
    private static List<String> parseActivities(String notes) {
        if (isBlank(notes)) return List.of();
        Matcher m = Pattern.compile("ACT:([A-Z_,\\s]+)").matcher(notes.toUpperCase(Locale.ROOT));
        if (!m.find()) return List.of();
        String[] codes = m.group(1).split("\\s*,\\s*");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String c : codes) {
            switch (c) {
                case "FLIGHT"           -> out.add("공항 환승/보안검색");
                case "MUSEUM_HERITAGE"  -> out.add("박물관·유적 관람");
                case "BEACH"            -> out.add("해변/수영");
                case "HIKING"           -> out.add("하이킹/트레킹");
                case "SHOPPING"         -> out.add("쇼핑");
                case "TOURING"          -> out.add("도시 투어");
                case "DINING"           -> out.add("식사/레스토랑");
                case "THEMEPARK"        -> out.add("테마파크");
                default -> {
                    if (!c.isBlank()) out.add(c); // 미정의 코드는 원문 토큰으로
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** HIST: +15% (REC: 최근5중 3) → gain=15, unit="%", recPhrase="(최근 5회 중 3회)" */
    private static class HistInfo {
        String gain; // 숫자
        String unit; // "%" or "p"
        String recPhrase; // "(최근 X회 중 Y회)"
    }

    private static HistInfo parseHist(String notes) {
        if (isBlank(notes)) return null;
        Matcher m = Pattern.compile(
                "HIST:\\s*\\+\\s*(\\d+)\\s*(%|p)(?:\\s*\\(REC:\\s*최근\\s*(\\d+)중\\s*(\\d+)\\))?",
                Pattern.CASE_INSENSITIVE).matcher(notes);
        if (!m.find()) return null;
        HistInfo hi = new HistInfo();
        hi.gain = m.group(1);
        hi.unit = m.group(2);
        if (m.group(3) != null && m.group(4) != null) {
            hi.recPhrase = "(최근 " + m.group(3) + "회 중 " + m.group(4) + "회)";
        } else {
            hi.recPhrase = "";
        }
        return hi;
    }

    /* 유틸 */
    private static String trim1(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
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


