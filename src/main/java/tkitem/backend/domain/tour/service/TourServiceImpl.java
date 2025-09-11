package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.LocationInfo;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TourServiceImpl implements TourService {
    private final TourMapper tourMapper;

    @Override
    public TourPackageDetailDto getTourPackageDetail(Long tourPackageId, Member member) {
        return tourMapper.selectTourPackageDetail(tourPackageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
    }

    @Override
    public List<TourCommonRecommendDto> getRecentRecommendedTours(Member member) {
        List<TourCommonRecommendDto> tourMaps = tourMapper.selectTourMetaByMemberId(member.getMemberId());

        for(TourCommonRecommendDto dto : tourMaps) {
            dto.setRealTitle(createTitle(dto.getLocations()));
        }

        return tourMaps;
    }

    @Override
    public List<TourCommonRecommendDto> getTopRankedTours(Member member, Integer topN) {
        List<TourCommonRecommendDto> tourMaps = tourMapper.selectTourMetaByTripSaved(member.getMemberId(), topN, "");
        for(TourCommonRecommendDto dto : tourMaps) {
            dto.setRealTitle(createTitle(dto.getLocations()));
        }
        return tourMaps;
    }

    @Override
    public Map<String, List<TourCommonRecommendDto>> getTopRankedToursInAllRegion(Member member) {
        String[] regions = {"동남아", "일본", "대만,홍콩,중국", "유럽"};
        Map<String, List<TourCommonRecommendDto>> tourMaps = new HashMap<>();
        tourMaps.put("인기", tourMapper.selectTourMetaByTripSaved(member.getMemberId(), 10, ""));
        for(String region : regions) {
            tourMaps.put(region, tourMapper.selectTourMetaByTripSaved(member.getMemberId(), 10, region));
        }

        for(Map.Entry<String, List<TourCommonRecommendDto>> e : tourMaps.entrySet()) {
            List<TourCommonRecommendDto> updated = e.getValue().stream().map(dto -> {
                String newTitle = createTitle(dto.getLocations());
                dto.setRealTitle(newTitle);
                return dto;
            }).toList();
            tourMaps.put(e.getKey(), updated);
        }
        return tourMaps;
    }

    private String createTitle(List<LocationInfo> cities){

        // 대한민국 포함 거르기
        cities = cities.stream()
                .filter(c -> c.getCountry() == null || (!"대한민국".equals(c.getCountry()) && !"-".equals(c.getCountry())))
                .toList();

        if(cities.isEmpty()) return "즐거운 여행";

        // 도시명 합산 길이
        int cityNameLen = cities.stream().map(LocationInfo::getCity).distinct().mapToInt(String::length).sum();

        // 방문도시 이름 합친게 10자 이내면 그냥 만들기
        if(cityNameLen <= 10) {
            return cities.stream().map(LocationInfo::getCity).distinct().collect(Collectors.joining(", "))+" 여행";
        }

        // 10자 이상이고 방문나라가 동일하면 대표 나라면 넣기
        if(cities.stream().map(LocationInfo::getCountry).distinct().count() == 1){
            return cities.get(0).getCountry()+" "+cities.get(0).getCity()+" 여행";
        }

        // 나라가 1~3개면 나라명 합산
        long distinctCountryCount = cities.stream().map(LocationInfo::getCountry).distinct().count();
        if(distinctCountryCount <= 3){
            return cities.stream().map(LocationInfo::getCountry).distinct().collect(Collectors.joining(", "))+" 여행";
        }

        // 나라가 4개 이상이면 대륙명 + N개국 여행 으로 반환
        return cities.get(0).getCountryGroup()+" "+distinctCountryCount+"개국 여행";
    }
}
