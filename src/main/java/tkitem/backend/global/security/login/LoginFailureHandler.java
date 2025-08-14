package tkitem.backend.global.security.login;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.ErrorResponse;

@Slf4j
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        ErrorCode errorCode = exception.getMessage().equals(ErrorCode.RECENT_RESIGNED_MEMBER.getCode()) ? ErrorCode.RECENT_RESIGNED_MEMBER :  ErrorCode.LOGIN_FAILED;
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);

        ResponseEntity<ErrorResponse> entity = ResponseEntity
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .body(errorResponse);

        response.setStatus(entity.getStatusCode().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(entity.getBody()));
        log.info(exception.getMessage());
    }

}
