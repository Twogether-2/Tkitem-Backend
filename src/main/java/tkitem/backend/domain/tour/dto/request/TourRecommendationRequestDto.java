package tkitem.backend.domain.tour.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tkitem.backend.domain.tour.dto.LocationInfo;

import java.util.Date;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TourRecommendationRequestDto {

    @Schema(description = "출발일 시작(포함)", type = "string", format = "date", example = "2026-04-01")
    private Date departureDate;

    @Schema(description = "출발일 종료(포함)", type = "string", format = "date", example = "2026-04-30")
    private Date returnDate;

    @Schema(description = "최소 가격", example = "500000")
    private Long priceMin;

    @Schema(description = "최대 가격", example = "2000000")
    private Long priceMax;

    @Schema(description = "지역 정보 리스트", example = "[{\"countryGroup\":\"동남아\", \"country\":\"베트남\", \"city\":\"다낭\"}]")
    private List<LocationInfo> locations;

    @Schema(description = "태그 ID 목록(WITH/STYLE 등)", example = "[7,8,10]")
    private List<Long> tagIdList;

    @Schema(description = "재추천 시 같은 추천 그룹 내 항목으로 가져오기. 최초 추천일 시 null 또는 0", example = "0")
    private Long groupId;
}