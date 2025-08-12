package tkitem.backend.domain.member.dto.request;

public record LoginRequest(
	String email,
	String password
) {

}
