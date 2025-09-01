package tkitem.backend.domain.preference.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"b", "m", "f", "v", "look", "lookScore"})
public record ScoreResponse(
	int b,
	int m,
	int f,
	int v,
	String look,
	int lookScore
) {

}
