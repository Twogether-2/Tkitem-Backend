package tkitem.backend.domain.product_recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.product_recommendation.dto.*;
import tkitem.backend.domain.product_recommendation.dto.request.ProductRecommendationRequest;
import tkitem.backend.domain.product_recommendation.dto.response.CandidateListResponse;
import tkitem.backend.domain.product_recommendation.dto.response.ProductRecommendationResponse;
import tkitem.backend.domain.product_recommendation.mapper.ProductRecommendationMapper;
import tkitem.backend.domain.product_recommendation.util.TagExtractor;

import java.math.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductRecommendationServiceImpl implements ProductRecommendationService {

    private final ProductRecommendationMapper productRecommendationMapper;

    @Override
    @Transactional(readOnly = true)
    public ProductRecommendationResponse planWithBudget(
            Long tripId,
            List<Long> checklistItemIds,
            BigDecimal budget,
            ProductRecommendationRequest.Weights weights,
            String scheduleDateParam,
            int perItemCandidates,
            int step
    ) {
        if (weights == null) weights = new ProductRecommendationRequest.Weights(0.7, 0.3);
        double wTag = Optional.ofNullable(weights.getTag()).orElse(0.7);
        double wRating = Optional.ofNullable(weights.getRating()).orElse(0.3);

        // 1) 아이템 조회(+tripId 검증) 및 scheduleDate 필터
        List<ChecklistItemDto> items = productRecommendationMapper.selectChecklistItemsByIds(tripId, checklistItemIds);
        items = applyScheduleDateFilter(items, scheduleDateParam);

        // 2) 각 아이템별 후보 수집 (loop)
        Map<Long, List<CandidateProductDto>> candidatesByItem = new LinkedHashMap<>();
        for (ChecklistItemDto it : items) {
            Set<String> tagCodes = TagExtractor.extractTagCodes(it.getNotes(), it.getItemName());

            List<CandidateProductDto> cands = tagCodes.isEmpty()
                    ? productRecommendationMapper.selectPopularCandidatesFallback(it.getProductCategorySubId(), perItemCandidates)
                    : productRecommendationMapper.selectCandidatesForItem(it.getProductCategorySubId(), new ArrayList<>(tagCodes), perItemCandidates);

            // 태그는 있었지만 매칭 0건 → 인기/평점 백업
            if (cands == null || cands.isEmpty()) {
                cands = productRecommendationMapper.selectPopularCandidatesFallback(it.getProductCategorySubId(), perItemCandidates);
            }
            candidatesByItem.put(it.getChecklistItemId(), cands);
        }

        // 3) 유틸리티 정규화(태그 점수 max)
        double maxTag = 0;
        for (List<CandidateProductDto> list : candidatesByItem.values()) {
            for (CandidateProductDto c : list) {
                if (c.getTagScore() != null) {
                    maxTag = Math.max(maxTag, c.getTagScore().doubleValue());
                }
            }
        }
        if (maxTag <= 0) maxTag = 1; // 0 division 보호

        // 4) 그룹(아이템)별 옵션 구성 (skip 포함)
        List<Long> itemOrder = items.stream().map(ChecklistItemDto::getChecklistItemId).toList();
        List<List<Option>> groups = new ArrayList<>();
        for (ChecklistItemDto it : items) {
            List<Option> opts = new ArrayList<>();
            // skip 옵션
            opts.add(Option.skip());
            for (CandidateProductDto c : candidatesByItem.getOrDefault(it.getChecklistItemId(), List.of())) {
                double price = c.getPrice().doubleValue();
                double tagNorm = Math.max(0, (c.getTagScore()==null?0.0:c.getTagScore().doubleValue()) / maxTag);
                double ratingNorm = Math.max(0, (c.getAvgReview()==null?0.0:c.getAvgReview().doubleValue()) / 5.0);
                double utility = wTag * tagNorm + wRating * ratingNorm;
                opts.add(Option.pick(
                        c.getProductId(), c.getName(), price, utility, c.getMatchedTags(),
                        c.getBrandName(), c.getCategory(), c.getImgUrl()
                ));
            }
            groups.add(opts);
        }

        // 5) DP (Multiple-Choice Knapsack) — 총예산 제약
        int cap = budget.divide(new BigDecimal(step), 0, RoundingMode.DOWN).intValue();
        double[] dp = new double[cap+1];
        int[][] pick = new int[groups.size()][cap+1];
        int[][] prev = new int[groups.size()][cap+1];

        for (int g=0; g<groups.size(); g++) {
            Arrays.fill(pick[g], -1);
            Arrays.fill(prev[g], -1);
            double[] next = Arrays.copyOf(dp, dp.length);

            List<Option> opts = groups.get(g);
            for (int oi=0; oi<opts.size(); oi++) {
                Option o = opts.get(oi);
                int w = (int)Math.floor(o.cost / step);
                if (w < 0) w = 0;
                for (int c = cap; c >= w; c--) {
                    double cand = dp[c - w] + o.utility;
                    if (cand > next[c]) {
                        next[c] = cand;
                        pick[g][c] = oi;
                        prev[g][c] = c - w;
                    }
                }
            }
            dp = next;
        }

        // 6) 역추적
        int bestC = 0; double bestV = dp[0];
        for (int c=1; c<=cap; c++) if (dp[c] > bestV) { bestV = dp[c]; bestC = c; }

        Map<Long, PlannedPickDto> bestByItem = new LinkedHashMap<>();
        int c = bestC;
        for (int g = groups.size()-1; g>=0; g--) {
            int oi = pick[g][c];
            if (oi < 0) continue;
            Option o = groups.get(g).get(oi);
            if (!o.isSkip) {
                PlannedPickDto pickDto = PlannedPickDto.builder()
                        .productId(o.productId)
                        .name(o.name)
                        .brandName(o.brandName)
                        .category(o.category)
                        .imgUrl(o.imgUrl)
                        .price(BigDecimal.valueOf(o.cost))
                        .utility(o.utility)
                        .matchedTags(o.matchedTags)
                        .build();
                bestByItem.put(itemOrder.get(g), pickDto);
            }
            c = Math.max(0, prev[g][c]);
        }

        // 7) 응답 구성 + 그룹핑 + 합계
        Map<Integer, List<ItemRecommendationResultDto>> grouped = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        double totalUtility = 0.0;

        for (ChecklistItemDto it : items) {
            PlannedPickDto p = bestByItem.get(it.getChecklistItemId());
            if (p != null) {
                totalCost = totalCost.add(p.getPrice());
                totalUtility += p.getUtility();
            }
            grouped.computeIfAbsent(it.getScheduleDate(), k -> new ArrayList<>())
                    .add(ItemRecommendationResultDto.builder()
                            .checklistItemId(it.getChecklistItemId())
                            .product(p)
                            .build());
        }

        List<ScheduleGroupDto> groupsOut = grouped.entrySet().stream()
                .sorted((a, b) -> {
                    Integer ka = a.getKey(), kb = b.getKey();
                    if (ka == null && kb == null) return 0;
                    if (ka == null) return 1;
                    if (kb == null) return -1;
                    return Integer.compare(ka, kb);
                })
                .map(e -> ScheduleGroupDto.builder()
                        .scheduleDate(e.getKey())
                        .items(e.getValue())
                        .build())
                .collect(Collectors.toList());

        BigDecimal remaining = budget.subtract(totalCost.max(BigDecimal.ZERO));

        return ProductRecommendationResponse.builder()
                .tripId(tripId)
                .budget(budget)
                .totalCost(totalCost)
                .totalUtility(totalUtility)
                .remaining(remaining)
                .groups(groupsOut)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateListResponse getCandidatesForChecklistItem(Long tripId, Long checklistItemId, int limit) {
        // 0) limit 가드
        int topN = Math.max(1, Math.min(limit, 50));

        // 1) checklist item 검증 + 메타 확보 (tripId 스코프)
        List<ChecklistItemDto> items = productRecommendationMapper.selectChecklistItemsByIds(tripId, List.of(checklistItemId));
        if (items.isEmpty()) {
            // 프로젝트에 EntityNotFoundException 있으면 그거 사용
            throw new IllegalArgumentException("tripId=" + tripId + " 범위에 checklistItemId=" + checklistItemId + "가 없습니다.");
        }
        ChecklistItemDto it = items.get(0);

        // 2) 태그 추출 (notes + itemName). TagExtractor가 Set<String>이든 List<String>이든 안전 처리
        Set<String> extracted = Optional.ofNullable(
                TagExtractor.extractTagCodes(
                        Optional.ofNullable(it.getNotes()).orElse(""),
                        Optional.ofNullable(it.getItemName()).orElse("")
                )
        ).orElseGet(Collections::emptySet);
        ArrayList<String> ctxTagCodes = new ArrayList<>(extracted);

        // 3) 태그 매칭 후보 → 없으면 인기/평점 백업
        List<CandidateProductDto> candidates = Collections.emptyList();
        if (!ctxTagCodes.isEmpty()) {
            candidates = productRecommendationMapper.selectCandidatesForItem(it.getProductCategorySubId(), ctxTagCodes, topN);
        }
        if (candidates == null || candidates.isEmpty()) {
            candidates = productRecommendationMapper.selectPopularCandidatesFallback(it.getProductCategorySubId(), topN);
        }

        // 4) 응답 (tripId 포함해 세팅)
        return CandidateListResponse.of(tripId, it, ctxTagCodes, candidates);
    }

    private List<ChecklistItemDto> applyScheduleDateFilter(List<ChecklistItemDto> items, String scheduleDateParam) {
        if (scheduleDateParam == null) return items;
        if ("null".equalsIgnoreCase(scheduleDateParam)) {
            return items.stream().filter(i -> i.getScheduleDate() == null).toList();
        }
        try {
            int want = Integer.parseInt(scheduleDateParam);
            return items.stream().filter(i -> Objects.equals(i.getScheduleDate(), want)).toList();
        } catch (NumberFormatException e) {
            return items;
        }
    }

    // 내부 DP 옵션
    // ProductRecommendationServiceImpl 내부

    private static final class Option {
        final boolean isSkip;
        final Long productId;
        final String name;
        final String brandName;
        final String category;
        final String imgUrl;
        final double cost;
        final double utility;
        final String matchedTags;

        private Option(boolean isSkip, Long pid, String name, double cost, double utility, String tags,
                       String brandName, String category, String imgUrl) {
            this.isSkip = isSkip; this.productId = pid; this.name = name;
            this.cost = cost; this.utility = utility; this.matchedTags = tags;
            this.brandName = brandName; this.category = category; this.imgUrl = imgUrl;
        }
        static Option skip() { return new Option(true, null, null, 0.0, 0.0, null, null, null, null); }
        static Option pick(Long id, String name, double cost, double util, String tags,
                           String brandName, String category, String imgUrl) {
            return new Option(false, id, name, cost, util, tags, brandName, category, imgUrl);
        }
    }


}


