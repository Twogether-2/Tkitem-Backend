package tkitem.backend.domain.survey.service;

import java.util.List;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.survey.vo.Survey;

public interface SurveyService {
	// 설문 조사 문항들을 랜덤 반환
	List<Survey> getSurveyQuestions(Member member);
}
