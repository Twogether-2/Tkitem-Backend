package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
	@Schema(description = "이메일", example = "qlrqod3356@gmail.com")
	@NotBlank
	String email,

	@Schema(description = "소셜 인증 후 받은 ID 토큰", example = "(아직 미사용이라 예시값 그대로 두면 됩니다)")
	@NotBlank
	String idToken,

	@Schema(description = "재로그인 여부(6개월 이내 탈퇴 회원) 기본값 false", example = "false")
	boolean reSignUp
) {

}
