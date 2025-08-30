package tkitem.backend.domain.preference.service;

import tkitem.backend.domain.preference.dto.response.ScoreResponse;

public interface PreferenceAnalyzeService {
	ScoreResponse getWeightByOnlyOpenAI(String imageUrl);

}
