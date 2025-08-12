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
	@Schema(description = "회원 고유 ID (서버에서 내부적으로 사용)", example = "1")
	private Long id;

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

	@Schema(description = "성별 (F: 여성, M: 남성)", example = "M")
	@NotBlank(message = "성별은 필수입니다.")
	@Pattern(regexp = "^(F|M)$", message = "성별은 'F' 또는 'M'입니다.")
	private Character gender;

	@Schema(description = "생년월일 (형식: YYYY-MM-DD)", example = "1990-01-01")
	@NotBlank(message = "생년월일은 필수입니다.")
	@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일 형식은 YYYY-MM-DD이어야 합니다.")
	private String birthday;
}
