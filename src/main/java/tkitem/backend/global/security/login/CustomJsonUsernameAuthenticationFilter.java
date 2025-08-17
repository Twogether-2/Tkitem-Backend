package tkitem.backend.global.security.login;

import static tkitem.backend.global.config.SecurityConfig.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.global.util.HashUtil;

@Slf4j
public class CustomJsonUsernameAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

	private static final String HTTP_METHOD = "POST";
	private static final String CONTENT_TYPE = "application/json";
	private static final String EMAIL_KEY = "email";
	private static final String PASSWORD_KEY = "password";
	private static final String RE_SIGNUP = "reSignUp";
	private static final RequestMatcher DEFAULT_LOGIN_PATH_REQUEST_MATCHER = request ->
		HTTP_METHOD.equalsIgnoreCase(request.getMethod()) && request.getServletPath().equals(LOGIN_URL);

	private final ObjectMapper objectMapper;
	private final MemberMapper memberMapper;

	public CustomJsonUsernameAuthenticationFilter(ObjectMapper objectMapper, MemberMapper memberMapper) {
		super(DEFAULT_LOGIN_PATH_REQUEST_MATCHER);
		this.objectMapper = objectMapper;
		this.memberMapper = memberMapper;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException{
		log.info("CustomJsonUsernameAuthenticationFilter 진입");

		if(request.getContentType() == null || !request.getContentType().startsWith(CONTENT_TYPE)) {
			throw new AuthenticationServiceException("Authentication Content-Type not supported: " + request.getContentType());
		}

		String messageBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
		Map<String, Object> loginDataMap = objectMapper.readValue(messageBody, Map.class);
		String email = (String) loginDataMap.get(EMAIL_KEY);
		String password = (String) loginDataMap.get(PASSWORD_KEY);
		log.info("password : {}", password);

		Object reSignUpObj = loginDataMap.get(RE_SIGNUP);
		boolean isReSignUp = reSignUpObj != null && "true".equalsIgnoreCase(reSignUpObj.toString().trim());

		if (isReSignUp) {
			log.info("[CustomJsonUsernameAuthenticationFilter] Re-Sign up");
			memberMapper.updateIsDeletedFalse(email);
		}

		UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(email, password);

		return this.getAuthenticationManager().authenticate(authRequest);
	}
}
