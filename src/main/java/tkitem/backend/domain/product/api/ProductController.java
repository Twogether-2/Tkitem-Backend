package tkitem.backend.domain.product.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.product.dto.response.ProductListResponseDto;
import tkitem.backend.domain.product.dto.response.SubCategoryListResponseDto;
import tkitem.backend.domain.product.service.ProductService;
import tkitem.backend.domain.product.vo.ProductVo;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/products")
@RestController
public class ProductController {

    private final ProductService productService;

    @GetMapping("/themes/{themeKey}")
    @Operation(summary="테마별 상품 조회(일일랜덤 + 무한스크롤)",
            description = """
            - themeKey: flight, hotel, sightseeing, hiking, water, nature, activity, spa
            - cursor: 이전 페이지 마지막 productId (없으면 첫 페이지)
            - size: 페이지 크기 (기본 20, 최대 100)
            - 정렬 : Asia/Seoul 기준 'yyyyMMdd'로 고정된 안정 랜덤 
            """)
    public ProductListResponseDto<ProductVo> getThemeProducts(
            @PathVariable String themeKey,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        return productService.getProductsByTheme(themeKey, cursor, size);
    }


    @GetMapping("/category")
    @Operation(
            summary = "카테고리ID(들)로 상품 조회",
            description = """
        - categoryIds: 콤마 구분
        - cursor: 이전 페이지 마지막 productId (없으면 첫 페이지)
        - size: 페이지 크기 (기본 20, 최대 100)
        """
    )
    public ProductListResponseDto<ProductVo> getProductsByCategory(
            @RequestParam List<Long> categoryIds,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("categoryIds must not be empty");
        }
         categoryIds = categoryIds.stream().distinct().toList();

        return productService.getProductsByCategoryIds(categoryIds, cursor, size);
    }

    @GetMapping("/categories/sub")
    @Operation(
            summary = "소분류 카테고리 조회",
            description = "대분류 id에 따른 소분류 카테고리 조회"
    )
    public SubCategoryListResponseDto getSubCategoriesByMain(
            @RequestParam Long mainId,
            @RequestParam(required = false) String isProduct
    ) {
        return productService.getSubCategoriesByMain(mainId, isProduct);
    }
}