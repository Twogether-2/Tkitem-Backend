package tkitem.backend.domain.preference.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.preference.dto.request.CalculateWeightRequest;
import tkitem.backend.domain.preference.dto.response.OpenAiResponse;
import tkitem.backend.domain.preference.dto.response.PreferenceResponse;
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
	private static final int PHOTO_Q_UNIT_PER_PHOTO = 2; // 사진 1장당 Q에 더해지는 절대치(축 공통), 설문 문항 1개(±2)와 동급

	@Override
	public void insertPreference(Member member, CalculateWeightRequest request) {
		log.info("[PreferenceService] insert preference");

		if(member == null) {
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}

		// 1. surveyIds 에 해당하는 문항의 전체 Q(모든 문항에서 B, M, F, V의 합에 대한 최대 절대값) 조회
		SurveyQ q = surveyMapper.selectSurveyQValues(request.surveyIds());

		// 기본 Q 값 복사
		int qB = q.getBQ();
		int qM = q.getMQ();
		int qF = q.getFQ();
		int qV = q.getVQ();

		// 사진 장수가 0보다 크면 Q에 가상의 문항을 추가(축 공통)
		if (request.photoInputCnt() > 0) {
			int photoQ = request.photoInputCnt() * PHOTO_Q_UNIT_PER_PHOTO;
			qB += photoQ;
			qM += photoQ;
			qF += photoQ;
			qV += photoQ;
			log.info("[PreferenceService] Q expanded by photos => +{} per axis (photoInputCnt={})", photoQ, request.photoInputCnt());
		}

		// 2. S 값 가져오기 + scoreResponseList(사진 가중치) 합산
		int sB = request.totalBWeight();
		int sM = request.totalMWeight();
		int sF = request.totalFWeight();
		int sV = request.totalVWeight();

		log.info("[PreferenceService] photoInputCnt = {} (photos are not directly scored in this request schema)", request.photoInputCnt());

		// 3. 정규화된 퍼센트 계산
		int percentB = normalize(sB, qB);
		int percentM = normalize(sM, qM);
		int percentF = normalize(sF, qF);
		int percentV = normalize(sV, qV);

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

	@Override
	public FashionType getFashionTypeByFashionTypeId(String fashionTypeId) {
		log.info("[PreferenceService] getFashionTypeByFashionTypeId = {}", fashionTypeId);
		Optional<FashionType> result = Optional.ofNullable(preferenceMapper.selectFashionTypeById(fashionTypeId));
		if(result.isEmpty()){
			throw new BusinessException(ErrorCode.FASHION_TYPE_NOT_FOUND);
		}

		return result.get();
	}

	@Override
	public List<FashionType> getAllFashionTypes() {
		log.info("[PreferenceService] getAllFashionTypes");

		return preferenceMapper.selectAllFashionTypeByMbti();
	}
}
