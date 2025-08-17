package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpRequest{
	@Schema(description = "이메일 주소", example = "example@example.com")
	@NotBlank(message = "이메일은 필수입니다.")
	@Email(message = "올바른 이메일 형식이어야 합니다.")
	private String email;

	@Schema(description = "비밀번호 (영문자와 숫자를 포함한 8~20자)", example = "test1234")
	@NotBlank(message = "아이디 회원가입은 비밀번호가 필수입니다.")
	@Pattern(
		regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$",
		message = "비밀번호는 영문자와 숫자를 포함해 8자 이상 20자 이하이어야 합니다."
	)
	private String password;

	@Schema(description = "사용자 이름", example = "홍길동")
	@NotBlank(message = "이름은 필수입니다.")
	private String nickname;
}
