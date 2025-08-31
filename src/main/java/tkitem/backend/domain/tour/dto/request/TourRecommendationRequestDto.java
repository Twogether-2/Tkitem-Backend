package tkitem.backend.domain.tour.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

public record TourRecommendationRequestDto (
    @Schema(description = "출발일 시작(포함)", example = "2026-04-01")
    Date departureDate,
    @Schema(description = "출발일 시작(포함)", example = "2026-04-30")
    Date returnDate,
    @Schema(description = "최소 가격", example = "500000")
    Long priceMin,
    @Schema(description = "최대 가격", example = "2000000")
    Long priceMax,
    @Schema(description = "국가명(한글)", example = "베트남")
    String country,
    @Schema(description = "도시명(한글)", example = "다낭")
    String city,
    @Schema(description = "태그 ID 목록(WITH/STYLE 등)", example = "[7,8,10]")
    List<Long> tagIdList
)
{
}
