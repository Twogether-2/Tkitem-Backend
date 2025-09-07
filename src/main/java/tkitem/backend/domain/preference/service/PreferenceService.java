package tkitem.backend.domain.preference.service;

import java.util.List;
import java.util.Optional;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.preference.dto.request.CalculateWeightRequest;
import tkitem.backend.domain.preference.dto.response.OpenAiResponse;
import tkitem.backend.domain.preference.dto.response.PreferenceResponse;
import tkitem.backend.domain.preference.vo.FashionType;
import tkitem.backend.domain.preference.vo.Preference;

public interface PreferenceService {
	void insertPreference(Member member, CalculateWeightRequest request);
	PreferenceResponse getPreference(Member member);
	FashionType getFashionTypeByFashionTypeId(String fashionTypeId);
	List<FashionType> getAllFashionTypes();
}
