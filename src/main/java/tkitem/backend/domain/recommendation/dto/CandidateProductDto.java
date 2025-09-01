package tkitem.backend.domain.recommendation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProductDto {
    private Long productId;
    private String name;
    private BigDecimal price;
    private BigDecimal avgReview;
    private BigDecimal tagScore;   // 태그 점수 합
    private String matchedTags;    // "WEATHER:RAIN,..."
}