package tkitem.backend.domain.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
