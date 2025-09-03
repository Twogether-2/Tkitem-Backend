package tkitem.backend.domain.trip.dto;

import tkitem.backend.domain.tour.dto.TourPackageInfo;
import tkitem.backend.domain.trip.vo.Trip;

public record TripInfoResponse(
    Trip trip,
    TourPackageInfo tourPackageInfo
) {}
