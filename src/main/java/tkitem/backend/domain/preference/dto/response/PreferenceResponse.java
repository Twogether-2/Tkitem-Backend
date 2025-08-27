package tkitem.backend.domain.preference.dto.response;

public record PreferenceResponse(
	String nickname,
	String fashionType,
	String fashionTypeName,
	String description,
	String imgUrl,
	int bPercent,
	int mPercent,
	int fPercent,
	int vPercent,
	String firstLook,
	String secondLook
) {

}
