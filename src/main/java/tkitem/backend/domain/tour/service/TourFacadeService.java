package tkitem.backend.domain.tour.service;

import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;

import java.util.List;

public interface TourFacadeService {
    public List<TourRecommendationResponseDto> recommend(TourRecommendationRequestDto req, String queryText, int topN) throws Exception;
}
