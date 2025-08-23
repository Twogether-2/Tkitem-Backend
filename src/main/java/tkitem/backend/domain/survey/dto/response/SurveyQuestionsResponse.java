package tkitem.backend.domain.survey.dto.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import tkitem.backend.domain.survey.vo.Survey;

@Getter
@Setter
public class SurveyQuestionsResponse {
	private List<Survey> surveyList;
}
