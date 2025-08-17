package tkitem.backend.global.security.login;

import static tkitem.backend.global.config.SecurityConfig.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.util.HashUtil;

@Slf4j
public class CustomSocialLoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

	private static final String HTTP_METHOD = "POST";
	private static final String CONTENT_TYPE = "application/json";
	private static final String EMAIL_KEY = "email";
	private static final String ID_TOKEN_KEY = "idToken";
	private static final String RE_SIGNUP = "reSignUp";
	private static final RequestMatcher DEFAULT_LOGIN_PATH_REQUEST_MATCHER = request -> {
	    if (!HTTP_METHOD.equalsIgnoreCase(request.getMethod())) {
	        return false;
	    }
	    String path = request.getServletPath();
	    return path.startsWith(LOGIN_URL + "/");
	};

	private final ObjectMapper objectMapper;
	private final MemberMapper memberMapper;

	public CustomSocialLoginAuthenticationFilter(ObjectMapper objectMapper, MemberMapper memberMapper) {
		super(DEFAULT_LOGIN_PATH_REQUEST_MATCHER);
		this.objectMapper = objectMapper;
		this.memberMapper = memberMapper;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException{
		log.info("CustomSocialLoginAuthenticationFilter 진입");

		if(request.getContentType() == null || !request.getContentType().startsWith(CONTENT_TYPE)) {
			throw new AuthenticationServiceException("Authentication Content-Type not supported: " + request.getContentType());
		}

		String uri = request.getRequestURI();
		// "/auth/login/" 이후 문자열만 추출
		String oauthType = null;

		if (uri.startsWith(LOGIN_URL)) {
			oauthType = uri.substring(LOGIN_URL.length() + 1);
		}

		String messageBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
		Map<String, Object> loginDataMap = objectMapper.readValue(messageBody, Map.class);
		String email = (String) loginDataMap.get(EMAIL_KEY);
		String idToken = (String) loginDataMap.get(ID_TOKEN_KEY);

		Object reSignUpObj = loginDataMap.get(RE_SIGNUP);
		boolean isReSignUp = reSignUpObj != null && "true".equalsIgnoreCase(reSignUpObj.toString().trim());

		if (isReSignUp) {
			log.info("[UserNamePasswordAuthenticationFilter] Re-Sign up");
			memberMapper.updateIsDeletedFalse(email);
		}

		String password = HashUtil.hash(UUID.randomUUID().toString());
		UsernamePasswordAuthenticationToken authRequest =
			new UsernamePasswordAuthenticationToken(
				email + ":" + oauthType.toUpperCase() + ":" + idToken,
				password
			);

		return this.getAuthenticationManager().authenticate(authRequest);
	}
}
