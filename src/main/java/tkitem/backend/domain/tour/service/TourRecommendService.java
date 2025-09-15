package tkitem.backend.domain.tour.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourRecommendService {

    private final TourMapper tourMapper;

    private static final Integer kTop = 10;
    private static final Integer nPerDay = 3; // 일자별 상위점수 채택 일정 개수

    /**
     * DB 점수만으로 Top-N 추천
     * @param req 출발일/가격/지역/태그/kTop 파라미터
     * @param topN 상위 N개
     * @return
     */
    @Transactional
    public List<TourRecommendationResponseDto> recommendDbOnly(TourRecommendationRequestDto req, int topN, Member member, List<Long> allowIds) throws JsonProcessingException {

        long t0 = System.nanoTime();
        // 정렬까지 DB에서 완료, 전체 반환 (TopN 아님)
        String allowIdsJson = new ObjectMapper().writeValueAsString(allowIds);

        List<TourCandidateRowDto> tourCandidateRowDtos = tourMapper.scoreByIdsInline(allowIdsJson, nPerDay, req.getTagIdList());
        t0 = System.nanoTime() - t0;
        log.info("[RECOMMEND] scoreByAllowIds DB time = {} ms, rows={}", t0/1_000_000, tourCandidateRowDtos.size());
        //-------------

        for(int i = 0; i<Math.min(tourCandidateRowDtos.size(), 5); i++) {
            log.info("[RECOMMEND] topN 개 추천 완료. DB : {}, id : {}", tourCandidateRowDtos.get(i).getSDbRaw(), tourCandidateRowDtos.get(i).getTourId());
        }

        if(tourCandidateRowDtos.isEmpty()) return Collections.emptyList();

        List<TourRecommendationResponseDto> ranked = tourCandidateRowDtos.stream().map(r -> TourRecommendationResponseDto.builder()
                .tourId(r.getTourId())
                .dbScore(r.getSDbRaw()) // DB 정규화 점수
                .esScore(0.0) // ES 하기 전이라 아직 0.0
                .finalScore(r.getSDbRaw()) // 최종점수에 아직 DB 점수만 활용함
                .build())
                .collect(Collectors.toCollection(ArrayList::new));

        return ranked;
    }

    @Transactional(readOnly = true)
    public List<TourRecommendationResponseDto> fillTopNPackage(
            List<TourRecommendationResponseDto> ranked,
            TourRecommendationRequestDto req,
            Member member,
            int topN
    ){
        if (ranked == null || ranked.isEmpty()) return ranked;
        if (topN <= 0) topN = 5;

        // 1) 순서를 보존하며 상위 N개 tourId만 추출
        Map<Long, TourRecommendationResponseDto> topMap = new LinkedHashMap<>();
        for (TourRecommendationResponseDto dto : ranked) {
            if (dto == null || dto.getTourId() == null) continue;
            if (!topMap.containsKey(dto.getTourId())) {
                topMap.put(dto.getTourId(), dto);
                if (topMap.size() >= topN) break;
            }
        }
        if (topMap.isEmpty()) return ranked;
        List<Long> topIds = new ArrayList<>(topMap.keySet());

        // 2) 패키지 조건 필터(날짜/가격)로 해당 투어들의 패키지 조회 (기존 Mapper 재사용)
        List<TourPackageDto> pkgRows = tourMapper.selectPackagesForTours(req, member.getMemberId(), topIds); // :contentReference[oaicite:3]{index=3}
        Map<Long, List<TourPackageDto>> pkgByTour = (pkgRows == null)
                ? Collections.emptyMap()
                : pkgRows.stream().collect(Collectors.groupingBy(TourPackageDto::getTourId));
        for (Long tourId : topIds) {
            TourRecommendationResponseDto dto = topMap.get(tourId);
            dto.setPackageDtos(pkgByTour.getOrDefault(tourId, Collections.emptyList()));
        }

        // 3) 투어 메타(제목/이미지 등) 보강 (기존 Mapper 재사용)
        List<Map<String, Object>> metaRows = tourMapper.selectTourMetaByIds(topIds); // :contentReference[oaicite:4]{index=4}
        if (metaRows != null) {
            for (Map<String, Object> r : metaRows) {
                Long tourId = ((Number) r.get("tourId")).longValue();
                TourRecommendationResponseDto dto = topMap.get(tourId);
                if (dto == null) continue;
                if (dto.getTitle() == null) dto.setTitle((String) r.get("title"));
                if (dto.getFeature() == null) dto.setFeature((String) r.get("feature"));
                if (dto.getImgUrl() == null) dto.setImgUrl((String) r.get("imgUrl"));
                if (dto.getProvider() == null) dto.setProvider((String) r.get("provider"));
            }
        }

        // 4) 세부 일정(TDS) 보강 (기존 Mapper 재사용)
        List<Map<String, Object>> tdsRows = tourMapper.selectTdsByTourIds(topIds); // :contentReference[oaicite:5]{index=5}
        Map<Long, List<TourDetailScheduleDto>> tdsByTour = new HashMap<>();
        if (tdsRows != null) {
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
                        .scheduleDay(((BigDecimal)r.get("scheduleDay")).intValue())
                        .build();
                tdsByTour.computeIfAbsent(tourId, k -> new ArrayList<>()).add(item);
            }
        }
        for (Long tourId : topIds) {
            TourRecommendationResponseDto dto = topMap.get(tourId);
            dto.setSchedules(tdsByTour.getOrDefault(tourId, Collections.emptyList()));
        }

        // 같은 객체 참조이므로 ranked 자체가 갱신됩니다.
        return ranked;
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