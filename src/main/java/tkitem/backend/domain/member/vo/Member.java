package tkitem.backend.domain.member.vo;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Member implements UserDetails {
	private Long id;
	private String email;
	private String password;
	private String nickname;
	private String role;
	private String birthday;
	private char isDeleted;
	private Character gender;
	private String imgUrl;
	private String updatedAt;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {

		return List.of(new SimpleGrantedAuthority(role));
	}

	@Override
	public String getPassword() {

		return password;
	}

	@Override
	public String getUsername() {

		return email;
	}
}
