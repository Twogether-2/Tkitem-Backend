package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.TourCandidateRowDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.global.util.NumberUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TourRecommendService {

    private final TourMapper tourMapper;

    private static final Integer kTop = 10;
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * DB 점수만으로 Top-N 추천
     * @param req 출발일/가격/지역/태그/kTop 파라미터
     * @param topN 상위 N개
     * @return
     */
    @Transactional(readOnly = true)
    public List<TourRecommendationResponseDto> recommendDbOnly(TourRecommendationRequestDto req, int topN) {

        List<TourCandidateRowDto> tourCandidateRowDtos = tourMapper.selectTourCandidates(req, kTop);

        if(tourCandidateRowDtos.isEmpty()) return Collections.emptyList();

        // min-max 정규화(S_DB 만)
        double dbMin = tourCandidateRowDtos.stream().mapToDouble(r -> NumberUtil.toDoubleOrZero(r.getSDbRaw())).min().orElse(0);
        double dbMax = tourCandidateRowDtos.stream().mapToDouble(r -> NumberUtil.toDoubleOrZero(r.getSDbRaw())).max().orElse(1);

        List<TourRecommendationResponseDto> ranked = tourCandidateRowDtos.stream().map(r -> {
            double db = NumberUtil.toDoubleOrZero(r.getSDbRaw());
            double dbNorm = (dbMax > dbMin) ? (db - dbMin) / (dbMax - dbMin) : 0.0;

            return TourRecommendationResponseDto.builder()
                    .tourId(r.getTourId())
//                    .price(r.getMinPrice())
//                    .departureDate(r.getLatestDeparture())
                    .dbScore(dbNorm) // DB 정규화 점수
                    .esScore(0.0) // ES 하기 전이라 아직 0.0
                    .finalScore(dbNorm) // 최종점수에 아직 DB 점수만 활용함
                    .tourPackageId(r.getRepTourPackageId())
                    .price(r.getRepPrice())
                    .bookingUrl(r.getRepBookingUrl())
                    .departureDate(r.getRepDepartureDate())
                    .returnDate(r.getRepReturnDate())
                    .departureAirline(r.getRepDepartureAirline())
                    .returnAirline(r.getRepReturnAirline())
                    .build();
        }).sorted(Comparator
                .comparing(TourRecommendationResponseDto::getFinalScore).reversed()
                .thenComparing(TourRecommendationResponseDto::getPrice, Comparator.nullsLast(Long::compareTo))
                .thenComparing(TourRecommendationResponseDto::getDepartureDate, Comparator.nullsLast(Date::compareTo))).collect(Collectors.toCollection(ArrayList::new));

        return ranked.subList(0, Math.min(topN, ranked.size()));
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
        Map<Long, List<TourRecommendationResponseDto.TdsItem>> schedulesByTour = new HashMap<>();
        for (Map<String, Object> r : tdsRows) {
            Long tourId = ((Number) r.get("tourId")).longValue();
            TourRecommendationResponseDto.TdsItem item = TourRecommendationResponseDto.TdsItem.builder()
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
        for (Map.Entry<Long, List<TourRecommendationResponseDto.TdsItem>> e : schedulesByTour.entrySet()) {
            TourRecommendationResponseDto dto = byId.get(e.getKey());
            if (dto != null) dto.setSchedules(e.getValue());
        }


        return items;
    }

    @Transactional
    public void saveShownRecommendations(List<TourRecommendationResponseDto> items, Member member){
        if (items == null || items.isEmpty()) return;

        for (TourRecommendationResponseDto it : items) {
            tourMapper.insertTourRecommendation(it, member.getMemberId());
        }
    }

}