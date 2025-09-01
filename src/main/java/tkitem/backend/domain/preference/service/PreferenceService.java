package tkitem.backend.domain.preference.service;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.preference.dto.request.CalculateWeightRequest;
import tkitem.backend.domain.preference.dto.response.OpenAiResponse;
import tkitem.backend.domain.preference.dto.response.PreferenceResponse;
import tkitem.backend.domain.preference.vo.Preference;

public interface PreferenceService {
	void insertPreference(Member member, CalculateWeightRequest request);
	void insertPreferenceByOpenAiResult(Member member, OpenAiResponse response);
	PreferenceResponse getPreference(Member member);
}
