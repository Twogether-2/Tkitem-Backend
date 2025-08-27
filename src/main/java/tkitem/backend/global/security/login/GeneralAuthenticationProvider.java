package tkitem.backend.global.security.login;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GeneralAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();

    /**
     * ID와 비밀번호를 검증하여 인증을 처리한다.
     * PasswordEncoder를 사용하여 안전한 비밀번호 검증을 수행한다.
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getPrincipal().toString();
        String rawPassword = authentication.getCredentials().toString();
        
        log.info("GeneralAuthenticationProvider authenticate 진입");
        log.info("email: {}", email);
        
        // UserDetailsService를 통해 사용자 정보 로드
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        
        // PasswordEncoder를 사용하여 비밀번호 검증
        if (!passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                userDetails.getPassword(),
                authoritiesMapper.mapAuthorities(userDetails.getAuthorities())
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
} 