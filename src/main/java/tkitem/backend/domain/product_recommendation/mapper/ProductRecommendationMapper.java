package tkitem.backend.domain.product_recommendation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.product_recommendation.dto.CandidateProductDto;
import tkitem.backend.domain.product_recommendation.dto.ChecklistItemDto;
import tkitem.backend.domain.product_recommendation.dto.response.ProductResponse;

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

    /** 1) 최근 본(앵커) 상품의 연관상품 */
    List<ProductResponse> selectRelatedToProduct(
            @Param("productId") Long productId,
            @Param("limit") int limit
    );

    /** 2) 사용자의 가장 가까운 Trip(다가오는 일정 우선) 도시에서 인기 아이템 */
    List<ProductResponse> selectPopularForNearestTrip(
            @Param("memberId") Long memberId,
            @Param("limit") int limit
    );

    /** 3) 사용자의 선호 취향(과거 구매 태그/브랜드) 기반 의류 추천 */
    List<ProductResponse> selectPersonalClothingForMember(
            @Param("memberId") Long memberId,
            @Param("limit") int limit
    );
}