package tkitem.backend.global.security.login;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.security.jwt.JwtProvider;

@Slf4j
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final JwtProvider jwtProvider;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws
		ServletException, IOException {
		// access token, refresh token 발급
		Member member = (Member) authentication.getPrincipal();
		TokenResponse tokenResponse = jwtProvider.provideAccessTokenAndRefreshToken(member.getMemberId());

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(
			Map.of(
				"accessToken", tokenResponse.accessToken(),
				"refreshToken", tokenResponse.refreshToken()
			)
		));

		log.info("로그인에 성공하였습니다. 회원 id={}", member.getMemberId());
	}
}
