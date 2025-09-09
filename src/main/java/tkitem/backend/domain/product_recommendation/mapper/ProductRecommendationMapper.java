package tkitem.backend.domain.product_recommendation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.product_recommendation.vo.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductRecommendationMapper {

    List<ChecklistItem> findChecklistItemsByIds(@Param("ids") List<Long> ids);

    ChecklistItem findChecklistItemById(@Param("id") Long id);

    Trip findTripById(@Param("id") Long id);

    Trip findUpcomingTrip(@Param("memberId") Long memberId);

    Product findBestProductForBudget(Map<String, Object> params);

    List<ProductWithScore> findProductCandidates(Map<String, Object> params);

    List<ProductWithSimilarity> findRelatedProducts(Map<String, Object> params);

    List<ProductForTrip> findUpcomingTripProducts(Map<String, Object> params);

    Preference findPreferenceByMemberId(@Param("memberId") Long memberId);

    List<FashionProduct> findFashionByPreference(Map<String, Object> params);
}