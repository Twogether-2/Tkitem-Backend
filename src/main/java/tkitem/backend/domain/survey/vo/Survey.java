package tkitem.backend.domain.survey.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Survey {
	private Long surveyId;
	private String title;
	private int pickLimit;
	private String type;
	private List<SurveyQuestion> questions = new ArrayList<>();
}
