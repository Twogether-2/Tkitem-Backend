package tkitem.backend.domain.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.product.dto.response.SubCategoryResponseDto;
import tkitem.backend.domain.product.vo.ProductVo;

import java.util.List;

@Mapper
public interface ProductMapper {

    // 테마별: 일일 고정 랜덤(씨드=KST yyyyMMdd), 커서는 합성키(Long)
    List<ProductVo> selectProductsByTheme(
            @Param("names") List<String> subNames,
            @Param("seed") String seed,
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );

    // 마지막 productId를 합성커서(Long)로 변환
    Long computeThemeCursorKey(
            @Param("seed") String seed,
            @Param("productId") Long productId
    );

    // 카테고리 ID(들): 최신순, 커서는 product_id
    List<ProductVo> selectProductsByCategoryIds(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );

    // 메인 카테고리 존재 여부
    int existsCategoryMain(@Param("mainId") Long mainId);

    // 메인 카테고리 이름 조회
    String selectMainName(@Param("mainId") Long mainId);

    // 메인 ID → 서브 카테고리 목록
    List<SubCategoryResponseDto> selectSubCategoriesByMainId(
            @Param("mainId") Long mainId,
            @Param("isProduct") String isProduct
    );

    // 부모 서브 존재 여부
    int existsCategoryParent(@Param("parentId") Long parentId);

    // 서브 이름
    String selectSubNameById(@Param("subId") Long subId);

    // parent_sub_id 로 자식 서브카테고리 목록
    List<SubCategoryResponseDto> selectSubCategoriesByParentId(@Param("parentId") Long parentId);
}
