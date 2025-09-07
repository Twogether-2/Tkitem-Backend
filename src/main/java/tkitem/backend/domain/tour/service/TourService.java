package tkitem.backend.domain.tour.service;

import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;

public interface TourService {
    TourPackageDetailDto getTourPackageDetail(Long tourPackageId);
}
