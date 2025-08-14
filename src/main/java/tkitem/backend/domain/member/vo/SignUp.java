package tkitem.backend.domain.member.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUp {
	private Long memberId;

	private String email;

	private String password;

	private String nickname;

	private Character gender;

	private String birthday;
}
