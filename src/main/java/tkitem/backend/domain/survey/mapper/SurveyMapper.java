package tkitem.backend.domain.survey.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.survey.vo.Survey;
import tkitem.backend.domain.survey.vo.SurveyQuestion;

@Mapper
public interface SurveyMapper {
	List<Survey> selectRandomSurveys();
	List<SurveyQuestion> selectSurveyQuestionBySurveyId(
		@Param("surveyId") Long surveyId,
		@Param("age") String age,
		@Param("gender") char gender
	);
}
