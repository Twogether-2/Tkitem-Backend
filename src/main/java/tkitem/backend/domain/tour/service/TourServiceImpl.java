package tkitem.backend.domain.tour.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class TourServiceImpl implements TourService {
    private final TourMapper tourMapper;

    @Override
    public TourPackageDetailDto getTourPackageDetail(Long tourPackageId) {
        TourPackageDetailDto detailDto = tourMapper.selectTourPackageDetail(tourPackageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));

        return detailDto;
    }
}
