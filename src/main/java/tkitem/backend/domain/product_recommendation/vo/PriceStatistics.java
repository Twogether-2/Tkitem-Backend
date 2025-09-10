package tkitem.backend.domain.product_recommendation.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceStatistics {
    private BigDecimal minPrice;
    private BigDecimal avgPrice;
    private BigDecimal maxPrice;
    private BigDecimal medianPrice;
    private Integer productCount;
}