package tkitem.backend.domain.trip.dto;

import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;
import tkitem.backend.domain.tour.dto.TourPackageInfo;
import tkitem.backend.domain.trip.vo.Trip;

import java.util.List;

public record TripInfoResponse(
    Trip trip,
    TourPackageInfo tourPackageInfo,
    List<TourDetailScheduleDto> tourDetailScheduleDto
) {}
