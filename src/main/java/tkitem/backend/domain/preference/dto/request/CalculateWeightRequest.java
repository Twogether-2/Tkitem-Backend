package tkitem.backend.domain.preference.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record CalculateWeightRequest(
	@Schema(description = "밝음/어두움 점수 합계 (양수 = 밝은옷, 음수 = 어두운옷)", example = "3")
	int totalBWeight,

	@Schema(description = "무난함/튀는 정도 점수 합계 (양수 = 무난, 음수 = 튀는옷)", example = "7")
	int totalMWeight,

	@Schema(description = "핏 점수 합계 (양수 = 딱붙, 음수 = 오버핏)", example = "6")
	int totalFWeight,

	@Schema(description = "컬러 점수 합계 (양수 = 비비드, 음수 = 뉴트럴)", example = "-8")
	int totalVWeight,

	@Schema(description = "첫 번째 선택한 룩 이름", example = "MODERN")
	String firstLook,

	@Schema(description = "두 번째 선택한 룩 이름", example = "CASUAL")
	String secondLook,

	@Schema(description = "사용자가 응답한 설문 ID 목록", example = "[1, 2, 3]")
	List<Long> surveyIds
) {}
