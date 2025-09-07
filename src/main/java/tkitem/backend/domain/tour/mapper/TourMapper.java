package tkitem.backend.domain.tour.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.tour.dto.TourCandidateRowDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;

import tkitem.backend.domain.tour.dto.TourPackageInfo;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourCity;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface TourMapper {
    /**
     * Tour 객체를 받아 DB 삽입, 생성된 tour_id 를 Tour 객체에 다시 담아줌
     * @param tour
     */
    void insertTour(Tour tour);

    /**
     * tripCode 로 Tour 객체 조회(중복방지)
     * @param tripCode
     * @return
     */
    Tour findTourByTripCode(String tripCode);

    /**
     * TourPackage 객체를 받아 단일(single)로 삽입
     * @param tourPackage
     */
    void insertTourPackage(TourPackage tourPackage);

    /**
     * TourPackageInfo 조회
     * @param tourPackageId
     * @return
     */
    Optional<TourPackageInfo> findTourPackageInfoByTourPackageId(@Param("tourPackageId") Long tourPackageId);

    /**
     * TourDetailSchedule 객체를 받아 단일(single)로 삽입
     * @param schedule
     */
    void insertTourDetailSchedule(TourDetailSchedule schedule);

    /**
     * TourCity 객체를 받아 단일(single)로 삽입
     * @param tourCity
     */
    void insertTourCity(TourCity tourCity);

    Set<String> findPackageDateCodesByTourId(Long tourId);

    List<TourCandidateRowDto> selectTourCandidates(
            @Param("req") TourRecommendationRequestDto tourRecommendationRequestDto,
            @Param("kTop") Integer kTop);

    List<Map<String, Object>> selectTourMetaByIds(List<Long> tourIds);

    List<Map<String, Object>> selectTdsByTourIds(List<Long> tourIds);

    void insertTourRecommendation(@Param("item") TourRecommendationResponseDto item, @Param("memberId") Long memberId);

    Optional<TourPackageDetailDto> selectTourPackageDetail(@Param("tourPackageId") Long tourPackageId);
}