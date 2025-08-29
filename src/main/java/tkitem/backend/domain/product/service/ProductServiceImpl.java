package tkitem.backend.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.product.dto.response.ProductListResponseDto;
import tkitem.backend.domain.product.enums.ThemePreset;
import tkitem.backend.domain.product.mapper.ProductMapper;
import tkitem.backend.domain.product.vo.ProductVo;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    @Override
    @Transactional(readOnly = true)
    public ProductListResponseDto<ProductVo> getProductsByTheme(String themeKey, Long cursor, Integer size) {
        int limit = normalizeSize(size);

        ThemePreset preset = ThemePreset.from(themeKey);
        if (preset == null) throw new BusinessException(ErrorCode.INVALID_THEME_KEY);

        // Asia/Seoul 기준 yyyyMMdd → 일일 셔플 seed
        String seed = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        List<ProductVo> rows = productMapper.selectProductsByTheme(
                preset.getNames(), seed, cursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<ProductVo> page = hasMore ? rows.subList(0, limit) : rows;

        Long nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Long lastPid = page.get(page.size() - 1).getProductId();
            nextCursor = productMapper.computeThemeCursorKey(seed, lastPid);
        }
        return new ProductListResponseDto<>(page, hasMore ? nextCursor : null, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductListResponseDto<ProductVo> getProductsByCategoryIds(List<Long> categoryIds, Long cursor, Integer size) {
        int limit = normalizeSize(size);

        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_IDS_REQUIRED);
        }

        List<Long> ids = categoryIds;
        List<ProductVo> rows = productMapper.selectProductsByCategoryIds(ids, cursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<ProductVo> page = hasMore ? rows.subList(0, limit) : rows;

        Long nextCursor = hasMore ? page.get(page.size() - 1).getProductId() : null;
        return new ProductListResponseDto<>(page, hasMore ? nextCursor : null, hasMore);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }
}
