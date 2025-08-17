package tkitem.backend.domain.member.dto.response;

public record MemberInfoResponse(
    String email,
    String nickname,
    String role,
    String birthday,
    char isDeleted,
    Character gender,
    String imgUrl,
	String type,
    String updatedAt
) {

}
