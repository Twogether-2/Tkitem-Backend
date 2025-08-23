package tkitem.backend.domain.survey.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.survey.mapper.SurveyMapper;
import tkitem.backend.domain.survey.vo.Age;
import tkitem.backend.domain.survey.vo.Survey;
import tkitem.backend.domain.survey.vo.SurveyQuestion;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SurveyServiceImpl implements SurveyService{
	private final SurveyMapper surveyMapper;

	@Override
	public List<Survey> getSurveyQuestions(Member member) {
		log.info("[SurveyService] getSurveyQuestions");
		List<Survey> surveyList = surveyMapper.selectRandomSurveys();

		// 회원 연령대 매칭
		// 생일 문자열로 나이 계산(만 나이 기준)
		if(member == null || member.getBirthday() == null || member.getBirthday().isBlank()){
			throw new BusinessException(ErrorCode.INVALID_MEMBER_INFO);
		}

		if(member.getGender() == null){
			throw new BusinessException(ErrorCode.INVALID_MEMBER_INFO);
		}

		LocalDate birthDate = LocalDate.parse(member.getBirthday());
		int age = Period.between(birthDate, LocalDate.now()).getYears();
		String ageType = age < 45 ? Age.YOUNG.name() : Age.OLD.name();

		for(Survey survey : surveyList){
			List<SurveyQuestion> surveyQuestionList = surveyMapper.selectSurveyQuestionBySurveyId(
				survey.getSurveyId(),
				ageType,
				member.getGender()
			);
			survey.setQuestions(surveyQuestionList);
		}

		return surveyList;
	}
}
