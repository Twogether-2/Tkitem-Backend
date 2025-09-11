package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.KeywordRule;
import tkitem.backend.domain.tour.dto.TopMatchDto;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.logic.KeywordRuleLoader;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.util.NumberUtil;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourRecommendFacadeServiceImpl implements TourRecommendFacadeService {

    private final TourRecommendService tourRecommendService;
    private final TourEsService tourEsService;
    private final TourLLmService tourLLmService;

    private final TourMapper tourMapper;
    private final KeywordRuleLoader ruleLoader;

    // 가중치 설정.
    private static final double ALPHA_DB = 0.6;     // DB 점수 가중
    private static final double BETA_ES  = 0.4;     // ES 점수 가중
    private static final int    ES_K = 2000;        // ES kNN k. 후보군 수
    private static final int    ES_CANDIDATES = 4000;
    private static final int    ES_MTOP = 3;        // 투어별 상위 m개 평균
    private static final int    DB_STAGE_TOP = 200; // 최소 1차 후보 확보량

    @Override
    public TourCommonRecommendDto searchTop1ByKeyword(String keyword) {
        if(keyword == null || keyword.isBlank()){
            throw new BusinessException("keyword is blank", ErrorCode.INVALID_INPUT_VALUE);
        }

        // keyword.json 룰 로드
        KeywordRule rule = ruleLoader.getRequired(keyword);

        // 허용 tourId 조회 (country 우선, 없으면 countryGroup)
        Set<Long> allow = null;
        if (rule.getCountry() != null && !rule.getCountry().isBlank()) { // [ADDED]
            List<Long> ids = tourMapper.selectAllowTourIdsByCountry(rule.getCountry()); // [ADDED]
            allow = (ids == null || ids.isEmpty()) ? Collections.emptySet() : new HashSet<>(ids); // [ADDED]
        } else if (rule.getCountryGroup() != null && !rule.getCountryGroup().isBlank()) { // [ADDED]
            List<Long> ids = tourMapper.selectAllowTourIdsByCountryGroup(rule.getCountryGroup()); // [ADDED]
            allow = (ids == null || ids.isEmpty()) ? Collections.emptySet() : new HashSet<>(ids); // [ADDED]
        }

        String rawJson = tourEsService.searchTop1ByVectorRawJson(rule, allow);

        TopMatchDto topMatchDto = tourEsService.sendRawEsQuery(rawJson);
        log.info("tourId : {}, score : {}", topMatchDto.tourId(), topMatchDto.score());

        return tourMapper.selectTourMetaByTourIds(List.of(topMatchDto.tourId())).getFirst();
    }

    @Override
    @Transactional
    public List<TourRecommendationResponseDto> recommend(TourRecommendationRequestDto req, String queryText, int topN, Member member) throws Exception {

        // 1. 사용자 입력 없으면 DB score 랭킹 상위 topN 개 반환
        boolean useEs = queryText != null && !queryText.isBlank();

        // TODO : DB 검색만 7초정도 지연시간 발생. DB 조회시간 최적화 필요
        // DB 후보 1차 계산
        int baseCount = useEs ? Math.max(DB_STAGE_TOP, topN) : topN;
        log.info("DB후보계산 시작");
        List<TourRecommendationResponseDto> base = tourRecommendService.recommendDbOnly(req, baseCount, member);
        if (base == null || base.isEmpty()) return Collections.emptyList();
        log.info("DB후보계산 완료 : {}", base.size());

        // ES 없을 때. DB 로만 계산
        if (!useEs) {
            int total = base.size();
            int show = Math.min(total, topN);
            log.info("[DB-ONLY] totalCandidates={}, willShowTopN={}", total, show);
            for (int i = 0; i < show; i++) {
                TourRecommendationResponseDto r = base.get(i);
                log.info("[DB-ONLY][{}] tourId={}, title='{}', price={}, dep={}, ret={}, pkgId={}, dbScore={}, finalScore={}",
                        i, r.getTourId(), r.getTitle(), r.getPackageDtos().getFirst().getPrice(), r.getPackageDtos().getFirst().getDepartureDate(), r.getPackageDtos().getFirst().getReturnDate(),
                        r.getPackageDtos().getFirst().getTourPackageId(), r.getDbScore(), r.getFinalScore());
            }
            if (show == 0) log.info("[DB-ONLY] no candidates.");
        }

        // ES 입력도 있을 때 DB + ES 합쳐서 계산
        else {
            // 2. ES KNN 집계
            List<Long> allowIds = tourMapper.selectTourIdsByFilters(req.getDepartureDate(), req.getReturnDate(), req.getPriceMin(), req.getPriceMax(), req.getLocations());
            log.info("[RE] allowids size from filtering db = {}", allowIds.size());

            // GEMINI 로 queryText -> should/exclude 분류
            KeywordRule rule = tourLLmService.buildRuleFromQueryText(queryText);

            // ES 호출 파라미터
            final int TOP_N_ES = 200;

            // TODO : ES 검색시 TOUR 상위 10개만 나오는거, 200개는 찾게 수정
            // ES 호출 (collapse + inner_hits 상위 m개 평균)
            List<TopMatchDto>esTop = tourEsService.sendRawEsQueryTopNAllowed(
                    rule, allowIds, TOP_N_ES, ES_K, ES_CANDIDATES, ES_MTOP
            );

            Map<Long, Double> sEsMap = esTop.stream()
                    .collect(Collectors.toMap(TopMatchDto::tourId, TopMatchDto::score,(a, b) -> Math.max(a, b)));
            log.info("ES KNN 집계 완료 : hits={},  distinctTours={}", esTop.size(), sEsMap.size());
            for(Map.Entry<Long, Double> e : sEsMap.entrySet()){
                log.info("tourId : {}, score: {}", e.getKey(), e.getValue());
            }

            // 3. ES 점수 정규화 + 가중합
            double esMax = allowIds.stream()
                    .mapToDouble(id -> NumberUtil.toDoubleOrZero(sEsMap.getOrDefault(id, 0.0)))
                    .max().orElse(1.0); // 전부 0 인 경우 분모에 0 들어가는거 방지

            for (TourRecommendationResponseDto dto : base) {
                double dbN = NumberUtil.toDoubleOrZero(dto.getDbScore());
                double esRaw = NumberUtil.toDoubleOrZero(sEsMap.getOrDefault(dto.getTourId(), 0.0));
                double esN = (esMax > 0.0) ? (esRaw / esMax) : 0.0;
                double finalScore = ALPHA_DB * dbN + BETA_ES * esN;

                dto.setEsScore(esN);
                dto.setFinalScore(finalScore);
            }
        }

        // =========================
        // 4) 최종 정렬
        //    - 일자/가격 필터가 있으면: 출발일 오름차순
        //    - 없으면: 기존 점수 기반 정렬
        // =========================
        boolean hasDateFilter  = (req != null) && (req.getDepartureDate() != null || req.getReturnDate() != null);
        boolean hasPriceFilter = (req != null) && (req.getPriceMin() != null || req.getPriceMax() != null);

        Comparator<TourRecommendationResponseDto> byFinalDesc = Comparator.comparing(
                TourRecommendationResponseDto::getFinalScore,
                Comparator.nullsFirst(Double::compareTo)
        ).reversed();

        Comparator<TourRecommendationResponseDto> byMinPriceAsc = Comparator.comparing(
                r -> minPrice(r.getPackageDtos()),
                Comparator.nullsLast(Long::compareTo)
        );

        Comparator<TourRecommendationResponseDto> byEarliestDepAsc = Comparator.comparing(
                r -> earliestDeparture(r.getPackageDtos()),
                Comparator.nullsLast(Date::compareTo)
        );

        if (hasDateFilter || hasPriceFilter) {
            // 요청하신 기준: 일자 빠른 순만 적용(필요 시 tourId로 tie-break)
            base.sort(
                    Comparator
                            .comparing((TourRecommendationResponseDto r) -> earliestDeparture(r.getPackageDtos()),
                                    Comparator.nullsLast(Date::compareTo))
                            .thenComparing(TourRecommendationResponseDto::getTourId, Comparator.nullsLast(Long::compareTo))
            );
        } else {
            // 기존 점수 기반 정렬
            base.sort(
                    byFinalDesc
                            .thenComparing(byMinPriceAsc)
                            .thenComparing(byEarliestDepAsc)
                            .thenComparing(TourRecommendationResponseDto::getTourId, Comparator.nullsLast(Long::compareTo))
            );
        }

        // 추천 결과 저장
        List<TourRecommendationResponseDto> dtos =
                tourRecommendService.enrichRecommendationDetails(base.subList(0, Math.min(topN, base.size())));

        tourRecommendService.saveShownRecommendations(req.getGroupId(), dtos, member);

        return dtos;
    }

    private static Long minPrice(List<TourPackageDto> pkgs) {
        if (pkgs == null || pkgs.isEmpty()) return null;
        return pkgs.stream()
                .map(TourPackageDto::getPrice)
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);
    }

    private static Date earliestDeparture(List<TourPackageDto> pkgs) {
        if (pkgs == null || pkgs.isEmpty()) return null;
        return pkgs.stream()
                .map(TourPackageDto::getDepartureDate)
                .filter(Objects::nonNull)
                .min(Date::compareTo)
                .orElse(null);
    }
}