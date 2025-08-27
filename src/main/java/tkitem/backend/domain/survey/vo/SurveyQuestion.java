package tkitem.backend.domain.survey.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyQuestion {
	private Long surveyQuestionId;
	private String text;
	private int bWeight;
	private int mWeight;
	private int fWeight;
	private int vWeight;
	private String look;
	private int lookWeight;
	private String imageUrl;
}
