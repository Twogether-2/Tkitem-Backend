package tkitem.backend.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InfoInputRequest{
	@Schema(description = "성별 (F: 여성, M: 남성)", example = "M")
	@NotBlank(message = "성별은 필수입니다.")
	@Pattern(regexp = "^(F|M)$", message = "성별은 'F' 또는 'M'입니다.")
	private Character gender;

	@Schema(description = "생년월일 (형식: YYYY-MM-DD)", example = "1990-01-01")
	@NotBlank(message = "생년월일은 필수입니다.")
	@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일 형식은 YYYY-MM-DD이어야 합니다.")
	private String birthday;
}
