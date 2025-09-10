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

        // DB 후보 1차 계산
        int baseCount = useEs ? Math.max(DB_STAGE_TOP, topN) : topN;
        List<TourRecommendationResponseDto> base = tourRecommendService.recommendDbOnly(req, baseCount, member);
        if (base == null || base.isEmpty()) return Collections.emptyList();

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
            Set<Long> allowIds = base.stream().map(TourRecommendationResponseDto::getTourId).collect(Collectors.toSet());
            Map<Long, Double> sEsMap = tourEsService.computeEsScores(queryText, allowIds, ES_K, ES_CANDIDATES, ES_MTOP);

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

        // ADDED: 유틸 Comparator
        Comparator<TourRecommendationResponseDto> byFinalDesc = Comparator.comparing(
                TourRecommendationResponseDto::getFinalScore,
                Comparator.nullsFirst(Double::compareTo)    // null 점수는 가장 뒤로
        ).reversed();

        Comparator<TourRecommendationResponseDto> byRepPriceAsc = Comparator.comparing(
                r -> minPrice(r.getPackageDtos()),
                Comparator.nullsLast(Long::compareTo)       // 가격 없는 경우 뒤로
        );

        Comparator<TourRecommendationResponseDto> byRepDepartureAsc = Comparator.comparing(
                r -> earliestDeparture(r.getPackageDtos()),
                Comparator.nullsLast(Date::compareTo)       // 날짜 없는 경우 뒤로
        );

// CHANGED: 정렬 기준 교체
        base.sort(
                byFinalDesc
                        .thenComparing(byRepPriceAsc)
                        .thenComparing(byRepDepartureAsc)
                        .thenComparing(TourRecommendationResponseDto::getTourId, Comparator.nullsLast(Long::compareTo)) // 안정적 tie-break
        );

        // 추천 결과 저장
        List<TourRecommendationResponseDto> dtos =
                tourRecommendService.enrichRecommendationDetails(base.subList(0, Math.min(topN, base.size())));

//        if (!useEs && !dtos.isEmpty() && dtos.get(0).getSchedules() != null) {
//            for (TourDetailScheduleDto tdsItem : dtos.get(0).getSchedules()) {
//                log.info("title = {}, sortOrder = {}, scheduleDay = {}, defaultType = {}",
//                        tdsItem.getTitle(), tdsItem.getSortOrder(), tdsItem.getScheduleDay(), tdsItem.getDefaultType());
//            }
//        }

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