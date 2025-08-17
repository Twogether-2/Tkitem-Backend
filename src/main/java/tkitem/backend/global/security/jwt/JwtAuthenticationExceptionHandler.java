package tkitem.backend.global.security.jwt;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.global.error.ErrorResponse;
import tkitem.backend.global.error.exception.InvalidTokenException;

@Slf4j
public class JwtAuthenticationExceptionHandler extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException e) {
            log.error("InvalidTokenException: {}", e.getMessage());
            setErrorResponse(response, e);
        }
    }

    private void setErrorResponse(HttpServletResponse response, InvalidTokenException e){
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(e.getErrorCode().getStatus());
        ErrorResponse errorResponse = ErrorResponse.of(e.getErrorCode());
        try{
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }
}
