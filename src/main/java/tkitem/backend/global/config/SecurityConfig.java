package tkitem.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.global.security.jwt.JwtAuthenticationExceptionHandler;
import tkitem.backend.global.security.jwt.JwtAuthenticationProcessingFilter;
import tkitem.backend.global.security.jwt.JwtProvider;
import tkitem.backend.global.security.login.CustomJsonUsernameAuthenticationFilter;
import tkitem.backend.global.security.login.CustomSocialLoginAuthenticationFilter;
import tkitem.backend.global.security.login.GeneralAuthenticationProvider;
import tkitem.backend.global.security.login.GeneralLoginService;
import tkitem.backend.global.security.login.LoginFailureHandler;
import tkitem.backend.global.security.login.LoginSuccessHandler;
import tkitem.backend.global.security.login.SocialAuthenticationProvider;
import tkitem.backend.global.security.login.SocialLoginService;
import tkitem.backend.global.util.RedisUtil;

@EnableMethodSecurity(prePostEnabled = true)
@Slf4j
@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    public static final String PREFIX = "";
    public static final String LOGIN_URL = PREFIX + "/auth/login";
    public static final String SIGNUP_URL = PREFIX + "/member/sign-up";
    public static final String STATIC_RESOURCE = "/css/**";

    private final SocialLoginService socialLoginService;
    private final GeneralLoginService generalLoginService;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final MemberMapper memberMapper;
    private final RedisUtil redisUtil;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // CSRF 보호 비활성화
            .formLogin(form -> form.disable()) // 기본 폼 로그인 비활성화
            .httpBasic(basic -> basic.disable()) // HTTP Basic 인증 비활성화
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 정책을 STATELESS로 설정
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    LOGIN_URL,
                    LOGIN_URL + "/**",
                    SIGNUP_URL,
                    SIGNUP_URL + "/**",
                    PREFIX + "/auth/email",
                    PREFIX + "/auth/email/verify",
                    PREFIX + "/member/duplicate",
                    PREFIX + "/preference/ai/**",
                    STATIC_RESOURCE,
                    PREFIX + "/actuator/health",
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                .requestMatchers("/member/info").hasRole("GUEST")
                .requestMatchers("/member").hasAnyRole("GUEST", "USER") // /member 경로는 GUEST와 USER 모두 허용
                .anyRequest().hasRole("USER")) // 나머지 모든 경로 인증 필요
            .addFilterBefore(jwtAuthenticationExceptionHandler(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationProcessingFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(customSocialLoginAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(customJsonUsernameAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * PasswordEncoder 빈 등록
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 기본 AuthenticationManager (Spring Security가 사용할 기본 빈)
     * @Primary 어노테이션으로 기본 빈으로 지정
     */
    @Bean
    @Primary
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(socialAuthenticationProvider());
    }

    /**
     * 소셜 로그인용 AuthenticationManager 등록
     */
    @Bean("socialAuthenticationManager")
    public AuthenticationManager socialAuthenticationManager() {
        return new ProviderManager(socialAuthenticationProvider());
    }

    /**
     * 일반 로그인용 AuthenticationManager 등록
     */
    @Bean("generalAuthenticationManager")
    public AuthenticationManager generalAuthenticationManager() {
        return new ProviderManager(generalAuthenticationProvider());
    }

    @Bean
    public SocialAuthenticationProvider socialAuthenticationProvider() {
        return new SocialAuthenticationProvider(socialLoginService);
    }

    @Bean
    public GeneralAuthenticationProvider generalAuthenticationProvider() {
        return new GeneralAuthenticationProvider(generalLoginService, passwordEncoder());
    }

    /**
     * 로그인 실패 시 호출되는 LoginFailureHandler 빈 등록
     */
    @Bean
    public LoginFailureHandler loginFailureHandler() {
        return new LoginFailureHandler();
    }

    /**
     * 소셜 로그인용 필터 빈 등록
     */
    @Bean
    public CustomSocialLoginAuthenticationFilter customSocialLoginAuthenticationFilter() {
        CustomSocialLoginAuthenticationFilter customSocialLoginFilter
            = new CustomSocialLoginAuthenticationFilter(objectMapper, memberMapper);
        customSocialLoginFilter.setAuthenticationManager(socialAuthenticationManager());
        customSocialLoginFilter.setAuthenticationFailureHandler(loginFailureHandler());
        customSocialLoginFilter.setAuthenticationSuccessHandler(loginSuccessHandler());
        return customSocialLoginFilter;
    }

    /**
     * 일반 로그인용 필터 빈 등록
     */
    @Bean
    public CustomJsonUsernameAuthenticationFilter customJsonUsernameAuthenticationFilter() {
        CustomJsonUsernameAuthenticationFilter customJsonUsernameFilter
            = new CustomJsonUsernameAuthenticationFilter(objectMapper, memberMapper);
        customJsonUsernameFilter.setAuthenticationManager(generalAuthenticationManager());
        customJsonUsernameFilter.setAuthenticationFailureHandler(loginFailureHandler());
        customJsonUsernameFilter.setAuthenticationSuccessHandler(loginSuccessHandler());
        return customJsonUsernameFilter;
    }

    /**
     * 로그인 성공 시 호출되는 LoginSuccessHandler 빈 등록
     */
    @Bean
    public LoginSuccessHandler loginSuccessHandler() {
        return new LoginSuccessHandler(jwtProvider);
    }

    @Bean
    public JwtAuthenticationProcessingFilter jwtAuthenticationProcessingFilter() {
        return new JwtAuthenticationProcessingFilter(jwtProvider, memberMapper, redisUtil);
    }

    @Bean
    public JwtAuthenticationExceptionHandler jwtAuthenticationExceptionHandler() {
        return new JwtAuthenticationExceptionHandler();
    }
}