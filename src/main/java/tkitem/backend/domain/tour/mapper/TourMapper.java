package tkitem.backend.domain.tour.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;
import tkitem.backend.domain.tour.dto.LocationInfo;
import tkitem.backend.domain.tour.dto.TourCandidateRowDto;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;

import tkitem.backend.domain.tour.dto.TourPackageInfo;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourCity;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.util.*;

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
            @Param("kTop") Integer kTop,
            @Param("memberId") Long memberId);

    List<TourPackageDto> selectPackagesForTours(
            @Param("req") TourRecommendationRequestDto req,
            @Param("memberId") Long memberId,
            @Param("tourIds")  List<Long> tourIds
    );

    List<Map<String, Object>> selectTourMetaByIds(List<Long> tourIds);

    List<Map<String, Object>> selectTdsByTourIds(List<Long> tourIds);

    void insertTourRecommendation(@Param("item") TourRecommendationResponseDto item, @Param("memberId") Long memberId);

    Optional<TourPackageDetailDto> selectTourPackageDetail(@Param("tourPackageId") Long tourPackageId);

    List<TourDetailScheduleDto> selectTourDetailScheduleListByTourId(@Param("tourId") Long tourId);

    Long selectNextGroupId();

    List<TourCommonRecommendDto> selectTourMetaByMemberId(@Param("memberId") Long memberId);

    List<TourCommonRecommendDto> selectTourMetaByTripSaved(@Param("memberId") Long memberId, @Param("topN") Integer topN, @Param("countryGroup") String countryGroup);

    List<Long> selectAllowTourIdsByCountry(@Param("country") String country);

    List<Long> selectAllowTourIdsByCountryGroup(@Param("countryGroup") String countryGroup);

    List<TourCommonRecommendDto> selectTourMetaByTourIds(@Param("ids") List<Long> ids);

    List<Long> selectTourIdsByFilters(
            @Param("depStart") Date depStart,
            @Param("retEnd") Date retEnd,
            @Param("priceMin") Long priceMin,
            @Param("priceMax") Long priceMax,
            @Param("locations") List<LocationInfo> locations,
            @Param("memberId") Long memberId,
            @Param("groupId") Long groupId
    );

    List<TourCandidateRowDto> scoreByIdsInline(@Param("allowIdsJson") String allowIdsJson,
                                               @Param("nPerDay") Integer nPerDay,
                                               @Param("tagIdList") List<Long> tagIdList);
}