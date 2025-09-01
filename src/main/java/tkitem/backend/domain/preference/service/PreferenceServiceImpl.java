package tkitem.backend.domain.preference.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.preference.dto.request.CalculateWeightRequest;
import tkitem.backend.domain.preference.dto.response.OpenAiResponse;
import tkitem.backend.domain.preference.dto.response.PreferenceResponse;
import tkitem.backend.domain.preference.dto.response.ScoreResponse;
import tkitem.backend.domain.preference.mapper.PreferenceMapper;
import tkitem.backend.domain.preference.vo.FashionType;
import tkitem.backend.domain.preference.vo.Preference;
import tkitem.backend.domain.survey.mapper.SurveyMapper;
import tkitem.backend.domain.survey.vo.SurveyQ;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.exception.EntityNotFoundException;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {
	private final PreferenceMapper preferenceMapper;
	private final SurveyMapper surveyMapper;

	@Override
	public void insertPreference(Member member, CalculateWeightRequest request) {
		log.info("[PreferenceService] insert preference");

		if(member == null) {
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}

		// 1. surveyIds 에 해당하는 문항의 전체 Q(모든 문항에서 B, M, F, V의 합에 대한 최대 절대값) 조회
		SurveyQ q = surveyMapper.selectSurveyQValues(request.surveyIds());

		// 2. S 값 가져오기 (request에 합산되어 들어온 값 그대로 사용)
		int sB = request.totalBWeight();
		int sM = request.totalMWeight();
		int sF = request.totalFWeight();
		int sV = request.totalVWeight();

		// 3. 정규화된 퍼센트 계산
		int percentB = normalize(sB, q.getBQ());
		int percentM = normalize(sM, q.getMQ());
		int percentF = normalize(sF, q.getFQ());
		int percentV = normalize(sV, q.getVQ());

		Preference preference = new Preference();
		preference.setBrightness(percentB);
		preference.setBoldness(percentM);
		preference.setFit(percentF);
		preference.setColor(percentV);
		preference.setFirstLook(request.firstLook());
		preference.setSecondLook(request.secondLook());
		preference.setMemberId(member.getMemberId());

		preferenceMapper.insertPreference(preference);
	}

	@Override
	public void insertPreferenceByOpenAiResult(Member member, OpenAiResponse response) {
	    log.info("[PreferenceService] insert preference by OpenAI result");

	    if (member == null) {
	        throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
	    }
	    if (response == null || response.scoreResponseList() == null) {
	        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
	    }

	    var list = response.scoreResponseList();

	    // 1) S 값: 각 축의 합계
	    int sB = list.stream().mapToInt(ScoreResponse::b).sum();
	    int sM = list.stream().mapToInt(ScoreResponse::m).sum();
	    int sF = list.stream().mapToInt(ScoreResponse::f).sum();
	    int sV = list.stream().mapToInt(ScoreResponse::v).sum();

	    // 2) Q 값: 각 축의 절대값 합(정규화용 최대 절대합)
	    int qB = list.stream().mapToInt(r -> Math.abs(r.b())).sum();
	    int qM = list.stream().mapToInt(r -> Math.abs(r.m())).sum();
	    int qF = list.stream().mapToInt(r -> Math.abs(r.f())).sum();
	    int qV = list.stream().mapToInt(r -> Math.abs(r.v())).sum();

	    // 3) 정규화된 퍼센트 계산
	    int percentB = normalize(sB, qB);
	    int percentM = normalize(sM, qM);
	    int percentF = normalize(sF, qF);
	    int percentV = normalize(sV, qV);

	    // 4) Look 상위 2개 산출: 동일 look 의 lookScore 를 합산 후 상위 2개
	    java.util.Map<String, Integer> lookScoreMap = new java.util.HashMap<>();
	    for (ScoreResponse r : list) {
	        if (r.look() == null) continue;
	        lookScoreMap.merge(r.look(), r.lookScore(), Integer::sum);
	    }
	    String firstLook = null;
	    String secondLook = null;
	    if (!lookScoreMap.isEmpty()) {
	        java.util.List<java.util.Map.Entry<String, Integer>> sorted =
	            new java.util.ArrayList<>(lookScoreMap.entrySet());
	        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
	        firstLook = sorted.get(0).getKey();
	        if (sorted.size() > 1) {
	            secondLook = sorted.get(1).getKey();
	        }
	    }

	    // 5) 저장
	    Preference preference = new Preference();
	    preference.setBrightness(percentB);
	    preference.setBoldness(percentM);
	    preference.setFit(percentF);
	    preference.setColor(percentV);
	    preference.setFirstLook(firstLook);
	    preference.setSecondLook(secondLook);
	    preference.setMemberId(member.getMemberId());

	    preferenceMapper.insertPreference(preference);
	}

	private int normalize(int s, int q) {
		if (q == 0) return 50; // 해당 축 문항이 없는 경우는 50%로 고정
		return (int) Math.round(((double) (s + q) / (2 * q)) * 100);
	}

	@Override
	public PreferenceResponse getPreference(Member member) {
		if(member == null) {
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}
		log.info("[PreferenceService] getPreference memberId = {}", member.getMemberId());

		Preference preference = preferenceMapper.selectPreferenceByMemberId(member.getMemberId());
		if(preference == null || preference.getPreferenceId() == null) {
			throw new EntityNotFoundException("Preference not found");
		}

		// 패션 취향 유형 구하기
		String fashionType = "";
		fashionType += preference.getBrightness() >= 50 ? "B" : "D";
		fashionType += preference.getBoldness() >= 50 ? "M" : "T";
		fashionType += preference.getFit() >= 50 ? "F" : "O";
		fashionType += preference.getColor() >= 50 ? "V" : "N";

		FashionType fashionTypeInfo = preferenceMapper.selectFashionTypeById(fashionType);

		return new PreferenceResponse(
			member.getNickname(),
			fashionTypeInfo.getFashionTypeId(),
			fashionTypeInfo.getFashionTypeName(),
			fashionTypeInfo.getDescription(),
			fashionTypeInfo.getImgUrl(),
			preference.getBrightness(),
			preference.getBoldness(),
			preference.getFit(),
			preference.getColor(),
			preference.getFirstLook(),
			preference.getSecondLook()
		);

	}
}
