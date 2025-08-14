package tkitem.backend.global.security.jwt;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.util.HashUtil;
import tkitem.backend.global.util.RedisUtil;

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class JwtProvider{

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access.expiration}")
    private Long accessTokenExpirationPeriod;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenExpirationPeriod;

    private static final String ACCESS_HEADER = "Authorization";
    private static final String REFRESH_HEADER = "Authorization-Refresh";


    private static final String ACCESS_TOKEN_SUBJECT = "AccessToken";
    private static final String REFRESH_TOKEN_SUBJECT = "RefreshToken";
    private static final String OAUTH_ID_CLAIM = "OAuthId";
    private static final String BEARER = "Bearer ";
    private static final String ID_CLAIM = "id";

    private final MemberMapper memberMapper;
    private final RedisUtil redisUtil;

    /**
     * access token 생성
     */
    public String createAccessToken(Long id) {
        Date now = new Date();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
            .setSubject(ACCESS_TOKEN_SUBJECT)
            .claim(ID_CLAIM, id)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + accessTokenExpirationPeriod))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
    }

    /**
     * refresh token 생성
     */
    public String createRefreshToken(Long id) {
        Date now = new Date();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(REFRESH_TOKEN_SUBJECT)
                .claim(ID_CLAIM, id)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenExpirationPeriod))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 헤더에서 access token 추출
     */
    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(ACCESS_HEADER))
            .filter(accessToken -> accessToken.startsWith(BEARER))
            .map(accessToken -> accessToken.replace(BEARER, ""));
    }

    /**
     * 헤더에서 refresh token 추출
     */
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(REFRESH_HEADER))
            .filter(refreshToken -> refreshToken.startsWith(BEARER))
            .map(refreshToken -> refreshToken.replace(BEARER, ""));
    }

    /**
     * access token에서 username 추출
     * access token이 유효하면 username 반환, 유효하지 않으면 Optional.empty 반환
     */
    public Optional<Long> extractMemberId(String accessToken) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(accessToken)
                .getBody();
            return Optional.ofNullable(claims.get(ID_CLAIM, Long.class));
        } catch (Exception e) {
            log.error("액세스 토큰이 유효하지 않습니다.");
            return Optional.empty();
        }
    }

    public void provideAccessToken(HttpServletResponse response, Member member) {
        String accessToken = createAccessToken(member.getMemberId());
        try {
            Map<String, String> tokenMap = Map.of("accessToken", accessToken);
            writeResponse(response, tokenMap);
        } catch (IOException e) {
            throw new RuntimeException("응답 JSON 쓰기 실패", e);
        }
    }

    public TokenResponse provideAccessTokenAndRefreshToken(Long memberId) {
        String accessToken = createAccessToken(memberId);
        String refreshToken = createRefreshToken(memberId);
        String hashedRefreshToken = HashUtil.hash(refreshToken);

        redisUtil.set(memberId.toString(), hashedRefreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }


    private void writeResponse(HttpServletResponse response, Map<String, String> tokenMap) throws IOException {
        String json = new ObjectMapper().writeValueAsString(tokenMap);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
        response.setStatus(HttpServletResponse.SC_OK);
    }


    /**
     * 토튼 유효성 검사
     */
    public boolean isTokenValid(String token){
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
