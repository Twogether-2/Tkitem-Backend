package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.TourCandidateRowDto;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.global.util.NumberUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourRecommendService {

    private final TourMapper tourMapper;

    private static final Integer kTop = 10;

    /**
     * DB 점수만으로 Top-N 추천
     * @param req 출발일/가격/지역/태그/kTop 파라미터
     * @param topN 상위 N개
     * @return
     */
    @Transactional(readOnly = true)
    public List<TourRecommendationResponseDto> recommendDbOnly(TourRecommendationRequestDto req, int topN, Member member) {

        log.info("DB 투어추천 시작");
        List<TourCandidateRowDto> tourCandidateRowDtos = tourMapper.selectTourCandidates(req, kTop, member.getMemberId());
        log.info("DB 투어추천 완료 : {}", tourCandidateRowDtos.size());

        if(tourCandidateRowDtos.isEmpty()) return Collections.emptyList();

        // TODO : 후보계산이 진짜 오래걸림
        // min-max 정규화(S_DB 만)
        double dbMin = tourCandidateRowDtos.stream().mapToDouble(r -> NumberUtil.toDoubleOrZero(r.getSDbRaw())).min().orElse(0);
        double dbMax = tourCandidateRowDtos.stream().mapToDouble(r -> NumberUtil.toDoubleOrZero(r.getSDbRaw())).max().orElse(1);

        List<TourRecommendationResponseDto> ranked = tourCandidateRowDtos.stream().map(r -> {
            double db = NumberUtil.toDoubleOrZero(r.getSDbRaw());
            double dbNorm = (dbMax > dbMin) ? (db - dbMin) / (dbMax - dbMin) : 0.0;

            return TourRecommendationResponseDto.builder()
                    .tourId(r.getTourId())
                    .dbScore(dbNorm) // DB 정규화 점수
                    .esScore(0.0) // ES 하기 전이라 아직 0.0
                    .finalScore(dbNorm) // 최종점수에 아직 DB 점수만 활용함
                    .build();
        }).sorted(Comparator.comparing(TourRecommendationResponseDto::getFinalScore).reversed())
                .collect(Collectors.toList());

        // 4) Top-N 투어만 선택 후, 해당 투어들의 "모든 패키지" 조회하여 주입
        List<TourRecommendationResponseDto> top = ranked.subList(0, Math.min(topN, ranked.size()));
        List<Long> tourIds = top.stream().map(TourRecommendationResponseDto::getTourId).toList();

        // 투어별 모든 패키지 행 조회 (XML: selectPackagesForTours)
        List<TourPackageDto> pkgRows = tourMapper.selectPackagesForTours(req, member.getMemberId(), tourIds);

        // tourId 기준 그룹핑 → DTO 주입
        Map<Long, List<TourPackageDto>> grouped = pkgRows.stream()
                .collect(Collectors.groupingBy(TourPackageDto::getTourId));

        for (TourRecommendationResponseDto dto : top) {
            List<TourPackageDto> list = grouped.get(dto.getTourId());
            dto.setPackageDtos(list != null ? list : Collections.emptyList());
        }
        return top;
    }

    /**
     * 분류가 끝난 dto 의 데이터를 추가적으로 채워서 전달하기 위한 메서드
     * @param items
     * @return
     */
    @Transactional
    public List<TourRecommendationResponseDto> enrichRecommendationDetails(List<TourRecommendationResponseDto> items){
        if(items == null || items.isEmpty()) return items;

        // 1. 인덱싱
        Map<Long, TourRecommendationResponseDto> byId = new ConcurrentHashMap<>();
        List<Long> tourIds = new ArrayList<>(items.size());
        for(TourRecommendationResponseDto item : items){
            if(item == null || item.getTourId() == null) continue;
            byId.put(item.getTourId(), item);
            tourIds.add(item.getTourId());
        }

        // 2. TOUR 데이터 조회
        List<Map<String, Object>> tourMetaDatas = tourMapper.selectTourMetaByIds(tourIds);
        for(Map<String, Object> row : tourMetaDatas){
            Long tourId = ((Number) row.get("tourId")).longValue();
            TourRecommendationResponseDto dto = byId.get(tourId);
            if(dto == null) continue;
            dto.setTitle((String) row.get("title"));
            dto.setFeature((String) row.get("feature"));
            dto.setImgUrl((String) row.get("imgUrl"));
            dto.setProvider((String) row.get("provider"));
        }

        // 3. TDS 조회 및 매핑
        List<Map<String, Object>> tdsRows = tourMapper.selectTdsByTourIds(tourIds);
        Map<Long, List<TourDetailScheduleDto>> schedulesByTour = new HashMap<>();
        for (Map<String, Object> r : tdsRows) {
            Long tourId = ((Number) r.get("tourId")).longValue();
            TourDetailScheduleDto item = TourDetailScheduleDto.builder()
                    .tourDetailScheduleId(((Number) r.get("tourDetailScheduleId")).longValue())
                    .cityId(r.get("cityId") == null ? null : ((Number) r.get("cityId")).longValue())
                    .countryName((String) r.get("countryName"))
                    .cityName((String) r.get("cityName"))
                    .title((String) r.get("title"))
                    .description((String) r.get("description"))
                    .sortOrder(r.get("sortOrder") == null ? null : ((Number) r.get("sortOrder")).intValue())
                    .defaultType((String) r.get("defaultType"))
                    .scheduleDay(r.get("scheduleDay") == null ? 0 : ((Number) r.get("scheduleDay")).intValue())
                    .build();
            schedulesByTour.computeIfAbsent(tourId, k -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<Long, List<TourDetailScheduleDto>> e : schedulesByTour.entrySet()) {
            TourRecommendationResponseDto dto = byId.get(e.getKey());
            if (dto != null) dto.setSchedules(e.getValue());
        }

        return items;
    }

    @Transactional
    public void saveShownRecommendations(Long groupId, List<TourRecommendationResponseDto> items, Member member){
        if (items == null || items.isEmpty()) return;

        // 신규추천이면(groupId가 null 또는 0 이면 MAX+1 생성
        if(groupId == null || groupId == 0L) groupId = tourMapper.selectNextGroupId();

        for (TourRecommendationResponseDto it : items) {
            if(it == null || it.getTourId() == null) continue;
            it.setGroupId(groupId);
            tourMapper.insertTourRecommendation(it, member.getMemberId());
        }
    }

}