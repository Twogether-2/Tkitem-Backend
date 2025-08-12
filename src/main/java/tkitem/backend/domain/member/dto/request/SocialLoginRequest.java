package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
	@Schema(description = "카카오 인증 후 받은 ID 토큰", example = "(아직 미사용이라 예시값 그대로 두면 됩니다)")
	String idToken,

	@Schema(description = "소셜 로그인에서 받은 고유 식별자", example = "kakao_123456")
	String oauthId,

	@Schema(description = "재로그인 여부(6개월 이내 탈퇴 회원) 기본값 false", example = "false")
	boolean reSignUp,

	@Schema(description = "소셜 로그인 타입 (예: KAKAO, GOOGLE)", example = "KAKAO")
	@NotBlank(message = "소셜 타입은 필수입니다.")
	String type
) {

}
