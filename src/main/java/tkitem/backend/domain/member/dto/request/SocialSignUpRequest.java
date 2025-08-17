package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialSignUpRequest {
	@Schema(description = "이메일 주소", example = "example@example.com")
	@NotBlank(message = "이메일은 필수입니다.")
	private String email;

	@Schema(description = "카카오 인증 후 받은 ID 토큰", example = "eyJhbGciOiJSUzI1...")
	@NotBlank(message = "idToken은 필수입니다.")
	private String idToken;

	@Schema(description = "사용자 이름", example = "홍길동")
	@NotBlank(message = "이름은 필수입니다.")
	private String nickname;
}
