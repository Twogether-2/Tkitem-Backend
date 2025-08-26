package tkitem.backend.domain.survey.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.survey.vo.Survey;
import tkitem.backend.domain.survey.vo.SurveyQ;
import tkitem.backend.domain.survey.vo.SurveyQuestion;

@Mapper
public interface SurveyMapper {
	// 설문 조항을 랜덤하게 가져오기
	List<Survey> selectRandomSurveys();

	// 문항별 선택지 가져오기
	List<SurveyQuestion> selectSurveyQuestionBySurveyId(
		@Param("surveyId") Long surveyId,
		@Param("age") String age,
		@Param("gender") char gender
	);

	// mbti 계산을 위한 가중치 최댓값 계산 메서드(정규화를 위한 Q값)
	SurveyQ selectSurveyQValues(@Param("surveyIds") List<Long> surveyIds);
}
