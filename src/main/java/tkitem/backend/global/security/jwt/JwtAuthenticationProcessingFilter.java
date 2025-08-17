package tkitem.backend.global.security.jwt;

import static tkitem.backend.global.config.SecurityConfig.*;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.InvalidTokenException;
import tkitem.backend.global.util.HashUtil;
import tkitem.backend.global.util.RedisUtil;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationProcessingFilter extends OncePerRequestFilter{

    /**
     * JWT 인증 필터 - 로그인 이외의 요청을 처리
     * 기본적으로 사용자는 요청 헤더에 AccessToken만 담아서 요청
     * AccessToken 만료 시에만 RefreshToken을 요청 헤더에 넣어 요청
     * 1. AccessToken이 유효한 경우 -> 인증 성공
     * 2. RefreshToken이 없고, AccessToken이 없거나 유효하지 않은 경우 -> 인증 실패 (401)
     * 3. 유효한 refresh token -> access, refresh 모두 재발급(RTR 방식)
     * 4. 유효하지 않은 refresh token -> 인증 실패 (401)
     */

    private static final String REISSUE_TOKEN_URL = PREFIX + "/auth/reissue";
    private final JwtProvider jwtProvider;
    private final MemberMapper memberMapper;
    private final GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();
    private final RedisUtil redisUtil;
    private static final String[] EXCLUDE_URLS = {
        LOGIN_URL,
        LOGIN_URL + "/**",
        SIGNUP_URL,
        SIGNUP_URL + "/**",
        STATIC_RESOURCE,
        PREFIX + "/actuator/health",
        "/actuator/health",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-resources/**",
        "/webjars/**"
    };
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws
        ServletException,
        IOException {
        for (String path : EXCLUDE_URLS) {
            if (pathMatcher.match(path, request.getServletPath())) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        log.info("[JWTAuthenticationFilter]");
        log.info(request.getRequestURI());
        if(request.getRequestURI().equals(REISSUE_TOKEN_URL)) {
            log.info("[Reissue token found]");
            reissueToken(request, response);
            return;
        }
        else{
            checkAccessTokenAndAuthentication(request, response, filterChain);
        }
    }

    private void reissueToken(HttpServletRequest request, HttpServletResponse response){
        log.info("refresh token 유효성 검증");
        String refreshToken = jwtProvider.extractRefreshToken(request)
            .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN));

        if(jwtProvider.isTokenValid(refreshToken)){
            Long memberId = jwtProvider.extractMemberId(refreshToken)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN));

            log.info("[reissueToken] memberId = {}", memberId);
            Member member = memberMapper.selectMemberByMemberId(memberId)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN));

            if(!isRefreshTokenValidInDatabase(memberId, refreshToken)){
                throw new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            jwtProvider.provideAccessToken(response, member);
        }
        else{
            throw new InvalidTokenException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }


    // access token 검사 및 인증 처리
    // request에서 extractAccessToken()으로 액세스 토큰 추출 후, isTokenValid()로 유효한 토큰인지 검증
    // 유효한 토큰이면, 액세스 토큰에서 extractUsername으로 username을 추출한 후 findByUsername()로 Member 객체 반환
    // 그 유저 객체를 saveAuthentication()으로 인증 처리하여
    // 인증 허가 처리된 객체를 SecurityContextHolder에 담은 후 다음 필터로 넘김
    private void checkAccessTokenAndAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain){
        log.info("checkAccessTokenAndAuthentication 진입");
        String accessToken = jwtProvider.extractAccessToken(request)
            .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_ACCESS_TOKEN));

        if(jwtProvider.isTokenValid(accessToken)){
            jwtProvider.extractMemberId(accessToken)
                .flatMap(memberMapper::selectMemberByMemberId)
                .ifPresentOrElse(
                    member -> {
                        log.info("인증 성공");
                        saveAuthentication(member); // 인증 허가 처리
                        try {
                            filterChain.doFilter(request, response);
                        } catch (IOException | ServletException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> {
                        throw new InvalidTokenException(ErrorCode.INVALID_ACCESS_TOKEN);
                    }
                );
        }
        else{
            throw new InvalidTokenException(ErrorCode.INVALID_ACCESS_TOKEN);
        }

    }

    /**
     * 인증 허가 처리
     * SecurityContextHolder.getContext()로 SecurityContext를 꺼낸 후,
     * setAuthentication()을 이용하여 위에서 만든 Authentication 객체에 대한 인증 허가 처리
     */
    private void saveAuthentication(Member member) {
        UserDetails userDetails = member;
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails,
            userDetails.getPassword(),
            authoritiesMapper.mapAuthorities(userDetails.getAuthorities())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


    // 요청의 refresh token이 db에 저장된 refresh token과 일치하는지 검사
    private boolean isRefreshTokenValidInDatabase(Long memberId, String refreshToken){
        log.info("isRefreshTokenValidInDatabase 진입");
        String hashedRefreshToken = HashUtil.hash(refreshToken);
        return redisUtil.get(memberId.toString()).equals(hashedRefreshToken);
    }
}