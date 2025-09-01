package tkitem.backend.domain.recommendation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.recommendation.dto.CandidateProductDto;
import tkitem.backend.domain.recommendation.dto.ChecklistItemDto;

import java.util.List;

@Mapper
public interface ProductRecommendationMapper {

    List<ChecklistItemDto> selectChecklistItemsByIds(@Param("tripId") Long tripId,
                                                     @Param("ids") List<Long> ids);

    List<CandidateProductDto> selectCandidatesForItem(@Param("productCategorySubId") Long subId,
                                                      @Param("ctxTagCodes") List<String> tagCodes,
                                                      @Param("limit") int limit);

    List<CandidateProductDto> selectPopularCandidatesFallback(@Param("productCategorySubId") Long subId,
                                                              @Param("limit") int limit);
}