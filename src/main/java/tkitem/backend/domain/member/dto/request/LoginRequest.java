package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
	@Schema(description = "이메일 주소", example = "qlrqod3356@gmail.com")
	@NotBlank(message = "이메일은 필수입니다.")
	String email,

	@Schema(description = "비밀번호 (영문자와 숫자를 포함한 8~20자)", example = "test1234")
	@NotBlank(message = "비밀번호는 필수입니다.")
	String password
) {

}
