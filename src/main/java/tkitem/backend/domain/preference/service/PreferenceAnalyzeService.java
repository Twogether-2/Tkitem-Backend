package tkitem.backend.domain.preference.service;

import java.util.List;

import tkitem.backend.domain.preference.dto.response.OpenAiResponse;

public interface PreferenceAnalyzeService {
	OpenAiResponse getWeightByOnlyOpenAI(List<String> imageUrl);

}
