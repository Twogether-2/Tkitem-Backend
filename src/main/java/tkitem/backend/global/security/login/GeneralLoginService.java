package tkitem.backend.global.security.login;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.error.ErrorCode;

/**
 * 일반 로그인용 UserDetailsService
 * 이메일로 사용자를 조회하고, 비밀번호 검증은 AuthenticationProvider에서 처리한다.
 */
@Slf4j
@Service("generalLoginService")
@RequiredArgsConstructor
public class GeneralLoginService implements UserDetailsService {

    private final MemberMapper memberMapper;

    /**
     * 이메일로 사용자를 조회한다.
     * 비밀번호 검증은 AuthenticationProvider에서 PasswordEncoder를 통해 처리한다.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("GeneralLoginService loadUserByUsername 진입");
        log.info("email: {}", email);
        
        // 이메일로 사용자 조회
        Optional<Member> member = memberMapper.selectMemberByEmailAndTypeAndOauthId(email, "NONE", null);

        if (member.isPresent()) {
            Member userDetails = member.get();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime updatedAt = LocalDateTime.parse(userDetails.getUpdatedAt(), formatter);

            if (userDetails.getIsDeleted() == 'T' && updatedAt.isAfter(LocalDateTime.now().minusMonths(6))) {
                throw new UsernameNotFoundException(ErrorCode.RECENT_RESIGNED_MEMBER.getCode());
            }

            if(userDetails.getIsDeleted() == 'T' && updatedAt.isBefore(LocalDateTime.now())) {
                throw new UsernameNotFoundException(ErrorCode.LOGIN_FAILED.getCode());
            }

            return member.get();
        } else {
            throw new UsernameNotFoundException(ErrorCode.LOGIN_FAILED.getCode());
        }
    }
} 