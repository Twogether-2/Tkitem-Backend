package tkitem.backend.domain.member.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

	private String type;

	private String oauthId;
}
