package tkitem.backend.domain.preference.dto.response;

import java.util.List;

public record OpenAiResponse(
	List<ScoreResponse> scoreResponseList
) {

}
