package tkitem.backend.domain.product.service;

import tkitem.backend.domain.product.dto.response.ProductListResponseDto;
import tkitem.backend.domain.product.vo.ProductVo;

import java.util.List;

public interface ProductService {

    ProductListResponseDto<ProductVo> getProductsByTheme(String themeKey, Long cursor, Integer size);

    ProductListResponseDto<ProductVo> getProductsByCategoryIds(List<Long> ids, Long cursor, Integer size);
}
