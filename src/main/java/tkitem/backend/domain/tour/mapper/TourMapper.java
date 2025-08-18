package tkitem.backend.domain.tour.mapper;

import org.apache.ibatis.annotations.Mapper;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourCity;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.util.List;

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
     * TourPackage 객체 리스트를 받아 배치(batch)로 삽입
     * @param tourPackageList
     */
    void insertTourPackageList(List<TourPackage> tourPackageList);

    /**
     * TourDetailSchedule 객체 리스트를 받아 배치(batch)로 삽입
     * @param scheduleList
     */
    void insertTourDetailScheduleList(List<TourDetailSchedule> scheduleList);

    /**
     * TourCity 객체 리스트를 받아 배치(batch)로 삽입
     * @param tourCityList
     */
    void insertTourCityList(List<TourCity> tourCityList);
}