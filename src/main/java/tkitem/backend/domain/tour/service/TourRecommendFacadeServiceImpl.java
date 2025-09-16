package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.KeywordRule;
import tkitem.backend.domain.tour.dto.TopMatchDto;
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

        // 지역, 날짜, 추천기록에 속하지 않는 허용 투어 ID 목록 조회
        long touridTime = System.nanoTime();
        List<Long> allowIds = tourMapper.selectTourIdsByFilters(req.getDepartureDate(), req.getReturnDate(), req.getPriceMin(), req.getPriceMax(), req.getLocations(), member.getMemberId(), req.getGroupId());
        touridTime = System.nanoTime() - touridTime;
        log.info("[RECOMMEND] allowids size from filtering db = {}, time = {}", allowIds.size(), touridTime/1_000_000);

        // DB 후보 1차 계산
        int baseCount = useEs ? Math.max(DB_STAGE_TOP, topN) : topN;
        log.info("[RECOMMEND]DB후보계산 시작");
        List<TourRecommendationResponseDto> base = tourRecommendService.recommendDbOnly(req, baseCount, member, allowIds);
        if (base == null || base.isEmpty()) return Collections.emptyList();
        log.info("[RECOMMEND]DB후보계산 완료 : {}", base.size());

        // ES 없을 때. DB 로만 계산
        if (!useEs) {
            int total = base.size();
            int show = Math.min(total, topN);
            log.info("[DB-ONLY] totalCandidates={}, willShowTopN={}", total, show);
            if (show == 0) log.info("[DB-ONLY] no candidates.");
        }

        // ES 입력도 있을 때 DB + ES 합쳐서 계산
        else {

            // GEMINI 로 queryText -> should/exclude 분류
            KeywordRule rule = null;
//                    tourLLmService.buildRuleFromQueryText(queryText);

            // ES 호출 파라미터
            final int TOP_N_ES = 200;


            // BM25 먼저 요청 → (부족: hit=0 or max_score<8.0)일 때만 kNN도 호출하여 ES 점수 생성
            TourEsService.HybridResult hr =
                    tourEsService.searchHybridSimple(queryText, new java.util.HashSet<>(allowIds), TOP_N_ES, rule);

            // 기존 변수명 유지: sEsMap (tourId -> ES 점수)
            Map<Long, Double> sEsMap = new HashMap<>(hr.scores);
            log.info("ES 하이브리드 완료 : bm25Hits={}, bm25Max={}, usedVector={}, distinctTours={}",
                    hr.bm25Hits, String.format(java.util.Locale.ROOT, "%.3f", hr.bm25MaxScore), hr.usedVector, sEsMap.size());

            double dbMax = base.stream()
                    .mapToDouble(dto -> NumberUtil.toDoubleOrZero(dto.getDbScore()))
                    .max().orElse(1.0);
            // 3. ES 점수 정규화 + 가중합
            double esMax = allowIds.stream()
                    .mapToDouble(id -> NumberUtil.toDoubleOrZero(sEsMap.getOrDefault(id, 0.0)))
                    .max().orElse(1.0); // 전부 0 인 경우 분모에 0 들어가는거 방지

            for (TourRecommendationResponseDto dto : base) {
                double dbRaw = NumberUtil.toDoubleOrZero(dto.getDbScore());
                double esRaw = NumberUtil.toDoubleOrZero(sEsMap.getOrDefault(dto.getTourId(), 0.0));
                double dbN = (dbMax > 0.0) ? (dbRaw / dbMax) : 0.0;  // DB 정규화
                double esN = (esMax > 0.0) ? (esRaw / esMax) : 0.0;  // ES 정규화

                double finalScore = 0.5 * dbN + 0.5 * esN;           // 5:5 합산

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
            base.sort(
                    byFinalDesc
                            .thenComparing(byEarliestDepAsc)
                            .thenComparing(byMinPriceAsc)
                            .thenComparing(TourRecommendationResponseDto::getTourId, Comparator.nullsLast(Long::compareTo))
            );
        } else {
            base.sort(
                    byFinalDesc
                            .thenComparing(byMinPriceAsc)
                            .thenComparing(byEarliestDepAsc)
                            .thenComparing(TourRecommendationResponseDto::getTourId, Comparator.nullsLast(Long::compareTo))
            );
        }

        // 상위 5개만 투어 패키지, 세부일정 채우기
        List<TourRecommendationResponseDto> dtos = tourRecommendService.fillTopNPackage(base.subList(0, Math.min(topN, base.size())), req, member, 5);

        // 추천 결과 저장
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