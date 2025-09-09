package tkitem.backend.domain.tour.service;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;

import java.util.List;

public interface TourRecommendFacadeService {
    public List<TourRecommendationResponseDto> recommend(TourRecommendationRequestDto req, String queryText, int topN, Member member) throws Exception;

    List<TourCommonRecommendDto> searchByKeyword(String keyword);
}
