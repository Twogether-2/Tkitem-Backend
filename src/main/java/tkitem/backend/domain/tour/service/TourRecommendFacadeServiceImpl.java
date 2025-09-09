package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.KeywordRule;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
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
    @Transactional
    public List<TourRecommendationResponseDto> recommend(TourRecommendationRequestDto req, String queryText, int topN, Member member) throws Exception {

        // 1. 사용자 입력 없으면 DB score 랭킹 상위 topN 개 반환
        boolean useEs = queryText != null && !queryText.isBlank();

        // DB 후보 1차 계산
        int baseCount = useEs ? Math.max(DB_STAGE_TOP, topN) : topN;
        List<TourRecommendationResponseDto> base = tourRecommendService.recommendDbOnly(req, baseCount, member);
        if (base == null || base.isEmpty()) return Collections.emptyList();

        // ES 없을 때. DB 로만 계산
        if(!useEs){
            int total = base.size();
            int show = Math.min(total, topN);
            log.info("[DB-ONLY] totalCandidates={}, willShowTopN={}", total, show);
            for (int i = 0; i < show; i++) {
                TourRecommendationResponseDto r = base.get(i);
                log.info("[DB-ONLY][{}] tourId={}, title='{}', price={}, dep={}, ret={}, pkgId={}, dbScore={}, finalScore={}",
                        i, r.getTourId(), r.getTitle(), r.getPrice(), r.getDepartureDate(), r.getReturnDate(),
                        r.getTourPackageId(), r.getDbScore(), r.getFinalScore());
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

            for(TourRecommendationResponseDto dto : base){
                double dbN = NumberUtil.toDoubleOrZero(dto.getDbScore());
                double esRaw = NumberUtil.toDoubleOrZero(sEsMap.getOrDefault(dto.getTourId(), 0.0));
                double esN = (esMax > 0.0) ? (esRaw / esMax) : 0.0;
                double finalScore = ALPHA_DB*dbN + BETA_ES*esN;

                dto.setEsScore(esN);
                dto.setFinalScore(finalScore);
            }
        }

        // 4. 최종 점수별 정렬
        base.sort(Comparator
                .comparing(TourRecommendationResponseDto::getFinalScore).reversed()
                .thenComparing(TourRecommendationResponseDto::getPrice, Comparator.nullsLast(Long::compareTo))
                .thenComparing(TourRecommendationResponseDto::getDepartureDate, Comparator.nullsLast(Date::compareTo)));

        // 추천 결과 저장
        List<TourRecommendationResponseDto> dtos =
                tourRecommendService.enrichRecommendationDetails(base.subList(0, Math.min(topN, base.size())));

        if (!useEs && !dtos.isEmpty() && dtos.get(0).getSchedules() != null) {
            for (TourDetailScheduleDto tdsItem : dtos.get(0).getSchedules()) {
                log.info("title = {}, sortOrder = {}, scheduleDay = {}, defaultType = {}",
                        tdsItem.getTitle(), tdsItem.getSortOrder(), tdsItem.getScheduleDay(), tdsItem.getDefaultType());
            }
        }

        tourRecommendService.saveShownRecommendations(req.getGroupId(), dtos, member);

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<TourCommonRecommendDto> searchByKeyword(String keyword) {
        try {
            if (keyword == null || keyword.isBlank()) {
                throw new BusinessException("keyword 파라미터가 비어 있습니다.", ErrorCode.INVALID_INPUT_VALUE);
            }

            // 1) 룰 조회 (없으면 404)
            KeywordRule rule = ruleLoader.getRequired(keyword); // BusinessException 발생 가능

            // 2) 허용 투어 집합 (DB) — 비어도 진행
            Set<Long> allow = new HashSet<>();
            if (rule.getCountry() != null && !rule.getCountry().isBlank()) {
                allow.addAll(tourMapper.selectAllowTourIdsByCountry(rule.getCountry()));
            }
            if (rule.getCountryGroup() != null && !rule.getCountryGroup().isBlank()) {
                allow.addAll(tourMapper.selectAllowTourIdsByCountryGroup(rule.getCountryGroup()));
            }

            // 3) ES 통합 스코어는 전부 TourEsService에서 처리
            Map<Long, Double> finalScores =
                    tourEsService.searchByKeywordScores(rule, allow, /*k*/2000, /*candidates*/4000, /*mTop*/3, /*wKnn*/0.7, /*wBm25*/0.3);

            if (finalScores.isEmpty()) {
                throw new BusinessException("검색 결과가 없습니다.", ErrorCode.ENTITY_NOT_FOUND);
            }

            // 4) 점수로 정렬한 뒤, 상위 50개 tourId 만 추출
            List<Long> topIds = finalScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(50)
                    .map(Map.Entry::getKey)
                    .toList();

            // DTO는 기본 생성자 + setter로 tourId만 세팅
            List<TourCommonRecommendDto> result = new ArrayList<>(topIds.size());
            for (Long id : topIds) {
                TourCommonRecommendDto dto = new TourCommonRecommendDto();
                dto.setTourId(id);
                result.add(dto);
            }



            return result;

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[Facade.searchByKeyword] unexpected error: {}", e.getMessage(), e);
            throw new BusinessException( "키워드 검색 중 오류: " + e.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}