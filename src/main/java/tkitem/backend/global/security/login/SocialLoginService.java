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
import tkitem.backend.global.util.HashUtil;

/**
 *    파라미터로 username으로 DB에서 일치하는 Member를 찾고,
 *    해당 회원의 username과 Role을 담아 UserDetails의 User 객체를 생성한다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService implements UserDetailsService {

    private final MemberMapper memberMapper;
    private final OidcService oidcService;

    /**
     * 파라미터 socialId를 해싱 처리한 후 db에서 일치하는 사용자를 조회한다.
     * 비밀번호는 인증 절차에 필요하지 않으므로 난수로 만든 다음 UserDetail 객체로 넘긴다.
     */

    @Override
    public UserDetails loadUserByUsername(String principal) throws UsernameNotFoundException {
        log.info("loadUserByUsername 진입");
        String[] str = principal.split(":");
        String email = str[0];
        String type = str[1];
        String idToken = str[2];

        String oauthId = (String) oidcService.verify(idToken).get("sub");

        String hashedOauthId = HashUtil.hash(oauthId);
        Optional<Member> member = memberMapper.selectMemberByEmailAndTypeAndOauthId(
            email,
            type,
            hashedOauthId
        );

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
        }
        else{
            throw new UsernameNotFoundException(ErrorCode.LOGIN_FAILED.getCode());
        }
    }

}
