package tkitem.backend.domain.tour.service;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;

import java.util.List;

public interface TourService {
    TourPackageDetailDto getTourPackageDetail(Long tourPackageId);

    List<TourCommonRecommendDto> getRecentRecommendedTours(Member member);
}
